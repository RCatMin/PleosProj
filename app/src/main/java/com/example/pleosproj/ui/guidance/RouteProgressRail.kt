package com.example.pleosproj.ui.guidance

import com.example.pleosproj.ui.theme.NavigationBlue
import com.example.pleosproj.ui.theme.NavigationBlueLight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.hazardColor
import com.example.pleosproj.ui.hazardName
import com.example.pleosproj.ui.state.*
import kotlin.math.max

internal fun distanceText(meters: Int): String = if (meters < 1000) "${meters.coerceAtLeast(0)}m" else
    "%.1fkm".format(java.util.Locale.KOREA, meters / 1000.0)

internal fun durationText(seconds: Int): String {
    val minutes = (seconds.coerceAtLeast(0) + 59) / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

@Composable
internal fun RouteProgressRail(progress: RouteProgress, remainingMeters: Int, modifier: Modifier = Modifier,
    hazards: List<Hazard> = emptyList(), nextTurn: TurnInstruction? = null,
    onSeekRatio: ((Double) -> Unit)? = null) {
    val current = progress.currentRatio.coerceIn(0.0, 1.0)
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < .5f
    val routeColor = if (dark) NavigationBlueLight else NavigationBlue
    val seek by rememberUpdatedState(onSeekRatio)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("남은 경로", fontSize = 14.sp, color = colors.onSurfaceVariant)
        Text(distanceText(remainingMeters), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("목적지", fontSize = 12.sp, color = colors.onSurfaceVariant)
        if (onSeekRatio != null) Text("눌러서 이동", fontSize = 12.sp, color = routeColor)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val height = maxHeight
            val labeled = progressForLabels(progress, hazards).let { base ->
                if (nextTurn == null || progress.totalMeters <= 0) base else base.copy(segments = base.segments +
                    ProgressSegment(SegmentKind.TURN, (current + nextTurn.distanceMeters.toDouble() / progress.totalMeters).coerceIn(0.0, 1.0),
                        (current + nextTurn.distanceMeters.toDouble() / progress.totalMeters).coerceIn(0.0, 1.0), nextTurn.text))
            }
            val seekModifier = if (onSeekRatio == null) Modifier else Modifier
                .pointerInput(Unit) {
                    detectTapGestures { point -> railRatioAt(point.y, size.height.toFloat())?.let { seek?.invoke(it) } }
                }.semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..1f)
                    setProgress { value ->
                        if (value.isFinite()) { seek?.invoke(value.coerceIn(0f, 1f).toDouble()); true } else false
                    }
                }
            Canvas(Modifier.fillMaxSize().then(seekModifier).semantics { contentDescription = "${distanceText(remainingMeters)} 남음 · 전체 경로의 ${(current * 100).toInt()}% 지점" }) {
                val x = 10.dp.toPx()
                fun y(ratio: Double): Float = ((1 - ratio.coerceIn(0.0, 1.0)) * size.height).toFloat()
                drawLine(routeColor.copy(alpha = .18f), Offset(x, 0f), Offset(x, size.height), 10.dp.toPx())
                drawLine(routeColor, Offset(x, 0f), Offset(x, y(current)), 10.dp.toPx())
                progress.segments.filter { max(it.startRatio, it.endRatio) >= current }.forEach { segment ->
                    if (segment.kind == SegmentKind.CONGESTION_HEAVY || segment.kind == SegmentKind.CONGESTION_SLOW)
                        drawLine(segmentColor(segment), Offset(x, y(segment.startRatio)), Offset(x, y(segment.endRatio)), 10.dp.toPx(), StrokeCap.Round)
                }
                val here = y(current).coerceIn(14.dp.toPx(), (size.height - 14.dp.toPx()).coerceAtLeast(14.dp.toPx()))
                drawCircle(colors.surface, 15.dp.toPx(), Offset(x, here))
                drawPath(Path().apply {
                    moveTo(x, here - 11.dp.toPx()); lineTo(x + 8.dp.toPx(), here + 8.dp.toPx())
                    lineTo(x, here + 4.dp.toPx()); lineTo(x - 8.dp.toPx(), here + 8.dp.toPx()); close()
                }, routeColor)
            }
            railLabels(labeled, height.value).forEach { item ->
                val segment = item.segment
                val distance = ((segment.startRatio - current).coerceAtLeast(0.0) * progress.totalMeters).toInt()
                val title = when (segment.kind) {
                        SegmentKind.HAZARD -> hazardName(segment.hazardKind ?: HazardKind.FLOOD) + " 주의"
                        SegmentKind.CONGESTION_HEAVY -> "정체"
                        SegmentKind.CONGESTION_SLOW -> "서행"
                        SegmentKind.TURN -> segment.label?.ifBlank { "다음 회전" } ?: "다음 회전"
                    }
                val jump = if (onSeekRatio == null) Modifier else Modifier.clickable(onClickLabel = "$title 지점 앞으로 이동") {
                    seek?.invoke(railEventRatio(segment.startRatio, progress.totalMeters))
                }
                Column(Modifier.padding(start = 26.dp).offset(y = item.top.dp).fillMaxWidth().heightIn(min = 48.dp).then(jump)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (dark) lerp(segmentColor(segment), Color.White, .35f) else segmentColor(segment), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (segment.endRatio > segment.startRatio && current >= segment.startRatio) "지나는 중" else distanceText(distance),
                        fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            Text("내 위치", Modifier.padding(start = 26.dp).offset(y = (height * (1 - current).toFloat() - 16.dp)
                .coerceIn(0.dp, (height - 28.dp).coerceAtLeast(0.dp))), fontSize = 12.sp, color = routeColor)
        }
        Text("출발", fontSize = 12.sp, color = colors.onSurfaceVariant)
    }
}

// 위쪽은 목적지, 아래쪽은 출발이다. 막대 좌표와 재생 진행률의 방향을 맞춘다.
internal fun railRatioAt(y: Float, height: Float): Double? =
    if (!y.isFinite() || !height.isFinite() || height <= 0f) null else (1.0 - y / height).coerceIn(0.0, 1.0)

// 회전·경고 장면을 바로 촬영할 수 있도록 항목을 누르면 약 200m 앞부터 보여 준다.
internal fun railEventRatio(ratio: Double, totalMeters: Int): Double =
    if (totalMeters > 0) (ratio - 200.0 / totalMeters).coerceIn(0.0, 1.0) else ratio.coerceIn(0.0, 1.0)

// 색칠한 위험 구간의 시작과 실제 위험 지점은 다르다. 거리 이름표는 지점 좌표의 비율을 쓴다.
internal fun progressForLabels(progress: RouteProgress, hazards: List<Hazard>): RouteProgress {
    val points = hazards.mapNotNull { hazard -> hazard.ratioOnRoute?.let { ratio ->
        ProgressSegment(SegmentKind.HAZARD, ratio, ratio, hazard.title, hazard.kind)
    } }
    return progress.copy(segments = progress.segments.filter {
        it.kind != SegmentKind.TURN && (points.isEmpty() || it.kind != SegmentKind.HAZARD)
    } + points)
}

private fun segmentColor(segment: ProgressSegment): Color = when (segment.kind) {
    SegmentKind.TURN -> NavigationBlue
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
