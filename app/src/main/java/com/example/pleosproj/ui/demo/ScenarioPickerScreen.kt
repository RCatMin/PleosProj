package com.example.pleosproj.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 목록 API에서 받은 표시용 정보만 보관한다. 시나리오 내용이나 재생 상태는 복제하지 않는다.
internal data class ScenarioChoice(val id: String, val title: String, val summary: String)
internal sealed interface ScenarioCatalogState {
    data object Loading : ScenarioCatalogState
    data object Failed : ScenarioCatalogState
    data class Ready(val choices: List<ScenarioChoice>) : ScenarioCatalogState
}

@Composable
internal fun ScenarioPickerScreen(state: ScenarioCatalogState, onSelect: (String) -> Unit, onRetry: () -> Unit, onExit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).padding(36.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("시연 모드", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
            TextButton(onClick = onExit) { Text("첫 화면", fontSize = 23.sp, color = colors.onSurface) }
        }
        Text("시연할 주행을 선택하세요", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text("미리 준비한 경로와 날씨로 주행 상황을 살펴볼 수 있어요", fontSize = 24.sp, color = colors.onSurfaceVariant)
        when (state) {
            ScenarioCatalogState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    CircularProgressIndicator()
                    Text("시연 목록을 불러오고 있어요", fontSize = 28.sp)
                }
            }
            ScenarioCatalogState.Failed -> ScenarioListMessage("시연 목록을 불러오지 못했어요", onRetry, Modifier.weight(1f))
            is ScenarioCatalogState.Ready -> if (state.choices.isEmpty()) {
                ScenarioListMessage("아직 준비된 시연이 없어요", onRetry, Modifier.weight(1f))
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(state.choices, key = { _, choice -> choice.id }) { index, choice ->
                    Surface(onClick = { onSelect(choice.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                        color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceVariant) {
                                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                    Text("%02d".format(index + 1), fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(choice.title, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                if (choice.summary.isNotBlank()) Text(choice.summary, fontSize = 23.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
                            }
                            Text("시연 보기 →", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioListMessage(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(message, fontSize = 30.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF23252B),
            contentColor = androidx.compose.ui.graphics.Color.White)) { Text("다시 불러오기", fontSize = 24.sp) }
    }
}
