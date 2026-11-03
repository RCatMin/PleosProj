package com.example.pleosproj.ui.compare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.guidance.distanceText
import com.example.pleosproj.ui.guidance.durationText
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.RouteCandidate
import com.example.pleosproj.ui.state.RouteCompareState
import kotlin.math.roundToInt

private val Ink = Color(0xFF232228)
private val Muted = Color(0xFF68666D)
private val Purple = Color(0xFF7145D6)
private val Closure = Color(0xFF9C2864)

// 데이터에서 정한 추천·제외 상태를 보여 주고, 선택과 시작 요청만 바깥으로 전달한다.
@Composable
fun RouteCompareScreen(
    state: RouteCompareState,
    onSelect: (String) -> Unit,
    onStart: (String) -> Unit,
    onFitRoutes: () -> Unit,
    mapContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedRoute()
    Column(modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            mapContent(Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.TopStart).padding(24.dp).widthIn(max = 620.dp),
                shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.97f)) {
                Column(Modifier.padding(horizontal = 26.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${state.destinationName}까지", fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("위험을 비교하고 경로를 선택하세요", fontSize = 23.sp, color = Muted)
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(24.dp), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onFitRoutes,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Ink),
                    contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp)) {
                    Text("전체 경로 보기", fontSize = 22.sp)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.97f)) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        RouteLegend("선택", Purple)
                        RouteLegend("다른 경로", Color(0xFFB5A6D4))
                        RouteLegend("제외", Closure, dashed = true)
                    }
                }
            }
        }
        state.adviceWhenAllRisky?.let { advice ->
            Surface(color = Color(0xFFF3EAF0)) {
                Text(advice, Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                    fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = Closure)
            }
        }
        if (state.candidates.isEmpty()) {
            Text("비교할 경로가 없습니다", Modifier.fillMaxWidth().padding(32.dp), fontSize = 28.sp, color = Muted)
        } else Row(Modifier.fillMaxWidth().selectableGroup().padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            state.candidates.forEach { candidate ->
                RouteCard(candidate, selected = candidate.id == selected?.id,
                    recommended = candidate.id == state.recommendedId,
                    onClick = { onSelect(candidate.id) }, modifier = Modifier.weight(1f))
            }
        }
        Surface(color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(selected?.let { "${it.name} 경로 선택" } ?: "안내 가능한 경로가 없습니다",
                        fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(selected?.let { "${durationText(it.durationSeconds)} · ${distanceText(it.distanceMeters)} · ${it.riskSummary}" }
                        ?: "통제 상황을 확인한 뒤 다시 시도해 주세요", fontSize = 22.sp, color = Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = { selected?.let { onStart(it.id) } }, enabled = selected != null,
                    shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    contentPadding = PaddingValues(horizontal = 72.dp, vertical = 24.dp)) {
                    Text("안내 시작", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RouteCard(candidate: RouteCandidate, selected: Boolean, recommended: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color = when (candidate.riskLevel) {
        RiskLevel.NONE, RiskLevel.LOW -> Color(0xFF16776F)
        RiskLevel.MEDIUM -> Color(0xFF886344)
        RiskLevel.HIGH -> Closure
    }
    val level = when (candidate.riskLevel) {
        RiskLevel.NONE -> "위험 없음"
        RiskLevel.LOW -> "낮음"
        RiskLevel.MEDIUM -> "주의"
        RiskLevel.HIGH -> "높음"
    }
    Surface(modifier.selectable(selected = selected, enabled = !candidate.excluded, role = Role.RadioButton, onClick = onClick)
        .semantics { stateDescription = if (candidate.excluded) "통제로 제외, 선택 불가" else if (selected) "선택됨" else "선택 안 됨" },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFFF4F0FC) else if (candidate.excluded) Color(0xFFF0EFF2) else Color.White,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Purple else Color(0xFFDDDBE1))) {
        Column(Modifier.fillMaxWidth().height(280.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(candidate.name, Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Bold,
                    color = if (candidate.excluded) Muted else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (candidate.excluded) StatusTag("통제로 제외", Closure, Color(0xFFF7E9F1))
                else if (recommended) StatusTag("안전 추천", Purple, Color(0xFFE9E0F9))
                else if (selected) StatusTag("선택됨", Purple, Color(0xFFE9E0F9))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(durationText(candidate.durationSeconds), fontSize = 42.sp, fontWeight = FontWeight.Bold,
                    color = if (candidate.excluded) Muted else Ink)
                Text(distanceText(candidate.distanceMeters), Modifier.padding(bottom = 5.dp), fontSize = 25.sp, color = Muted)
                Text(if (candidate.tollWon == 0) "무료" else "${candidate.tollWon}원",
                    Modifier.padding(bottom = 5.dp), fontSize = 21.sp, color = Muted)
            }
            Text(candidate.riskSummary, Modifier.weight(1f), fontSize = 24.sp, lineHeight = 32.sp,
                color = if (candidate.excluded) Closure else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = Color(0xFFDDD9E4))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("위험도 ${(candidate.riskScore.coerceIn(0.0, 1.0) * 100).roundToInt()} / 100",
                    fontSize = 23.sp, color = Muted)
                Text(level, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color, background: Color) {
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun RouteLegend(label: String, color: Color, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(width = 36.dp, height = 10.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 5.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null)
        }
        Text(label, fontSize = 19.sp, color = Muted)
    }
}
