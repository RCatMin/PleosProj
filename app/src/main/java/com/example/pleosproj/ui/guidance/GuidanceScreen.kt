package com.example.pleosproj.ui.guidance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.hazardColor
import com.example.pleosproj.ui.state.*
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF232228)
private val Muted = Color(0xFF68666D)

// 화면은 상태와 사용자 동작만 주고받는다. SDK·ViewModel은 화면 안에서 호출하지 않는다.
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
) {
    Column(modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                mapContent(Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopStart).padding(24.dp).width(420.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ManeuverCard(state.nextTurn)
                    if (state.weather.isNotEmpty()) WeatherPanel(state.weather.take(2))
                }
                Column(Modifier.align(Alignment.TopEnd).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SurfaceButton(if (driverView) "위에서 보기" else "운전자 시점", onToggleViewpoint)
                    SurfaceButton("내 위치로", onRecenter)
                }
                state.alert?.let { alert ->
                    val kind = state.hazards.firstOrNull { it.id == alert.hazardId }?.kind ?: HazardKind.FLOOD
                    AlertCard(alert, Color(hazardColor(kind)),
                        onAcknowledge = { onAcknowledge(alert.hazardId) }, onDetour = onDetour,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 64.dp).width(520.dp))
                }
                if (state.routeLine.isNullOrEmpty()) {
                    Surface(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 64.dp),
                        color = Color.White, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("경로선 없이 안내 중", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            Text("오른쪽 진행 막대에서 앞쪽 구간을 확인하세요", fontSize = 19.sp, color = Muted)
                        }
                    }
                }
            }
            RouteProgressRail(state.progress, state.remainingMeters,
                Modifier.width(if (state.routeLine.isNullOrEmpty()) 290.dp else 260.dp).fillMaxHeight())
        }
        ArrivalBar(state, voiceMuted, onOtherRoutes, onToggleVoice, onEnd)
    }
}

@Composable
private fun ManeuverCard(turn: TurnInstruction?) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 2.dp) {
        if (turn == null) {
            Column(Modifier.fillMaxWidth().padding(26.dp)) {
                Text("안내 대기", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("주변 위험 정보를 확인하세요", fontSize = 24.sp, color = Muted)
            }
        } else Row(Modifier.fillMaxWidth().padding(26.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TurnGlyph(turn.kind, Modifier.size(94.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(distanceText(turn.distanceMeters), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(turn.text.ifBlank { turnName(turn.kind) }, fontSize = 29.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(turnName(turn.kind), fontSize = 21.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun WeatherPanel(weather: List<WeatherCard>) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.97f), shadowElevation = 1.dp) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("경로 날씨", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            weather.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = Color(0xFFEEECEF))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    WeatherGlyph(item.rainMmPerHour > 0, item.snow, Modifier.size(60.dp), animated = false)
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        val rain = if (item.snow) "눈" else if (item.rainMmPerHour > 0) "비 ${item.rainMmPerHour}mm/h" else "비 없음"
                        Text("$rain · 강수 ${item.precipitationChance}%", fontSize = 18.sp, color = Muted, maxLines = 1)
                    }
                    Text("${item.temperatureC}°", fontSize = 34.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: HazardAlert, color: Color, onAcknowledge: () -> Unit, onDetour: () -> Unit, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 3.dp) {
        Row {
            Box(Modifier.width(7.dp).height(210.dp).background(color))
            Column(Modifier.weight(1f).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(alert.title, fontSize = 29.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(alert.message, fontSize = 23.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAcknowledge, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("확인", fontSize = 22.sp, color = Ink)
                    }
                    if (alert.suggestsDetour) Button(onClick = onDetour,
                        colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp)) {
                        Text("우회", fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalBar(state: GuidanceState, voiceMuted: Boolean, onOtherRoutes: () -> Unit, onToggleVoice: () -> Unit, onEnd: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column {
                    Text("도착 예정", fontSize = 20.sp, color = Muted)
                    Text(state.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                }
                VerticalDivider(Modifier.height(60.dp), color = Color(0xFFE7E4EB))
                Column {
                    Text("${state.destinationName}까지", fontSize = 20.sp, color = Muted, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(durationText(state.remainingSeconds), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                        Text(distanceText(state.remainingMeters), fontSize = 29.sp, color = Muted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOtherRoutes, contentPadding = PaddingValues(horizontal = 26.dp, vertical = 20.dp)) {
                    Text("다른 경로", fontSize = 24.sp, color = Ink)
                }
                OutlinedButton(onClick = onToggleVoice, contentPadding = PaddingValues(horizontal = 26.dp, vertical = 20.dp)) {
                    Text(if (voiceMuted) "음성 꺼짐" else "음성 켜짐", fontSize = 24.sp, color = Ink)
                }
                Button(onClick = onEnd, colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    contentPadding = PaddingValues(horizontal = 30.dp, vertical = 20.dp)) {
                    Text("안내 종료", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun SurfaceButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Ink),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp)) { Text(label, fontSize = 21.sp) }
}
