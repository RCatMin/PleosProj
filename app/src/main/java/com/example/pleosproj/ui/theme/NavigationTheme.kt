package com.example.pleosproj.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

const val NavigationBlueHex = "#2A70FF"
val NavigationBlue = Color(0xFF2A70FF)
val NavigationBlueLight = Color(0xFFABC9FF)

// Pleos 기본 글꼴(Asta Sans)을 앱에 포함한 다른 글꼴로 덮어쓰지 않는다.
private val NavigationTypography = Typography()

@Composable
internal fun NavigationTheme(night: Boolean, content: @Composable () -> Unit) {
    val scheme = if (night) darkColorScheme(
        primary = NavigationBlueLight, onPrimary = Color(0xFF102446), surface = Color(0xFF222630),
        surfaceVariant = Color(0xFF29374E), background = Color(0xFF161B24), onSurface = Color(0xFFF4F5F8),
        onSurfaceVariant = Color(0xFFB6BDCC), outlineVariant = Color(0xFF444B5A),
    ) else lightColorScheme(
        primary = NavigationBlue, onPrimary = Color.White, surface = Color.White,
        surfaceVariant = Color(0xFFEEF4FF), background = Color(0xFFF5F6F8), onSurface = Color(0xFF23252B),
        onSurfaceVariant = Color(0xFF747B8A), outlineVariant = Color(0xFFE0E3E9),
    )
    val blueScheme = scheme.copy(
        primaryContainer = scheme.surfaceVariant, onPrimaryContainer = scheme.onSurface,
        secondary = scheme.primary, onSecondary = scheme.onPrimary,
        secondaryContainer = scheme.surfaceVariant, onSecondaryContainer = scheme.onSurface,
        tertiary = scheme.primary, onTertiary = scheme.onPrimary,
        tertiaryContainer = scheme.surfaceVariant, onTertiaryContainer = scheme.onSurface,
        inversePrimary = if (night) NavigationBlue else NavigationBlueLight,
        surfaceTint = scheme.primary,
        onBackground = scheme.onSurface,
        outline = if (night) Color(0xFF8D96A6) else Color(0xFF818895),
        inverseSurface = if (night) Color(0xFFE9EDF3) else Color(0xFF303640),
        inverseOnSurface = if (night) Color(0xFF23252B) else Color(0xFFF5F6F8),
        surfaceDim = if (night) Color(0xFF161B24) else Color(0xFFDCE1E9),
        surfaceBright = if (night) Color(0xFF353D4B) else Color.White,
        surfaceContainerLowest = if (night) Color(0xFF11161E) else Color.White,
        surfaceContainerLow = if (night) Color(0xFF1C222C) else Color(0xFFF7F9FC),
        surfaceContainer = if (night) Color(0xFF222A36) else Color(0xFFF1F5FB),
        surfaceContainerHigh = if (night) Color(0xFF293341) else Color(0xFFEBF1F9),
        surfaceContainerHighest = if (night) Color(0xFF323D4E) else Color(0xFFE3ECF8),
    )
    MaterialTheme(colorScheme = blueScheme, typography = NavigationTypography, content = content)
}
