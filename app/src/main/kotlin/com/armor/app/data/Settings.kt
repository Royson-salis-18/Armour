package com.armor.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class Sensitivity(val label: String, val impactG: Double) {
    LOW("Low", 5.0),
    MEDIUM("Medium", 4.0),
    HIGH("High", 3.0),
}

data class Settings(
    val riderId: String,
    val riderName: String,
    val groupCode: String,
    val emergencyNumber: String,
    /** Realtime Database root, e.g. https://my-project-default-rtdb.firebaseio.com. Blank disables online sync. */
    val firebaseUrl: String,
    val keepScreenOn: Boolean,
    val soloMonitoring: Boolean,
    val sensitivity: Sensitivity,
    val usesIntercom: Boolean,
    /** ARMOR server root, e.g. https://armor.example.com. Blank disables the ride-session flow. */
    val backendUrl: String = "",
    /** The ride this rider has created or joined on the ARMOR server (blank when not in one). */
    val rideId: String = "",
    val rideCode: String = "",
    val rideName: String = "",
    val isRideAdmin: Boolean = false,
    val bloodGroup: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val medications: String = "",
    /** Roadside assistance / breakdown number (insurer, OEM, local mechanic). */
    val roadsideNumber: String = "",
    /** Which Google map view the ride screen shows: MAP, NIGHT, TERRAIN, SATELLITE or HYBRID. */
    val mapView: String = "MAP",
    /** Vibrate for taps, confirmations and group events. */
    val haptics: Boolean = true,
) {
    val medical: com.armor.core.model.MedicalInfo get() = com.armor.core.model.MedicalInfo(bloodGroup, allergies, conditions, medications)

    val backendConfigured: Boolean get() = backendUrl.isNotBlank() && rideId.isNotBlank()
    val syncConfigured: Boolean get() = backendConfigured || (groupCode.isNotBlank() && firebaseUrl.isNotBlank())
}

/** Small SharedPreferences wrapper that also exposes the current values as a [StateFlow] for the UI. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("armor", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    val current: Settings get() = _settings.value

    /** Whether a ride was in progress; lets the service resume itself if Android restarts it. */
    var rideActive: Boolean
        get() = sp.getBoolean("rideActive", false)
        set(value) { sp.edit().putBoolean("rideActive", value).apply() }

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        sp.edit()
            .putString("riderName", next.riderName)
            .putString("groupCode", next.groupCode)
            .putString("emergencyNumber", next.emergencyNumber)
            .putString("firebaseUrl", next.firebaseUrl)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .putBoolean("soloMonitoring", next.soloMonitoring)
            .putString("sensitivity", next.sensitivity.name)
            .putBoolean("usesIntercom", next.usesIntercom)
            .putString("riderId", next.riderId)
            .putString("backendUrl", next.backendUrl)
            .putString("rideId", next.rideId)
            .putString("rideCode", next.rideCode)
            .putString("rideName", next.rideName)
            .putBoolean("isRideAdmin", next.isRideAdmin)
            .putString("bloodGroup", next.bloodGroup)
            .putString("allergies", next.allergies)
            .putString("conditions", next.conditions)
            .putString("medications", next.medications)
            .putString("roadsideNumber", next.roadsideNumber)
            .putString("mapView", next.mapView)
            .putBoolean("haptics", next.haptics)
            .apply()
        _settings.value = next
    }

    private fun read(): Settings {
        var id = sp.getString("riderId", null)
        if (id == null) {
            id = UUID.randomUUID().toString().take(8)
            sp.edit().putString("riderId", id).apply()
        }
        return Settings(
            riderId = id,
            riderName = sp.getString("riderName", "") ?: "",
            groupCode = sp.getString("groupCode", "") ?: "",
            emergencyNumber = sp.getString("emergencyNumber", "112") ?: "112",
            firebaseUrl = sp.getString("firebaseUrl", "") ?: "",
            keepScreenOn = sp.getBoolean("keepScreenOn", true),
            soloMonitoring = sp.getBoolean("soloMonitoring", true),
            sensitivity = runCatching { Sensitivity.valueOf(sp.getString("sensitivity", null) ?: "MEDIUM") }.getOrDefault(Sensitivity.MEDIUM),
            usesIntercom = sp.getBoolean("usesIntercom", false),
            backendUrl = sp.getString("backendUrl", "") ?: "",
            rideId = sp.getString("rideId", "") ?: "",
            rideCode = sp.getString("rideCode", "") ?: "",
            rideName = sp.getString("rideName", "") ?: "",
            isRideAdmin = sp.getBoolean("isRideAdmin", false),
            bloodGroup = sp.getString("bloodGroup", "") ?: "",
            allergies = sp.getString("allergies", "") ?: "",
            conditions = sp.getString("conditions", "") ?: "",
            medications = sp.getString("medications", "") ?: "",
            roadsideNumber = sp.getString("roadsideNumber", "") ?: "",
            mapView = sp.getString("mapView", "MAP") ?: "MAP",
            haptics = sp.getBoolean("haptics", true),
        )
    }
}
