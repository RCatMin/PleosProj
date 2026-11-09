package com.example.pleosproj.ui.guidance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.compare.tollText
import com.example.pleosproj.ui.hazardColor
import com.example.pleosproj.ui.state.*
import com.example.pleosproj.ui.theme.ManeuverBlue
import com.example.pleosproj.ui.theme.NavigationViewport
import com.example.pleosproj.ui.components.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// SDK 호출 없이, 안내 엔진의 상태와 바깥에서 받은 차량 속도만 그린다.
@Composable
fun GuidanceScreen(
    state: GuidanceState,
    voiceMuted: Boolean,
    onAcknowledge: (String) -> Unit,
    onDetour: () -> Unit,
    onOtherRoutes: () -> Unit,
    onToggleVoice: () -> Unit,
    onEnd: () -> Unit,
    onToggleViewpoint: () -> Unit,
    onRecenter: () -> Unit,
    driverView: Boolean,
    mapContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    speedKph: Double? = null,
    tollWon: Int? = null,
    mapControls: (@Composable () -> Unit)? = null,
    toolbar: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    NavigationViewport(modifier.fillMaxSize().background(colors.background)) {
        mapContent(Modifier.fillMaxSize())
        val panelWidth = minOf(408.dp, maxWidth * .38f)
        Column(Modifier.padding(16.dp).width(panelWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManeuverCard(state, tollWon)
                AnimatedVisibility(state.alert != null) {
                    state.alert?.let { alert ->
                        val hazard = state.hazards.firstOrNull { it.id == alert.hazardId }
                        AlertCard(alert, hazard, state.progress, onAcknowledge = { onAcknowledge(alert.hazardId) },
                            onOtherRoutes = if (alert.suggestsDetour) onDetour else onOtherRoutes)
                    }
                }
                FacilityPanel(state)
            }
            WeatherPanel(state.weather, state.remainingMeters)
            Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOtherRoutes, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                        NavigationIcon(NavigationIconKind.OTHER_ROUTES, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("다른 길 보기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NavigationIcon(NavigationIconKind.MICROPHONE, Modifier.size(16.dp), colors.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("음성으로 선택", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        }
                        Text("준비 중", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    Button(onClick = onEnd, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23252B), contentColor = Color.White)) {
                        Text("안내 종료", fontSize = 15.sp, maxLines = 1)
                    }
                }
            }
        }
        RouteProgressRail(state.progress, state.remainingMeters,
            Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 88.dp, bottom = 18.dp).width(100.dp).fillMaxHeight(), hazards = state.hazards)
        Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).offset(x = 130.dp)) { toolbar() }
        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mapControls != null) mapControls()
            else {
                TextButton(onClick = onToggleViewpoint) { Text(if (driverView) "위에서 보기" else "운전석에서 보기") }
                TextButton(onClick = onRecenter) { Text("내 위치로") }
                TextButton(onClick = onToggleVoice) { Text(if (voiceMuted) "음성 켜기" else "음성 끄기") }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp).size(72.dp),
            shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(speedKph?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.roundToInt()?.toString() ?: "—", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(if (state.mode == DriveMode.SCENARIO) "km/h · 예시" else "km/h", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ManeuverCard(state: GuidanceState, tollWon: Int?) {
    val turn = state.nextTurn
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
        Column {
            Column(Modifier.fillMaxWidth().background(ManeuverBlue).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TurnGlyph(turn?.kind ?: TurnKind.STRAIGHT, Modifier.size(60.dp), Color.White)
                    if (turn != null) DistanceReadout(turn.distanceMeters, Modifier.weight(1f), color = Color.White)
                    else Text("안내 대기", Modifier.weight(1f), fontSize = 32.sp, color = Color.White)
                    state.upcomingTurns.firstOrNull()?.let { next ->
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .18f), contentColor = Color.White) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TurnGlyph(next.kind, Modifier.size(22.dp), Color.White)
                                Text(distanceText(next.distanceMeters), fontSize = 13.sp)
                            }
                        }
                    }
                }
                Text(turn?.text?.ifBlank { turnName(turn.kind) } ?: "주변 위험 정보를 확인하세요", fontSize = 18.sp,
                    lineHeight = 25.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("도착", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${distanceText(state.remainingMeters)} · ${durationText(state.remainingSeconds)} · ${tollWon?.let(::tollText) ?: "통행료 정보 없음"}",
                    fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlertCard(alert: HazardAlert, hazard: Hazard?, progress: RouteProgress, onAcknowledge: () -> Unit, onOtherRoutes: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color = Color(hazardColor(hazard?.kind ?: HazardKind.FLOOD))
    Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HazardGlyph(hazard?.kind ?: HazardKind.FLOOD, Modifier.size(46.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(alert.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 2)
                    Text(alert.message, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                hazard?.ratioOnRoute?.let { ratio ->
                    DistanceReadout(((ratio - progress.currentRatio).coerceAtLeast(0.0) * progress.totalMeters).toInt(), large = false)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOtherRoutes, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23252B), contentColor = Color.White)) {
                    NavigationIcon(NavigationIconKind.OTHER_ROUTES, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("다른 길 보기", fontSize = 16.sp)
                }
                OutlinedButton(onClick = onAcknowledge, modifier = Modifier.weight(1f)) { Text("확인", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun FacilityPanel(state: GuidanceState) {
    if (state.facilities.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("앞으로 지날 시설", fontSize = 13.sp, color = colors.onSurfaceVariant)
            state.facilities.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(when (item.kind) {
                        FacilityKind.IC -> "IC"
                        FacilityKind.JC -> "JC"
                        FacilityKind.TOLLGATE -> "TG"
                        FacilityKind.REST_AREA -> "휴게소"
                        FacilityKind.ROADSIDE_STOP -> "쉼터"
                    }, Modifier.width(44.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ManeuverBlue)
                    Text(item.name, Modifier.weight(1f), fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(distanceText(item.distanceMeters), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WeatherPanel(weather: List<WeatherCard>, remainingMeters: Int) {
    val colors = MaterialTheme.colorScheme
    val slots = guidanceWeatherSlots(weather, remainingMeters)
    Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(12.dp)) {
            Text("거리별 날씨", fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Row(Modifier.fillMaxWidth()) {
                slots.forEachIndexed { index, item ->
                    if (index > 0) VerticalDivider(Modifier.height(108.dp), color = colors.outlineVariant)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listOf("지금", "10km 앞", "20km 앞")[index], fontSize = 13.sp, color = colors.onSurfaceVariant)
                        if (item != null) WeatherGlyph(item.rainMmPerHour > 0, item.snow, Modifier.size(38.dp), animated = false)
                        else Spacer(Modifier.height(38.dp))
                        Text(item?.let { "${it.temperatureC}°" } ?: "—", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(when { item == null -> "정보 없음"; item.snow -> "눈"; item.rainMmPerHour > 0 -> "${item.rainMmPerHour}mm/h"; else -> "비 없음" },
                            fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DistanceReadout(meters: Int, modifier: Modifier = Modifier, large: Boolean = true, color: Color = MaterialTheme.colorScheme.onSurface) {
    val label = distanceText(meters)
    val unit = if (meters < 1000) "m" else "km"
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontSize = if (large) 44.sp else 30.sp, fontWeight = FontWeight.Bold)) { append(label.removeSuffix(unit)) }
        withStyle(SpanStyle(fontSize = if (large) 20.sp else 14.sp, fontWeight = FontWeight.SemiBold)) { append(unit) }
    }, modifier, color = color, maxLines = 1)
}

@Composable
internal fun HazardGlyph(kind: HazardKind, modifier: Modifier = Modifier) {
    val color = Color(hazardColor(kind))
    Canvas(modifier.semantics { contentDescription = when (kind) {
        HazardKind.ICE -> "결빙"; HazardKind.FLOOD -> "침수"; HazardKind.HEAVY_RAIN -> "폭우"; HazardKind.HEAVY_SNOW -> "폭설"
        HazardKind.FOG -> "안개"; HazardKind.CLOSURE -> "통제"; HazardKind.ACCIDENT -> "사고"; HazardKind.CONSTRUCTION -> "공사"
    } }) {
        drawCircle(color, size.minDimension / 2)
        val w = size.width; val h = size.height
        drawPath(Path().apply { moveTo(w*.5f,h*.2f);lineTo(w*.8f,h*.75f);lineTo(w*.2f,h*.75f);close() }, Color.White, style = Stroke(2.dp.toPx()))
        drawLine(Color.White, Offset(w*.5f,h*.39f),Offset(w*.5f,h*.55f),2.dp.toPx())
        drawCircle(Color.White,1.3.dp.toPx(),Offset(w*.5f,h*.65f))
    }
}
