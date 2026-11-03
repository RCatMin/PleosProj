package com.example.pleosproj.ui.guidance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.map.GuidanceMap
import com.example.pleosproj.ui.map.MapSurface
import com.example.pleosproj.ui.map.RouteCompareMap
import com.example.pleosproj.ui.compare.RouteCompareScreen
import com.example.pleosproj.ui.state.PreviewStates
import kotlinx.coroutines.launch

// 실제 데이터 연결 전 안내·경로 비교 예시를 확인하는 진입 화면이다. 공유 상태 모델 자체는 수정하지 않는다.
@Composable
fun GuidancePreviewHost(surface: MapSurface) {
    var comparingRoutes by rememberSaveable { mutableStateOf(true) }
    var selectedRouteId by rememberSaveable { mutableStateOf(PreviewStates.routeCompare.selectedId) }
    var startRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    val comparison = PreviewStates.routeCompare.copy(selectedId = selectedRouteId)
    var livePreview by rememberSaveable { mutableStateOf(false) }
    var acknowledgedId by rememberSaveable(livePreview) { mutableStateOf<String?>(null) }
    var voiceMuted by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var ended by rememberSaveable { mutableStateOf(false) }
    val base = if (livePreview) PreviewStates.guidanceLive else PreviewStates.guidanceScenario
    val state = if (base.alert?.hazardId == acknowledgedId) base.copy(alert = null) else base
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun previewEvent(message: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) }
    }
    BackHandler(enabled = comparingRoutes) { comparingRoutes = false }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("SafeRoute", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(if (comparingRoutes) "경로 비교 · 예시" else if (livePreview) "경로선 없는 안내 · 예시" else "시연 안내 · 예시",
                        fontSize = 20.sp, color = Color(0xFF68666D))
                    Text("실제 내비·날씨와 연결되지 않은 예시입니다", fontSize = 18.sp, color = Color(0xFF68666D))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { comparingRoutes = !comparingRoutes },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(if (comparingRoutes) "안내 화면" else "경로 비교", fontSize = 20.sp, color = Color(0xFF232228))
                    }
                    if (!comparingRoutes) TextButton(onClick = { livePreview = !livePreview; ended = false },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(if (livePreview) "경로선 있는 예시" else "경로선 없는 예시", fontSize = 20.sp, color = Color(0xFF232228))
                    }
                }
            }
            if (comparingRoutes) RouteCompareScreen(
                state = comparison,
                onSelect = { id -> if (comparison.candidates.any { it.id == id && !it.excluded }) selectedRouteId = id },
                onStart = { startRequestId = it },
                onFitRoutes = surface::resetCamera,
                mapContent = { RouteCompareMap(surface, comparison, it) },
                modifier = Modifier.weight(1f),
            ) else if (ended) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("안내를 종료했어요", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("${state.destinationName}까지의 예시 안내가 끝났습니다", fontSize = 24.sp)
                        Button(onClick = { ended = false; acknowledgedId = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232228)),
                            contentPadding = PaddingValues(24.dp)) { Text("예시 다시 보기", fontSize = 24.sp) }
                    }
                }
            } else GuidanceScreen(
                state = state,
                voiceMuted = voiceMuted,
                onAcknowledge = { acknowledgedId = it },
                onDetour = { acknowledgedId = state.alert?.hazardId; previewEvent("우회 요청 · 예시 동작") },
                onOtherRoutes = { comparingRoutes = true },
                onToggleVoice = { voiceMuted = !voiceMuted },
                onEnd = { confirmEnd = true },
                onToggleViewpoint = { surface.showDriverView(!surface.isDriverView) },
                onRecenter = surface::resetCamera,
                driverView = surface.isDriverView,
                mapContent = { GuidanceMap(surface, state, it) },
                modifier = Modifier.weight(1f),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }
    startRequestId?.let { id ->
        val candidate = comparison.candidates.first { it.id == id }
        AlertDialog(
            onDismissRequest = { startRequestId = null },
            title = { Text("안내 시작 요청 · 예시", fontSize = 30.sp) },
            text = { Text("${candidate.name} 경로를 선택했습니다.\n실제 내비로 경로를 넘기는 기능은 아직 연결되지 않았습니다.",
                fontSize = 24.sp, lineHeight = 34.sp) },
            confirmButton = { TextButton(onClick = { startRequestId = null }) { Text("확인", fontSize = 24.sp) } },
        )
    }
    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text("안내를 종료할까요?", fontSize = 30.sp) },
        text = { Text("${state.destinationName}까지의 예시 안내를 종료합니다.", fontSize = 24.sp) },
        confirmButton = { TextButton(onClick = { confirmEnd = false; ended = true }) { Text("안내 종료", fontSize = 24.sp) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("계속 안내", fontSize = 24.sp) } },
    )
}
