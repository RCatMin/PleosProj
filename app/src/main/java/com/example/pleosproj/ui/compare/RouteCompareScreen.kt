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
import com.example.pleosproj.ui.theme.NavigationViewport
import com.example.pleosproj.ui.components.*
import com.example.pleosproj.ui.state.*
import java.time.format.DateTimeFormatter

// 경로별 지점·통과 시각·예보와 근거는 연결부가 만든 값을 그대로 표시한다.
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
    currentRemainingSeconds: Int? = null,
    onContinue: (() -> Unit)? = null,
    mapControls: @Composable () -> Unit = {},
    onBack: (() -> Unit)? = null,
    toolbar: @Composable () -> Unit = {},
) {
    val selected = state.selectedRoute()
    val colors = MaterialTheme.colorScheme
    NavigationViewport(modifier.fillMaxSize().background(colors.background)) {
        mapContent(Modifier.fillMaxSize())
        val panelWidth = minOf(408.dp, maxWidth * .38f)
        Column(Modifier.padding(16.dp).width(panelWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavigationIcon(NavigationIconKind.CAR, Modifier.size(24.dp), colors.primary)
                    Text(state.destinationName, Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    onBack?.let { TextButton(onClick = it) { Text(if (onContinue != null) "돌아가기" else "변경", fontSize = 15.sp) } }
                }
            }
            state.adviceWhenAllRisky?.let {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                    Text(it, Modifier.padding(14.dp), fontSize = 16.sp, color = colors.primary)
                }
            }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onContinue != null) Text("안내 중 · 다른 길 비교", Modifier.padding(6.dp), fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.candidates.isEmpty()) Text("비교할 경로가 없습니다", Modifier.padding(20.dp), fontSize = 22.sp)
                        val reference = currentRemainingSeconds ?: state.candidates.filterNot { it.excluded }.minOfOrNull { it.durationSeconds }
                        state.candidates.forEach { candidate ->
                            RouteCard(candidate, candidate.id == selected?.id, candidate.id == state.recommendedId,
                                inputEnabled, reference, currentRemainingSeconds != null,
                                state.forecastBasis, onClick = { onSelect(candidate.id) })
                        }
                    }
                    startNotice?.let { Text(it, Modifier.padding(horizontal = 8.dp), fontSize = 14.sp, color = colors.onSurfaceVariant) }
                    Button(onClick = { selected?.let { onStart(it.id) } }, enabled = selected != null && inputEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF23252B), contentColor = androidx.compose.ui.graphics.Color.White)) {
                        Text(if (onContinue != null) "이 길로 안내 시작" else startActionLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    onContinue?.let { action ->
                        OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                            Text("지금 길로 계속 가기", fontSize = 17.sp)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).offset(x = 100.dp)) { toolbar() }
        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            mapControls()
            TextButton(onClick = onFitRoutes) { Text("전체 경로", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun RouteCard(candidate: RouteCandidate, selected: Boolean, recommended: Boolean, inputEnabled: Boolean,
    referenceSeconds: Int?, comparingCurrent: Boolean, forecastBasis: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.surfaceVariant else colors.surface, label = "route-selection")
    Surface(Modifier.fillMaxWidth().animateContentSize().selectable(selected = selected,
        enabled = !candidate.excluded && inputEnabled, role = Role.RadioButton, onClick = onClick)
        .semantics { stateDescription = if (candidate.excluded) "통제로 제외, 선택 불가" else if (selected) "선택됨" else "선택 안 됨" },
        shape = RoundedCornerShape(16.dp), color = background, contentColor = colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.surfaceVariant else colors.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(candidate.name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (recommended || candidate.excluded) Surface(shape = RoundedCornerShape(8.dp), color = colors.surface) {
                    Text(if (candidate.excluded) "통제로 제외" else "안전 추천", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DurationReadout(candidate.durationSeconds)
                referenceSeconds?.let {
                    Text(routeTimeDifference(candidate.durationSeconds, it, comparingCurrent), Modifier.padding(bottom = 5.dp),
                        fontSize = 15.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(distanceText(candidate.distanceMeters), fontSize = 14.sp, color = colors.onSurfaceVariant)
                Text(tollText(candidate.tollWon), fontSize = 14.sp, color = colors.onSurfaceVariant)
            }
            Text(candidate.riskSummary, fontSize = 14.sp, lineHeight = 20.sp,
                color = if (candidate.excluded) colors.primary else colors.onSurface, maxLines = if (selected) 3 else 1, overflow = TextOverflow.Ellipsis)
            if (selected) {
                forecastBasis?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
                }
                if (candidate.forecast.isEmpty()) Text("경로 예보 정보 없음", fontSize = 14.sp, color = colors.onSurfaceVariant)
                else RouteWeatherTimeline(candidate.forecast)
            }
        }
    }
}

@Composable
private fun DurationReadout(seconds: Int) {
    val minutes = (seconds.coerceAtLeast(0) + 59) / 60
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (minutes >= 60) {
            Text("${minutes / 60}", fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Text("시간", Modifier.padding(bottom = 5.dp, end = 3.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("${minutes % 60}", fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text("분", Modifier.padding(bottom = 5.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RouteWeatherTimeline(forecast: List<RouteForecastPoint>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.fillMaxWidth().height(6.dp), shape = RoundedCornerShape(4.dp), color = colors.primary.copy(alpha = .65f)) {}
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            forecast.forEach { point ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(point.label, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(point.passTime.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(distanceText(point.distanceMeters), fontSize = 11.sp, color = colors.onSurfaceVariant)
                    val weather = point.weather
                    if (weather != null) {
                        WeatherGlyph(weather.rainMmPerHour > 0, weather.snow, Modifier.size(32.dp), animated = false)
                        Text("${weather.temperatureC}°", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Spacer(Modifier.height(32.dp))
                        Text("—", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("예보 없음", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                }
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
