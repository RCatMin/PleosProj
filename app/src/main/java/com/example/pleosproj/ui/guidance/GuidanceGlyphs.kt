package com.example.pleosproj.ui.guidance

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.pleosproj.ui.state.TurnKind
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

internal fun turnName(kind: TurnKind) = when (kind) {
    TurnKind.LEFT -> "좌회전"
    TurnKind.RIGHT -> "우회전"
    TurnKind.SHARP_LEFT -> "크게 좌회전"
    TurnKind.SHARP_RIGHT -> "크게 우회전"
    TurnKind.KEEP_LEFT -> "왼쪽 방향"
    TurnKind.KEEP_RIGHT -> "오른쪽 방향"
    TurnKind.U_TURN -> "유턴"
    TurnKind.ENTER_HIGHWAY -> "고속도로 진입"
    TurnKind.EXIT_HIGHWAY -> "고속도로 진출"
    TurnKind.TOLLGATE -> "요금소"
    TurnKind.TUNNEL -> "터널 진입"
    TurnKind.ARRIVE -> "도착"
    TurnKind.STRAIGHT -> "직진"
    TurnKind.UNKNOWN -> "안내 확인"
}

@Composable
internal fun TurnGlyph(kind: TurnKind, modifier: Modifier, color: Color = Color(0xFF232228)) {
    Canvas(modifier.semantics { contentDescription = turnName(kind) }) {
        val unit = size.minDimension / 100f
        val stroke = Stroke(width = 9 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(vararg points: Pair<Float, Float>): Path = Path().apply {
            points.forEachIndexed { index, (x, y) ->
                if (index == 0) moveTo(x * unit, y * unit) else lineTo(x * unit, y * unit)
            }
        }
        val right = kind in listOf(TurnKind.RIGHT, TurnKind.SHARP_RIGHT, TurnKind.KEEP_RIGHT, TurnKind.EXIT_HIGHWAY)
        scale(if (right) -1f else 1f, 1f) {
            when (kind) {
                TurnKind.LEFT, TurnKind.RIGHT -> {
                    val line = Path().apply {
                        moveTo(73 * unit, 87 * unit)
                        lineTo(73 * unit, 48 * unit)
                        quadraticTo(73 * unit, 29 * unit, 53 * unit, 29 * unit)
                        lineTo(18 * unit, 29 * unit)
                    }
                    drawPath(line, color, style = stroke)
                    drawPath(path(38f to 9f, 18f to 29f, 38f to 49f), color, style = stroke)
                }
                TurnKind.SHARP_LEFT, TurnKind.SHARP_RIGHT -> {
                    drawPath(path(75f to 87f, 75f to 22f, 25f to 62f), color, style = stroke)
                    drawPath(path(25f to 36f, 25f to 62f, 52f to 62f), color, style = stroke)
                }
                TurnKind.U_TURN -> {
                    val line = Path().apply {
                        moveTo(77 * unit, 87 * unit)
                        lineTo(77 * unit, 35 * unit)
                        cubicTo(77 * unit, 4 * unit, 23 * unit, 4 * unit, 23 * unit, 35 * unit)
                        lineTo(23 * unit, 68 * unit)
                    }
                    drawPath(line, color, style = stroke)
                    drawPath(path(5f to 50f, 23f to 68f, 41f to 50f), color, style = stroke)
                }
                TurnKind.KEEP_LEFT, TurnKind.KEEP_RIGHT, TurnKind.ENTER_HIGHWAY, TurnKind.EXIT_HIGHWAY -> {
                    drawPath(path(60f to 87f, 60f to 57f, 25f to 22f), color, style = stroke)
                    drawPath(path(25f to 48f, 25f to 22f, 51f to 22f), color, style = stroke)
                }
                TurnKind.ARRIVE -> {
                    drawPath(path(24f to 87f, 24f to 15f, 78f to 15f, 78f to 51f, 24f to 51f), color, style = stroke)
                }
                TurnKind.TOLLGATE, TurnKind.TUNNEL -> {
                    drawPath(path(15f to 85f, 15f to 38f, 35f to 15f, 65f to 15f, 85f to 38f, 85f to 85f), color, style = stroke)
                    drawPath(path(50f to 85f, 50f to 49f), color, style = stroke)
                }
                TurnKind.UNKNOWN -> {
                    val question = Path().apply {
                        moveTo(28 * unit, 29 * unit)
                        cubicTo(28 * unit, 5 * unit, 76 * unit, 5 * unit, 76 * unit, 31 * unit)
                        cubicTo(76 * unit, 49 * unit, 50 * unit, 47 * unit, 50 * unit, 66 * unit)
                    }
                    drawPath(question, color, style = stroke)
                    drawCircle(color, 5 * unit, Offset(50 * unit, 87 * unit))
                }
                TurnKind.STRAIGHT -> {
                    drawPath(path(50f to 87f, 50f to 13f), color, style = stroke)
                    drawPath(path(25f to 38f, 50f to 13f, 75f to 38f), color, style = stroke)
                }
            }
        }
    }
}

@Composable
internal fun WeatherGlyph(rain: Boolean, snow: Boolean, modifier: Modifier, animated: Boolean = true) {
    val phase = if (animated) rememberInfiniteTransition(label = "weather").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "weather-cycle",
    ) else null
    val weatherInk = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        val cycle = phase?.value ?: 0f
        val color = weatherInk
        val cloud = Path().apply {
            moveTo(20 * unit, 62 * unit)
            cubicTo(0f, 61 * unit, 4 * unit, 33 * unit, 26 * unit, 36 * unit)
            cubicTo(26 * unit, 8 * unit, 66 * unit, 9 * unit, 70 * unit, 36 * unit)
            cubicTo(95 * unit, 27 * unit, 105 * unit, 64 * unit, 78 * unit, 64 * unit)
            close()
        }
        // 아이콘의 작은 범위에서만 움직이고, 정보의 자리와 크기는 유지한다.
        translate(if (animated) sin(cycle * 2 * PI).toFloat() * 1.5f * unit else 0f, 0f) {
            drawPath(cloud, color.copy(alpha = .08f))
            drawPath(cloud, color, style = Stroke(3.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (rain || snow) for ((index, x) in listOf(29f, 50f, 71f).withIndex()) {
            val fall = if (animated) (cycle * 3 + index * 0.33f) % 1f else 0.5f
            val y = 75f + fall * 17f
            val dropColor = if (animated) color.copy(alpha = sin(fall * PI).toFloat().coerceIn(0f, 1f)) else color
            if (snow) {
                val drift = if (animated) sin(fall * PI * 2).toFloat() * 2f else 0f
                val center = Offset((x + drift) * unit, y * unit)
                drawCircle(dropColor, 4 * unit, center, style = Stroke(1.7f * unit))
            } else drawLine(dropColor, Offset(x * unit, y * unit), Offset((x - 4) * unit, (y + 7) * unit),
                4 * unit, StrokeCap.Round)
        }
    }
}
