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
import androidx.compose.ui.text.style.TextOverflow
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
internal fun RouteProgressRail(progress: RouteProgress, remainingMeters: Int, modifier: Modifier = Modifier, hazards: List<Hazard> = emptyList()) {
    val current = progress.currentRatio.coerceIn(0.0, 1.0)
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.End) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = colors.surface) {
            Text(distanceText(remainingMeters), Modifier.padding(10.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp)) {
            val height = maxHeight
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "전체 경로의 ${(current * 100).toInt()}% 지점" }) {
                val x = size.width - 22.dp.toPx()
                fun y(ratio: Double): Float = ((1 - ratio.coerceIn(0.0, 1.0)) * size.height).toFloat()
                drawLine(colors.surface, Offset(x, 0f), Offset(x, size.height), 18.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFF7145D6), Offset(x, 0f), Offset(x, y(current)), 7.dp.toPx(), StrokeCap.Round)
                progress.segments.filter { max(it.startRatio, it.endRatio) >= current }.forEach { segment ->
                    if (segment.kind != SegmentKind.TURN) drawLine(segmentColor(segment), Offset(x, y(segment.startRatio)),
                        Offset(x, y(segment.endRatio)), 12.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(colors.surface, 15.dp.toPx(), Offset(x, y(current)))
                drawCircle(Color(0xFF7145D6), 10.dp.toPx(), Offset(x, y(current)))
            }
            railLabels(progressForLabels(progress, hazards), height.value).forEach { item ->
                val segment = item.segment
                val distance = ((segment.startRatio - current).coerceAtLeast(0.0) * progress.totalMeters).toInt()
                Row(Modifier.fillMaxWidth().offset(y = item.top.dp), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = colors.surface) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.End) {
                            Text(segment.label ?: if (segment.kind == SegmentKind.HAZARD) "위험" else "앞쪽 구간", fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, color = segmentColor(segment))
                            Text(if (segment.endRatio > segment.startRatio && current >= segment.startRatio) "지나는 중" else distanceText(distance),
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (segment.kind == SegmentKind.HAZARD) HazardGlyph(segment.hazardKind ?: HazardKind.FLOOD, Modifier.size(32.dp))
                    else Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(18.dp)) { drawCircle(segmentColor(segment)) }
                    }
                }
            }
        }
        Text("출발", fontSize = 16.sp, color = colors.onSurfaceVariant)
    }
}

// 색칠한 위험 구간의 시작과 실제 위험 지점은 다르다. 거리 이름표는 지점 좌표의 비율을 쓴다.
internal fun progressForLabels(progress: RouteProgress, hazards: List<Hazard>): RouteProgress {
    val points = hazards.mapNotNull { hazard -> hazard.ratioOnRoute?.let { ratio ->
        ProgressSegment(SegmentKind.HAZARD, ratio, ratio, hazard.title, hazard.kind)
    } }
    if (points.isEmpty()) return progress
    return progress.copy(segments = progress.segments.filterNot { it.kind == SegmentKind.HAZARD } + points)
}

private fun segmentColor(segment: ProgressSegment): Color = when (segment.kind) {
    SegmentKind.TURN -> Color(0xFF7145D6)
    SegmentKind.CONGESTION_SLOW -> Color(0xFFC39A25)
    SegmentKind.CONGESTION_HEAVY -> Color(0xFFD05252)
    SegmentKind.HAZARD -> Color(hazardColor(segment.hazardKind ?: HazardKind.FLOOD))
}

internal data class RailLabel(val segment: ProgressSegment, val top: Float)

internal fun railLabels(progress: RouteProgress, height: Float): List<RailLabel> {
    val current = progress.currentRatio.coerceIn(0.0, 1.0)
    val labelHeight = 58f
    val spacing = 76f
    // 내 위치 설명 위에 여유를 두고, 공간이 부족해지면 먼 구간부터 생략한다.
    val bottom = (height * (1 - current).toFloat() - 36f).coerceAtLeast(0f)
    val count = (((bottom - labelHeight) / spacing).toInt() + 1).coerceIn(0, 4)
        .let { if (bottom < labelHeight) 0 else it }
    val segments = progress.segments.filter { max(it.startRatio, it.endRatio) > current }
        .sortedBy { it.startRatio }.distinctBy { it.kind to it.label }.take(count).reversed()
    var previous = -spacing
    return segments.mapIndexed { index, segment ->
        val ideal = height * (1 - (segment.startRatio + segment.endRatio) / 2).toFloat() - labelHeight / 2
        val upper = bottom - labelHeight - (segments.lastIndex - index) * spacing
        val top = ideal.coerceIn(previous + spacing, upper.coerceAtLeast(previous + spacing))
        previous = top
        RailLabel(segment, top)
    }
}
