package com.example.pleosproj.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

// 한 화면을 가린 뒤 교체한다. 지도 화면 두 개가 같은 MapView를 동시에 붙이지 않는다.
@Composable
internal fun <T> NavigationTransition(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    var displayed by remember { mutableStateOf(target) }
    val veil = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (displayed != target) {
            veil.animateTo(1f, tween(120, easing = FastOutLinearInEasing))
            displayed = target
            withFrameNanos { }
        }
        veil.animateTo(0f, tween(260, easing = LinearOutSlowInEasing))
    }
    val blocking = displayed != target || veil.value > 0f || veil.isRunning
    Box(modifier) {
        content(displayed)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil.value }.background(Color(0xFFF7F7F8)))
        if (blocking) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}
