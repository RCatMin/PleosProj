package com.example.pleosproj.ui.guidance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.hazardColor
import com.example.pleosproj.ui.state.*
import kotlin.math.max

internal fun distanceText(meters: Int): String = if (meters < 1000) "${meters.coerceAtLeast(0)}m" else
    "%.1fkm".format(java.util.Locale.KOREA, meters / 1000.0)

internal fun durationText(seconds: Int): String {
    val minutes = (seconds.coerceAtLeast(0) + 59) / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

@Composable
internal fun RouteProgressRail(progress: RouteProgress, remainingMeters: Int, modifier: Modifier = Modifier) {
    val purple = Color(0xFF7145D6)
    val current = progress.currentRatio.coerceIn(0.0, 1.0)
    Column(modifier.background(Color.White).padding(horizontal = 22.dp, vertical = 24.dp)) {
        Text("남은 경로", fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(distanceText(remainingMeters), fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("목적지", fontSize = 20.sp, color = Color(0xFF68666D))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp)) {
            val height = maxHeight
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "전체 경로의 ${(current * 100).toInt()}% 지점" }) {
                val x = 22.dp.toPx()
                fun y(ratio: Double): Float = ((1 - ratio.coerceIn(0.0, 1.0)) * size.height).toFloat()
                drawLine(Color(0xFFE7E4EB), Offset(x, 0f), Offset(x, size.height), 12.dp.toPx(), StrokeCap.Round)
                drawLine(purple, Offset(x, 0f), Offset(x, y(current)), 12.dp.toPx(), StrokeCap.Round)
                progress.segments.filter { max(it.startRatio, it.endRatio) >= current }.forEach { segment ->
                    val color = segmentColor(segment)
                    if (segment.kind == SegmentKind.TURN) {
                        drawCircle(Color.White, 12.dp.toPx(), Offset(x, y(segment.startRatio)))
                        drawCircle(color, 7.dp.toPx(), Offset(x, y(segment.startRatio)))
                    } else {
                        drawLine(color, Offset(x, y(segment.startRatio)), Offset(x, y(segment.endRatio)),
                            22.dp.toPx(), StrokeCap.Round)
                    }
                }
                val currentY = y(current)
                val arrow = Path().apply {
                    moveTo(x, currentY - 17.dp.toPx())
                    lineTo(x + 16.dp.toPx(), currentY + 15.dp.toPx())
                    lineTo(x, currentY + 8.dp.toPx())
                    lineTo(x - 16.dp.toPx(), currentY + 15.dp.toPx())
                    close()
                }
                drawCircle(Color.White, 23.dp.toPx(), Offset(x, currentY))
                drawPath(arrow, purple)
            }
            Text("내 위치", Modifier.offset(x = 48.dp, y = height * (1 - current).toFloat() - 14.dp),
                fontSize = 21.sp, color = purple, fontWeight = FontWeight.SemiBold)
            // 라벨은 현재 위치 앞의 구간만 보여 주고, 경로의 원래 순서·비율은 바꾸지 않는다.
            progress.segments.filter { max(it.startRatio, it.endRatio) > current }.forEach { segment ->
                val middle = (segment.startRatio + segment.endRatio) / 2
                val top = (height * (1 - middle.coerceIn(0.0, 1.0)).toFloat() - 24.dp)
                    .coerceIn(0.dp, (height - 50.dp).coerceAtLeast(0.dp))
                val label = segment.label ?: when (segment.kind) {
                    SegmentKind.TURN -> "회전"
                    SegmentKind.CONGESTION_SLOW -> "서행"
                    SegmentKind.CONGESTION_HEAVY -> "정체"
                    SegmentKind.HAZARD -> "위험 구간"
                }
                Column(Modifier.offset(x = 48.dp, y = top)) {
                    Text(label, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = segmentColor(segment), maxLines = 1)
                    val distance = ((segment.startRatio - current).coerceAtLeast(0.0) * progress.totalMeters).toInt()
                    Text(distanceText(distance), fontSize = 19.sp, color = Color(0xFF68666D))
                }
            }
        }
        Text("출발", fontSize = 20.sp, color = Color(0xFF68666D))
    }
}

private fun segmentColor(segment: ProgressSegment): Color = when (segment.kind) {
    SegmentKind.TURN -> Color(0xFF7145D6)
    SegmentKind.CONGESTION_SLOW -> Color(0xFFC39A25)
    SegmentKind.CONGESTION_HEAVY -> Color(0xFFD05252)
    SegmentKind.HAZARD -> Color(hazardColor(segment.hazardKind ?: HazardKind.FLOOD))
}
