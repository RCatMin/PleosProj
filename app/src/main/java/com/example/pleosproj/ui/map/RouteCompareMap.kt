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
        LaunchedEffect(surface, density) {
            with(density) { surface.setViewportInsets(24.dp.roundToPx(), 110.dp.roundToPx(), 104.dp.roundToPx(), 32.dp.roundToPx()) }
        }
        MapCanvas(surface, androidx.compose.ui.Modifier.matchParentSize())
    }
}
