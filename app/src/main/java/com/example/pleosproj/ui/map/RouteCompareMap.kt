package com.example.pleosproj.ui.map

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.pleosproj.ui.state.RouteCompareState

@Composable
fun RouteCompareMap(surface: MapSurface, state: RouteCompareState, modifier: Modifier = Modifier) {
    LaunchedEffect(surface, state) { surface.render(state) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val left = minOf(480.dp, maxWidth * .46f) + 36.dp
        LaunchedEffect(surface, density, left) {
            with(density) { surface.setViewportInsets(left.roundToPx(), 60.dp.roundToPx(), 110.dp.roundToPx(), 48.dp.roundToPx()) }
        }
        MapCanvas(surface, androidx.compose.ui.Modifier.matchParentSize())
    }
}
