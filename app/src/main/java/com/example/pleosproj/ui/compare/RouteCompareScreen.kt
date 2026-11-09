package com.example.pleosproj.ui.compare

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.guidance.*
import com.example.pleosproj.ui.hazardName
import com.example.pleosproj.ui.state.*
import com.example.pleosproj.ui.theme.NavigationViewport
import com.example.pleosproj.ui.theme.RoutePurple
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// 경로 선택은 카드와 선의 강조로 구분한다. 지도에 후보 이름을 겹쳐 쓰지 않는다.
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
    val reference = currentRemainingSeconds ?: state.candidates.filterNot { it.excluded }.minOfOrNull { it.durationSeconds }
    NavigationViewport(modifier.fillMaxSize().background(colors.background)) {
        val cardWidth = (maxWidth - 48.dp) / minOf(state.candidates.size.coerceAtLeast(1), 2)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                mapContent(Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopStart).padding(16.dp).fillMaxWidth(.87f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                            Column(Modifier.widthIn(max = 380.dp).padding(horizontal = 18.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${state.destinationName}까지", Modifier.weight(1f, fill = false), fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    onBack?.let { TextButton(onClick = it, contentPadding = PaddingValues(4.dp)) {
                                        Text(if (onContinue != null) "돌아가기" else "변경", fontSize = 13.sp)
                                    } }
                                }
                                Text(if (onContinue != null) "안내 중 · 다른 경로 비교" else "위험을 비교하고 경로를 선택하세요",
                                    fontSize = 14.sp, color = colors.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        toolbar()
                    }
                    val hazards = state.candidates.flatMap { it.hazards }.distinctBy { it.id }.groupBy { it.kind }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        hazards.forEach { (kind, points) ->
                            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface) {
                                Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HazardGlyph(kind, Modifier.size(20.dp))
                                    Text("${hazardName(kind)} ${points.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    state.adviceWhenAllRisky?.let { Surface(shape = RoundedCornerShape(12.dp)) {
                        Text(it, Modifier.padding(12.dp), fontSize = 15.sp)
                    } }
                }
                Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                    mapControls()
                    TextButton(onClick = onFitRoutes) { Text("전체 경로 보기", fontSize = 13.sp) }
                }
            }
            if (state.candidates.isEmpty()) Text("비교할 경로가 없습니다", Modifier.padding(24.dp), fontSize = 22.sp)
            else Row(Modifier.fillMaxWidth().height(306.dp).horizontalScroll(rememberScrollState())
                .selectableGroup().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.candidates.forEach { candidate ->
                    RouteCard(candidate, candidate.id == selected?.id, candidate.id == state.recommendedId,
                        inputEnabled, reference, currentRemainingSeconds != null, state.forecastBasis,
                        Modifier.width(cardWidth).fillMaxHeight(), onClick = { onSelect(candidate.id) })
                }
            }
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(selected?.let { "${it.name} 경로 선택" } ?: "안내할 수 있는 경로가 없습니다", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(startNotice ?: selected?.let { "${durationText(it.durationSeconds)} · ${distanceText(it.distanceMeters)} · ${tollText(it.tollWon)}" }.orEmpty(),
                            fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.candidates.size > 2) Text("후보 ${state.candidates.size}개 · 좌우로 보기", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    onContinue?.let { action -> OutlinedButton(onClick = action) { Text("지금 길로 계속 가기", fontSize = 16.sp) } }
                    Button(onClick = { selected?.let { onStart(it.id) } }, enabled = selected != null && inputEnabled,
                        modifier = Modifier.widthIn(min = 180.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23252B), contentColor = Color.White)) {
                        Text(if (onContinue != null) "이 길로 안내 시작" else startActionLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(candidate: RouteCandidate, selected: Boolean, recommended: Boolean, inputEnabled: Boolean,
    referenceSeconds: Int?, comparingCurrent: Boolean, forecastBasis: String?, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.surfaceVariant else colors.surface, label = "route-selection")
    Surface(modifier.selectable(selected = selected, enabled = !candidate.excluded && inputEnabled,
        role = Role.RadioButton, onClick = onClick).semantics {
        stateDescription = if (candidate.excluded) "통제로 제외, 선택 불가" else if (selected) "선택됨" else "선택 안 됨"
    }, shape = RoundedCornerShape(18.dp), color = background, contentColor = colors.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) RoutePurple else colors.outlineVariant)) {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(candidate.name, Modifier.weight(1f), fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (recommended || candidate.excluded) Text(if (candidate.excluded) "통제로 제외" else "안전 추천", fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, color = if (candidate.excluded) Color(0xFF9C2864) else RoutePurple)
                if (selected) Text("선택됨", fontSize = 12.sp, color = RoutePurple)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DurationReadout(candidate.durationSeconds)
                Text(distanceText(candidate.distanceMeters), Modifier.padding(bottom = 4.dp), fontSize = 16.sp, color = colors.onSurfaceVariant)
                Text(tollText(candidate.tollWon), Modifier.padding(bottom = 4.dp), fontSize = 12.sp, color = colors.onSurfaceVariant)
                referenceSeconds?.let { Text(routeTimeDifference(candidate.durationSeconds, it, comparingCurrent), Modifier.padding(bottom = 4.dp),
                    fontSize = 13.sp, color = colors.onSurfaceVariant) }
            }
            Text(candidate.riskSummary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("위험도 ${(candidate.riskScore.coerceIn(0.0, 1.0) * 100).roundToInt()} / 100", fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
                Text(when (candidate.riskLevel) { RiskLevel.NONE -> "위험 없음"; RiskLevel.LOW -> "낮음"; RiskLevel.MEDIUM -> "주의"; RiskLevel.HIGH -> "높음" },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = when (candidate.riskLevel) {
                        RiskLevel.HIGH -> Color(0xFF9C2864); RiskLevel.MEDIUM -> Color(0xFF886344); else -> Color(0xFF16776F)
                    })
            }
            if (selected) {
                HorizontalDivider(color = colors.outlineVariant)
                forecastBasis?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant) }
                if (candidate.forecast.isEmpty()) Text("경로 예보 정보 없음", fontSize = 13.sp, color = colors.onSurfaceVariant)
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
            Text("${minutes / 60}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("시간", Modifier.padding(bottom = 4.dp, end = 3.dp), fontSize = 15.sp)
        }
        Text("${minutes % 60}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("분", Modifier.padding(bottom = 4.dp), fontSize = 15.sp)
    }
}

@Composable
private fun RouteWeatherTimeline(forecast: List<RouteForecastPoint>) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        forecast.forEach { point ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(point.passTime.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(if (point.label == "출발" || point.label == "도착") "${point.label} ${distanceText(point.distanceMeters)}" else point.label,
                    fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant, maxLines = 1)
                val weather = point.weather
                if (weather != null) {
                    WeatherGlyph(weather.rainMmPerHour > 0, weather.snow, Modifier.size(25.dp), animated = false)
                    Text("${weather.temperatureC}°", fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Spacer(Modifier.height(25.dp))
                    Text("예보 없음", fontSize = 12.sp, color = colors.onSurfaceVariant)
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
