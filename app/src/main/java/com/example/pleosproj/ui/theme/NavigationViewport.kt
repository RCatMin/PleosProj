package com.example.pleosproj.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// 예시 앱의 지도 영역 비율(1204×852)을 기준으로 카드와 글씨를 함께 맞춘다.
// 차량 패널을 제외한 앱 영역만 사용하며 시스템 화면 배율은 바꾸지 않는다.
@Composable
internal fun NavigationViewport(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val scale = minOf(maxWidth.value / 1204f, maxHeight.value / 852f).coerceIn(.85f, 1.5f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            BoxWithConstraints(Modifier.fillMaxSize(), content = content)
        }
    }
}
