package com.example.pleosproj.ui

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
import com.example.pleosproj.ui.briefing.BriefingScreen
import com.example.pleosproj.ui.guidance.GuidanceScreen
import com.example.pleosproj.ui.state.PreviewStates
import kotlinx.coroutines.launch

private enum class PreviewScreen { BRIEFING, COMPARE, GUIDANCE }

// 실제 데이터 연결 전 브리핑·경로 비교·안내 예시를 확인한다. 차량 상태와 음성은 연결하지 않는다.
@Composable
fun NavigationPreviewHost(surface: MapSurface) {
    var screen by rememberSaveable { mutableStateOf(PreviewScreen.BRIEFING) }
    var clearBriefing by rememberSaveable { mutableStateOf(false) }
    var parkedPreview by rememberSaveable { mutableStateOf(true) }
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
    fun showBriefing() { parkedPreview = true; screen = PreviewScreen.BRIEFING }
    fun showGuidance() { ended = false; acknowledgedId = null; screen = PreviewScreen.GUIDANCE }
    BackHandler(enabled = screen != PreviewScreen.BRIEFING) { showBriefing() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("SafeRoute", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(when (screen) {
                        PreviewScreen.BRIEFING -> "출발 전 브리핑 · 예시"
                        PreviewScreen.COMPARE -> "경로 비교 · 예시"
                        PreviewScreen.GUIDANCE -> if (livePreview) "경로선 없는 안내 · 예시" else "시연 안내 · 예시"
                    },
                        fontSize = 20.sp, color = Color(0xFF68666D))
                    Text("실제 내비·날씨와 연결되지 않은 예시입니다", fontSize = 18.sp, color = Color(0xFF68666D))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (screen == PreviewScreen.BRIEFING) {
                        TextButton(onClick = { clearBriefing = !clearBriefing }) {
                            Text(if (clearBriefing) "위험 있는 예시" else "위험 없는 예시", fontSize = 20.sp, color = Color(0xFF232228))
                        }
                        TextButton(onClick = { parkedPreview = false }) {
                            Text("주행 전환 예시", fontSize = 20.sp, color = Color(0xFF232228))
                        }
                    } else TextButton(onClick = { showBriefing() }) {
                        Text("브리핑", fontSize = 20.sp, color = Color(0xFF232228))
                    }
                    if (screen == PreviewScreen.COMPARE) TextButton(onClick = { showGuidance() }) {
                        Text("안내 화면", fontSize = 20.sp, color = Color(0xFF232228))
                    }
                    if (screen == PreviewScreen.GUIDANCE) TextButton(onClick = { livePreview = !livePreview; ended = false },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(if (livePreview) "경로선 있는 예시" else "경로선 없는 예시", fontSize = 20.sp, color = Color(0xFF232228))
                    }
                }
            }
            if (screen == PreviewScreen.BRIEFING) BriefingScreen(
                state = if (clearBriefing) PreviewStates.briefingClear else PreviewStates.briefing,
                isParked = parkedPreview,
                onStart = { selectedRouteId = comparison.recommendedId ?: selectedRouteId; livePreview = false; showGuidance() },
                onCompare = { screen = PreviewScreen.COMPARE },
                onDrivingStarted = { showGuidance() },
                modifier = Modifier.weight(1f),
            ) else if (screen == PreviewScreen.COMPARE) RouteCompareScreen(
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
                onOtherRoutes = { screen = PreviewScreen.COMPARE },
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
