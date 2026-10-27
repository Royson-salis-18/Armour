package com.armor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.R
import com.armor.core.engine.RideSnapshot
import com.armor.core.engine.RiderView
import com.armor.core.model.LatLon
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private fun LatLon.g() = LatLng(lat, lon)

/**
 * The ride on a real Google map: ARMOR's dark style, a bright orange route, you as an orange dot and each other rider as
 * an avatar. It frames everyone and re-frames as the group moves. Needs a Maps API key and a connection; without them
 * [RiderMap] (the offline map) is used instead, so the safety features never depend on tiles loading.
 */
@Composable
fun GoogleRiderMap(snapshot: RideSnapshot, trail: List<LatLon>, modifier: Modifier = Modifier, bottomInset: Dp = 0.dp) {
    val context = LocalContext.current
    val style = remember { runCatching { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) }.getOrNull() }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(20.0, 78.0), 4f) }

    // Re-frame when anyone has moved noticeably (≈ 25 m), not on every GPS tick, so the map doesn't jitter.
    val framing = snapshot.riders.filter { !it.isStale }.map { (it.position.lat * 4000).toLong() to (it.position.lon * 4000).toLong() }
    LaunchedEffect(framing) {
        val pts = snapshot.riders.filter { !it.isStale }.map { it.position.g() }
        when {
            pts.isEmpty() -> Unit
            pts.size == 1 -> camera.animate(CameraUpdateFactory.newLatLngZoom(pts[0], 16f))
            else -> camera.animate(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().apply { pts.forEach(::include) }.build(), 140))
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        contentPadding = PaddingValues(bottom = bottomInset),
        properties = MapProperties(mapStyleOptions = style),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false, compassEnabled = false, mapToolbarEnabled = false,
            myLocationButtonEnabled = false, indoorLevelPickerEnabled = false, tiltGesturesEnabled = false,
        ),
    ) {
        if (trail.size > 1) {
            Polyline(points = trail.map { it.g() }, color = ArmorColors.Orange.copy(alpha = 0.35f), width = 22f)
            Polyline(points = trail.map { it.g() }, color = ArmorColors.Orange, width = 9f)
        }
        snapshot.hazards.forEach { h ->
            h.position?.let { pos ->
                MarkerComposable(h.id, state = rememberMarkerState(key = h.id, position = pos.g()), anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)) {
                    Box(Modifier.size(26.dp).background(ArmorColors.Amber, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Text("!", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        snapshot.regroup?.position?.let { pos ->
            MarkerComposable("regroup", state = rememberMarkerState(key = "regroup", position = pos.g()), anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)) {
                Box(Modifier.size(30.dp).background(ArmorColors.Orange, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text("R", color = Color(0xFF1A0B00), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        snapshot.riders.forEach { r ->
            if (r.isSelf) Circle(center = r.position.g(), radius = maxOf(r.accuracyM, 6.0), fillColor = ArmorColors.Orange.copy(alpha = 0.16f), strokeColor = Color.Transparent, strokeWidth = 0f)
            MarkerComposable(r.id, r.isSelf, r.isOutlier, r.isStale, state = rememberMarkerState(position = r.position.g()), anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)) {
                RiderPuck(r)
            }
        }
    }
}

/** A rider on the map: a white-ringed disc. You are an orange dot; a rider who has dropped off gets a red ring. */
@Composable
private fun RiderPuck(r: RiderView) {
    if (r.isSelf) {
        Box(Modifier.size(22.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Box(Modifier.size(16.dp).background(ArmorColors.Orange, CircleShape)) }
        return
    }
    val fill = if (r.isStale) ArmorColors.Dim else ArmorColors.riderColor(r.id)
    Box(
        Modifier.size(36.dp).background(Color.White, CircleShape).border(if (r.isOutlier && !r.isStale) 3.dp else 0.dp, ArmorColors.Red, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
            Text(r.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
