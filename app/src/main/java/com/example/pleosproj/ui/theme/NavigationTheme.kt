package com.example.pleosproj.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import com.example.pleosproj.R

val RoutePurple = Color(0xFF7145D6)
val ManeuverBlue = Color(0xFF2A70FF)

@OptIn(ExperimentalTextApi::class)
private val Pretendard = FontFamily(listOf(400, 500, 600, 700).map { weight ->
    Font(R.font.pretendard_variable, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})

private val TypographyWithPretendard = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Pretendard), displayMedium = base.displayMedium.copy(fontFamily = Pretendard),
        displaySmall = base.displaySmall.copy(fontFamily = Pretendard), headlineLarge = base.headlineLarge.copy(fontFamily = Pretendard),
        headlineMedium = base.headlineMedium.copy(fontFamily = Pretendard), headlineSmall = base.headlineSmall.copy(fontFamily = Pretendard),
        titleLarge = base.titleLarge.copy(fontFamily = Pretendard), titleMedium = base.titleMedium.copy(fontFamily = Pretendard),
        titleSmall = base.titleSmall.copy(fontFamily = Pretendard), bodyLarge = base.bodyLarge.copy(fontFamily = Pretendard),
        bodyMedium = base.bodyMedium.copy(fontFamily = Pretendard), bodySmall = base.bodySmall.copy(fontFamily = Pretendard),
        labelLarge = base.labelLarge.copy(fontFamily = Pretendard), labelMedium = base.labelMedium.copy(fontFamily = Pretendard),
        labelSmall = base.labelSmall.copy(fontFamily = Pretendard),
    )
}

@Composable
internal fun NavigationTheme(night: Boolean, content: @Composable () -> Unit) {
    val scheme = if (night) darkColorScheme(
        primary = Color(0xFFABC9FF), onPrimary = Color(0xFF211538), surface = Color(0xFF222630),
        surfaceVariant = Color(0xFF29374E), background = Color(0xFF161B24), onSurface = Color(0xFFF4F5F8),
        onSurfaceVariant = Color(0xFFB6BDCC), outlineVariant = Color(0xFF444B5A),
    ) else lightColorScheme(
        primary = RoutePurple, onPrimary = Color.White, surface = Color.White,
        surfaceVariant = Color(0xFFF3EFFB), background = Color(0xFFF5F6F8), onSurface = Color(0xFF23252B),
        onSurfaceVariant = Color(0xFF747B8A), outlineVariant = Color(0xFFE0E3E9),
    )
    MaterialTheme(colorScheme = scheme, typography = TypographyWithPretendard, content = content)
}
