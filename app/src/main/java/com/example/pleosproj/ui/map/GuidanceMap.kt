package com.example.pleosproj.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        val leftInset = minOf(480.dp, maxWidth * .46f) + 36.dp
        LaunchedEffect(surface, leftInset, density) {
            with(density) { surface.setViewportInsets(leftInset.roundToPx(), 120.dp.roundToPx(), 250.dp.roundToPx(), 80.dp.roundToPx()) }
        }
        MapCanvas(surface, Modifier.fillMaxSize())
    }
}

@Composable
internal fun MapCanvas(surface: MapSurface, modifier: Modifier = Modifier) {
    Box(modifier) {
        AndroidView(factory = { surface.view }, modifier = Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp), color = Color.White.copy(alpha = 0.95f), contentColor = Color(0xFF23252B)) {
            Text(buildAnnotatedString {
                withLink(LinkAnnotation.Url("https://openfreemap.org/")) { append("© OpenFreeMap") }
                append(" · ")
                withLink(LinkAnnotation.Url("https://openmaptiles.org/")) { append("© OpenMapTiles") }
                append(" · ")
                withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright")) { append("© OpenStreetMap") }
            }, Modifier.padding(8.dp), fontSize = 13.sp)
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
