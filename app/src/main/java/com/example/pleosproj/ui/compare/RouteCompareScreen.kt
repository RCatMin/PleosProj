package com.example.pleosproj.ui.compare

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.guidance.*
import com.example.pleosproj.ui.state.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// 경로 값과 예보는 연결부가 만든 값을 그대로 쓴다. 정확도는 계산하지 않고 제공 여부를 표시한다.
@Composable
fun RouteCompareScreen(
    state: RouteCompareState,
    onSelect: (String) -> Unit,
    onStart: (String) -> Unit,
    onFitRoutes: () -> Unit,
    mapContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    startNotice: String? = null,
    startActionLabel: String = "안내 시작",
    inputEnabled: Boolean = true,
    weatherByRoute: Map<String, List<WeatherCard>> = emptyMap(),
    departureTime: LocalTime? = null,
    currentRemainingSeconds: Int? = null,
    onContinue: (() -> Unit)? = null,
    mapControls: @Composable () -> Unit = {},
) {
    val selected = state.selectedRoute()
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        mapContent(Modifier.fillMaxSize())
        val panelWidth = minOf(480.dp, maxWidth * .46f)
        Column(Modifier.padding(20.dp).width(panelWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("↗", fontSize = 28.sp, color = colors.primary, modifier = Modifier.padding(end = 16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (onContinue != null) "안내 중 · 다른 길 비교" else "목적지", fontSize = 16.sp, color = colors.onSurfaceVariant)
                        Text(state.destinationName, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.adviceWhenAllRisky?.let {
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceVariant) {
                        Text(it, Modifier.padding(18.dp), fontSize = 20.sp, color = colors.primary)
                    }
                }
                if (state.candidates.isEmpty()) Surface(shape = RoundedCornerShape(18.dp)) {
                    Text("비교할 경로가 없습니다", Modifier.fillMaxWidth().padding(24.dp), fontSize = 24.sp)
                }
                val reference = currentRemainingSeconds ?: state.candidates.filterNot { it.excluded }.minOfOrNull { it.durationSeconds }
                state.candidates.forEach { candidate ->
                    RouteCard(candidate, candidate.id == selected?.id, candidate.id == state.recommendedId,
                        inputEnabled, reference, currentRemainingSeconds != null,
                        weatherByRoute[candidate.id].orEmpty(), departureTime, onClick = { onSelect(candidate.id) })
                }
            }
            Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    startNotice?.let { Text(it, Modifier.padding(horizontal = 8.dp), fontSize = 18.sp, color = colors.onSurfaceVariant) }
                    Button(onClick = { selected?.let { onStart(it.id) } }, enabled = selected != null && inputEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF23252B), contentColor = androidx.compose.ui.graphics.Color.White)) {
                        Text(if (onContinue != null) "이 길로 안내 시작" else startActionLabel, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                    onContinue?.let { action ->
                        OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("지금 길로 계속 가기", fontSize = 22.sp)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(20.dp), horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onFitRoutes, colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surface)) {
                Text("전체 경로 보기", fontSize = 20.sp)
            }
            mapControls()
        }
    }
}

@Composable
private fun RouteCard(candidate: RouteCandidate, selected: Boolean, recommended: Boolean, inputEnabled: Boolean,
    referenceSeconds: Int?, comparingCurrent: Boolean, weather: List<WeatherCard>, departure: LocalTime?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.surfaceVariant else colors.surface, label = "route-selection")
    Surface(Modifier.fillMaxWidth().animateContentSize().selectable(selected = selected,
        enabled = !candidate.excluded && inputEnabled, role = Role.RadioButton, onClick = onClick)
        .semantics { stateDescription = if (candidate.excluded) "통제로 제외, 선택 불가" else if (selected) "선택됨" else "선택 안 됨" },
        shape = RoundedCornerShape(22.dp), color = background, contentColor = colors.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(candidate.name, Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (recommended || candidate.excluded) Text(if (candidate.excluded) "통제로 제외" else "안전 추천",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(durationText(candidate.durationSeconds), fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                referenceSeconds?.let {
                    Text(routeTimeDifference(candidate.durationSeconds, it, comparingCurrent), fontSize = 21.sp,
                        color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            }
            Text("${distanceText(candidate.distanceMeters)}  ·  ${tollText(candidate.tollWon)}", fontSize = 20.sp, color = colors.onSurfaceVariant)
            Text(candidate.riskSummary, fontSize = 19.sp, lineHeight = 26.sp,
                color = if (candidate.excluded) colors.primary else colors.onSurface, maxLines = if (selected) 3 else 1, overflow = TextOverflow.Ellipsis)
            if (selected) {
                HorizontalDivider(Modifier.padding(vertical = 3.dp), color = colors.outlineVariant)
                Text("예상 정확도 · 제공 안 됨", fontSize = 16.sp, color = colors.onSurfaceVariant)
                if (weather.isEmpty()) Text("경로 날씨를 받지 못했어요", fontSize = 18.sp, color = colors.onSurfaceVariant)
                else RouteWeatherTimeline(weather, departure)
            }
        }
    }
}

@Composable
private fun RouteWeatherTimeline(weather: List<WeatherCard>, departure: LocalTime?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        weather.take(4).forEachIndexed { index, item ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (index == 0) "${departure?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "지금"} 출발" else item.label,
                    fontSize = 17.sp, color = colors.onSurfaceVariant, maxLines = 1)
                WeatherGlyph(item.rainMmPerHour > 0, item.snow, Modifier.size(42.dp), animated = false)
                Text("${item.temperatureC}°", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text(if (item.snow) "눈" else if (item.rainMmPerHour > 0) "${item.rainMmPerHour}mm/h" else "비 없음", fontSize = 16.sp,
                    color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

internal fun tollText(won: Int): String = if (won == 0) "통행료 없음" else "통행료 ${"%,d".format(java.util.Locale.KOREA, won)}원"

internal fun routeTimeDifference(seconds: Int, reference: Int, current: Boolean): String {
    val difference = seconds - reference
    val minutes = (kotlin.math.abs(difference) + 30) / 60
    if (minutes == 0) return if (current) "지금 길과 비슷" else ""
    return "${if (difference > 0) "+" else "−"}${minutes}분${if (current) " · 지금 길 대비" else ""}"
}
