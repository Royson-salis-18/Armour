package com.armor.app.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer build of ARMOR that can be installed over this one. */
data class UpdateInfo(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String, val notes: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    /** Downloaded and checked; the install screen has been opened (or needs permission first). */
    data class Ready(val info: UpdateInfo, val needsPermission: Boolean) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * In-app updates. Every CI build publishes ARMOR.apk and a small version.json next to it; the app compares that
 * version code with its own, downloads the new APK, checks its SHA-256 against version.json, and hands it to Android's
 * installer. Android itself only accepts the install if the APK is signed with the same key as the installed app.
 */
object Updater {
    private const val BASE = "https://github.com/Royson-salis-18/Armour/releases/download/latest-apk"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    fun installedVersion(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    fun installedName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Looks for a newer build. [quiet] keeps a failed or up-to-date check out of the UI (automatic checks on launch). */
    fun check(context: Context, quiet: Boolean = false) {
        val current = state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        val app = context.applicationContext
        if (!quiet) state.value = UpdateState.Checking
        scope.launch {
            try {
                val json = JSONObject(httpText("$BASE/version.json"))
                val info = UpdateInfo(
                    json.getLong("versionCode"), json.optString("versionName", "?"), json.optString("apk", "$BASE/ARMOR.apk"),
                    json.optString("sha256").lowercase(), json.optString("notes"), json.optLong("size", 0),
                )
                state.value = if (info.versionCode > installedVersion(app)) UpdateState.Available(info)
                else if (quiet) UpdateState.Idle else UpdateState.UpToDate
            } catch (e: Exception) {
                if (!quiet) state.value = UpdateState.Failed("Couldn't check for updates. Check your internet connection.")
            }
        }
    }

    /** Downloads the update, verifies it and opens Android's installer. */
    fun install(context: Context, info: UpdateInfo) {
        if (state.value is UpdateState.Downloading) return
        val app = context.applicationContext
        state.value = UpdateState.Downloading(info, 0)
        scope.launch {
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val apk = File(dir, "ARMOR.apk")
                val digest = MessageDigest.getInstance("SHA-256")
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                    var done = 0L
                    var lastPercent = -1
                    conn.inputStream.use { input ->
                        apk.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                                if (pct != lastPercent) { lastPercent = pct; state.value = UpdateState.Downloading(info, pct) }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256.isNotEmpty() && actual != info.sha256) { apk.delete(); error("The download didn't match its checksum, so it was discarded.") }
                openInstaller(app, info, apk)
            } catch (e: Exception) {
                state.value = UpdateState.Failed("Update failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    private fun openInstaller(context: Context, info: UpdateInfo, apk: File) {
        // Android asks once, per app, to allow installs from ARMOR; send the user to that switch and come back.
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            state.value = UpdateState.Ready(info, needsPermission = true)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        state.value = UpdateState.Ready(info, needsPermission = false)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** After the rider allowed installs from ARMOR and came back: carry on with the already-downloaded file. */
    fun resumeInstall(context: Context) {
        val ready = state.value as? UpdateState.Ready ?: return
        if (!ready.needsPermission) return
        val apk = File(context.cacheDir, "updates/ARMOR.apk")
        if (!apk.exists() || (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls())) return
        openInstaller(context.applicationContext, ready.info, apk)
    }

    private fun httpText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
