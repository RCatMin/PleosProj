package com.example.pleosproj.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.SegmentKind

@Composable
internal fun GuidanceMap(surface: MapSurface, state: GuidanceState, modifier: Modifier = Modifier,
    motionFrame: (() -> MapMotion)? = null) {
    LaunchedEffect(surface, state, motionFrame) { surface.render(state, motionFrame != null) }
    LaunchedEffect(surface, motionFrame) {
        val pacer = MapFramePacer()
        if (motionFrame != null) while (isActive) {
            withFrameNanos { time ->
                if (pacer.shouldRender(time)) surface.renderMotion(motionFrame())
            }
        }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val topInset = maxHeight * .20f
        val leftInset = minOf(408.dp, maxWidth * .38f) + 32.dp
        LaunchedEffect(surface, topInset, leftInset, density) {
            with(density) { surface.setViewportInsets(leftInset.roundToPx(), topInset.roundToPx(), 84.dp.roundToPx(), 48.dp.roundToPx()) }
        }
        MapCanvas(surface, Modifier.fillMaxSize())
        val ranges = state.progress.segments.filter { it.kind == SegmentKind.HAZARD }
        val kinds = remember(state.routeLine, state.hazards, ranges) {
            hazardRouteLines(state.routeLine.orEmpty(), state.hazards, ranges).map { it.kind }.distinct()
        }
        HazardLegend(kinds, Modifier.align(Alignment.BottomEnd).padding(end = 104.dp, bottom = 40.dp))
    }
}

@Composable
internal fun MapCanvas(surface: MapSurface, modifier: Modifier = Modifier) {
    Box(modifier) {
        AndroidView(factory = { surface.view }, modifier = Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 104.dp, bottom = 4.dp), color = Color.White.copy(alpha = 0.95f), contentColor = Color(0xFF23252B)) {
            Text(buildAnnotatedString {
                withLink(LinkAnnotation.Url("https://openfreemap.org/")) { append("© OpenFreeMap") }
                append(" · ")
                withLink(LinkAnnotation.Url("https://openmaptiles.org/")) { append("© OpenMapTiles") }
                append(" · ")
                withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright")) { append("© OpenStreetMap") }
            }, Modifier.padding(horizontal = 5.dp, vertical = 3.dp), fontSize = 9.sp)
        }
        surface.loadMessage?.let { message ->
            Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, fontSize = 20.sp)
                    if (message.contains("못했어요")) TextButton(onClick = surface::reload) { Text("다시 시도") }
                }
            }
        }
    }
}
