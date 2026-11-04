package com.example.pleosproj.ui.briefing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.guidance.WeatherGlyph
import com.example.pleosproj.ui.guidance.durationText
import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.WeatherCard
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF232228)
private val Muted = Color(0xFF68666D)
private val Teal = Color(0xFF16776F)

// 정차 여부는 바깥에서 판단한다. 알 수 없는 상태에서는 입력을 열지 않는다.
@Composable
fun BriefingScreen(
    state: BriefingState,
    isParked: Boolean?,
    onStart: () -> Unit,
    onCompare: () -> Unit,
    onDrivingStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDrivingStarted by rememberUpdatedState(onDrivingStarted)
    LaunchedEffect(isParked) { if (isParked == false) currentOnDrivingStarted() }
    if (isParked == false) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("안내 화면으로 전환 중", fontSize = 32.sp, color = Ink)
        }
        return
    }
    Column(modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("오늘의 목적지", fontSize = 23.sp, color = Muted)
                    Text(state.destinationName, fontSize = 56.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold,
                        color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("도착 예정", fontSize = 23.sp, color = Muted)
                    Text(state.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 48.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                VerticalDivider(Modifier.height(72.dp), color = Color(0xFFDDDBE1))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("예상 소요", fontSize = 23.sp, color = Muted)
                    Text(durationText(state.durationSeconds), fontSize = 42.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RiskSummary(state, Modifier.weight(1.7f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("경로 날씨", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    if (state.weather.isEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                            Text("날씨 정보를 기다리고 있어요", Modifier.fillMaxWidth().padding(28.dp),
                                fontSize = 26.sp, lineHeight = 36.sp, color = Muted)
                        }
                    } else LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.weight(1f)) {
                        itemsIndexed(state.weather) { _, weather -> BriefingWeather(weather) }
                    }
                }
            }
            SpeakingStatus(state.speaking)
        }
        Surface(color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(if (isParked == true) "출발 전, 경로의 위험과 날씨를 확인하세요" else "정차 상태를 확인하면 출발할 수 있어요",
                    Modifier.weight(1f), fontSize = 23.sp, lineHeight = 32.sp, color = Muted)
                OutlinedButton(onClick = onCompare, enabled = isParked == true, shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 25.dp)) {
                    Text("경로 비교 보기", fontSize = 27.sp, color = if (isParked == true) Ink else Muted)
                }
                Button(onClick = onStart, enabled = isParked == true, shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    contentPadding = PaddingValues(horizontal = 44.dp, vertical = 25.dp)) {
                    Text("안전 경로로 출발", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RiskSummary(state: BriefingState, modifier: Modifier) {
    val clear = state.riskLines.isEmpty()
    val color = when {
        clear -> Teal
        state.overallLevel == RiskLevel.HIGH -> Color(0xFF9C2864)
        state.overallLevel == RiskLevel.MEDIUM -> Color(0xFF886344)
        else -> Teal
    }
    val level = when {
        clear -> "위험 없음"
        state.overallLevel == RiskLevel.HIGH -> "위험 높음"
        state.overallLevel == RiskLevel.MEDIUM -> "주의"
        state.overallLevel == RiskLevel.LOW -> "위험 낮음"
        else -> "경로 정보"
    }
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("경로 위험 요약", fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Surface(color = color.copy(alpha = 0.09f), shape = RoundedCornerShape(10.dp)) {
                    Text(level, Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = color)
                }
            }
            HorizontalDivider(color = Color(0xFFE8E5EC))
            if (clear) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(88.dp)) {
                    drawCircle(color.copy(alpha = 0.08f))
                    val path = Path().apply {
                        moveTo(size.width * 0.28f, size.height * 0.51f)
                        lineTo(size.width * 0.44f, size.height * 0.67f)
                        lineTo(size.width * 0.74f, size.height * 0.34f)
                    }
                    drawPath(path, color, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                }
                Spacer(Modifier.height(28.dp))
                Text("오늘 경로에 위험 없음", fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.riskLines) { index, line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
                        Text("%02d".format(index + 1), Modifier.padding(top = 6.dp), fontSize = 23.sp,
                            fontWeight = FontWeight.SemiBold, color = color)
                        Text(line, fontSize = 34.sp, lineHeight = 48.sp, color = Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefingWeather(weather: WeatherCard) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(26.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(weather.label, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                WeatherGlyph(weather.rainMmPerHour > 0, weather.snow, Modifier.size(86.dp))
                Text("${weather.temperatureC}°", fontSize = 48.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
            val precipitation = when {
                weather.snow -> "눈"
                weather.rainMmPerHour > 0 -> "비 ${weather.rainMmPerHour}mm/h"
                else -> "비 없음"
            }
            Text("$precipitation · 강수 ${weather.precipitationChance}%", fontSize = 24.sp, lineHeight = 34.sp, color = Muted)
        }
    }
}

@Composable
private fun SpeakingStatus(speaking: Boolean) {
    val color = if (speaking) Teal else Muted
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(width = 34.dp, height = 30.dp)) {
            val heights = if (speaking) listOf(0.3f, 0.7f, 1f, 0.5f, 0.3f) else List(5) { 0.2f }
            heights.forEachIndexed { index, height ->
                val x = size.width * (index + 0.5f) / heights.size
                drawLine(color, Offset(x, size.height * (1 - height) / 2),
                    Offset(x, size.height * (1 + height) / 2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Text(if (speaking) "음성 브리핑 중" else "음성 브리핑 대기", fontSize = 24.sp, color = color)
    }
}
