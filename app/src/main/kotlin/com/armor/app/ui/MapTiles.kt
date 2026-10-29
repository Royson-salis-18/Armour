package com.armor.app.ui

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.armor.app.ArmorApp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Street map tiles from OpenStreetMap, no API key. Tiles are cached on disk, so a route you have already seen still
 * draws with no internet; anything not cached simply stays blank and the rider markers draw over a plain background.
 */
object MapTiles {
    /** Read in the map's draw pass so it repaints whenever a tile arrives. */
    var version by mutableIntStateOf(0)
        private set

    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 96
    }
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()

    fun lon(x: Int, z: Int) = x / (1 shl z).toDouble() * 360.0 - 180.0
    fun lat(y: Int, z: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * y / (1 shl z)))))
    fun tileX(lon: Double, z: Int) = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt()
    fun tileY(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * (1 shl z)).toInt()
    }

    /** The tile if it is in memory, otherwise null (and it is fetched in the background). */
    fun get(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$z/$x/$y"
        synchronized(memory) { memory[key] }?.let { return it }
        val failed = failedAt[key]
        if (failed != null && SystemClock.elapsedRealtime() - failed < 30_000) return null
        if (loading.add(key)) {
            ArmorApp.instance.appScope.launch {
                try {
                    val bmp = load(key, z, x, y)
                    if (bmp != null) synchronized(memory) { memory[key] = bmp } else failedAt[key] = SystemClock.elapsedRealtime()
                } catch (_: Exception) {
                    failedAt[key] = SystemClock.elapsedRealtime()
                } finally {
                    loading.remove(key)
                    version++
                }
            }
        }
        return null
    }

    private fun load(key: String, z: Int, x: Int, y: Int): ImageBitmap? {
        val dir = File(ArmorApp.instance.cacheDir, "tiles")
        val file = File(dir, key.replace('/', '_') + ".png")
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it.asImageBitmap() }
        val c = URL("https://tile.openstreetmap.org/$key.png").openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "ARMOR-Android/1.0 (rider safety app)")
        try {
            if (c.responseCode != 200) return null
            val bytes = c.inputStream.use { it.readBytes() }
            dir.mkdirs()
            file.writeBytes(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } finally {
            c.disconnect()
        }
    }
}
