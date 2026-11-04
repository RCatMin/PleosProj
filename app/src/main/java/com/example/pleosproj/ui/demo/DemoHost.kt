package com.example.pleosproj.ui.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.NavigationTransition
import com.example.pleosproj.ui.briefing.BriefingScreen
import com.example.pleosproj.ui.compare.RouteCompareScreen
import com.example.pleosproj.ui.guidance.GuidanceScreen
import com.example.pleosproj.ui.map.GuidanceMap
import com.example.pleosproj.ui.map.MapSurface
import com.example.pleosproj.ui.map.RouteCompareMap
import kotlinx.coroutines.launch

private enum class DemoPage { BRIEFING, COMPARE, GUIDANCE, ENDED }
private val Ink = Color(0xFF232228)

@Composable
internal fun DemoHost(surface: MapSurface, session: DemoSession, onExit: () -> Unit) {
    val playback = session.playback
    var page by remember { mutableStateOf(DemoPage.BRIEFING) }
    var selectedId by remember { mutableStateOf(session.comparison.selectedId) }
    var acknowledgedId by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val state = playback.guidance.let { if (it.alert?.hazardId == acknowledgedId) it.copy(alert = null) else it }
    fun compare() { playback.stop(); page = DemoPage.COMPARE }
    fun start() { playback.reset(); acknowledgedId = null; page = DemoPage.GUIDANCE; playback.play() }
    fun message(text: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) } }
    BackHandler {
        when (page) {
            DemoPage.BRIEFING -> onExit()
            DemoPage.COMPARE -> { playback.stop(); page = DemoPage.BRIEFING }
            DemoPage.GUIDANCE -> { playback.stop(); confirmEnd = true }
            DemoPage.ENDED -> onExit()
        }
    }
    LaunchedEffect(playback.finished) {
        if (playback.finished && page == DemoPage.GUIDANCE) page = DemoPage.ENDED
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Text("SafeRoute", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                Column(Modifier.weight(1f)) {
                    Text("시연 · ${session.title}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("위험·날씨는 시나리오 데이터입니다", fontSize = 18.sp, color = Color(0xFF68666D))
                }
                if (page == DemoPage.GUIDANCE) PlaybackControls(playback,
                    onReset = { acknowledgedId = null; playback.reset() })
                TextButton(onClick = { playback.stop(); onExit() }) { Text("첫 화면", fontSize = 21.sp, color = Ink) }
            }
            NavigationTransition(page, Modifier.weight(1f).fillMaxWidth()) { displayed ->
                when (displayed) {
                    DemoPage.BRIEFING -> BriefingScreen(session.briefing, isParked = true,
                        onStart = { start() }, onCompare = { compare() }, onDrivingStarted = { start() })
                    DemoPage.COMPARE -> RouteCompareScreen(session.comparison.copy(selectedId = selectedId),
                        onSelect = { id -> if (session.comparison.candidates.any { it.id == id && !it.excluded }) selectedId = id },
                        onStart = { start() }, onFitRoutes = surface::resetCamera,
                        startNotice = "시연 안내는 ${session.driveRouteName} 경로로 처음부터 진행합니다",
                        mapContent = { RouteCompareMap(surface, session.comparison.copy(selectedId = selectedId), it) })
                    DemoPage.GUIDANCE -> GuidanceScreen(state, voiceMuted = muted,
                        onAcknowledge = { acknowledgedId = it },
                        onDetour = { message("시연은 정해진 ${session.driveRouteName} 경로로 진행합니다") },
                        onOtherRoutes = { compare() }, onToggleVoice = { muted = !muted; message("음성 출력은 아직 연결되지 않았습니다") },
                        onEnd = { playback.stop(); confirmEnd = true },
                        onToggleViewpoint = { surface.showDriverView(!surface.isDriverView) },
                        onRecenter = surface::resetCamera, driverView = surface.isDriverView,
                        mapContent = { GuidanceMap(surface, state, it) })
                    DemoPage.ENDED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text(if (playback.finished) "${session.briefing.destinationName} 도착 · 시연 완료" else "시연 안내를 종료했어요",
                                fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            Text("도심 폭우 퇴근길 시연입니다", fontSize = 25.sp, color = Color(0xFF68666D))
                            Button(onClick = { start() }, colors = ButtonDefaults.buttonColors(containerColor = Ink),
                                contentPadding = PaddingValues(28.dp)) { Text("처음부터 다시 재생", fontSize = 26.sp) }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false },
        title = { Text("시연 안내를 종료할까요?", fontSize = 29.sp) },
        text = { Text("시연은 일시정지된 상태입니다.", fontSize = 24.sp, lineHeight = 34.sp) },
        confirmButton = { TextButton(onClick = { confirmEnd = false; playback.stop(); page = DemoPage.ENDED }) { Text("안내 종료", fontSize = 24.sp) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("계속 보기", fontSize = 24.sp) } })
}

@Composable
private fun PlaybackControls(playback: PlaybackController, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${clock(playback.seconds)} / ${clock(playback.durationSeconds.toDouble())}", fontSize = 20.sp, color = Ink)
        FilledTonalButton(onClick = { if (playback.playing) playback.stop() else playback.play() },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Ink, contentColor = Color.White)) {
            Text(if (playback.playing) "일시정지" else "재생", fontSize = 21.sp)
        }
        for (speed in listOf(1.0, 2.0, 4.0)) FilterChip(selected = playback.speed == speed,
            onClick = { playback.changeSpeed(speed) }, label = { Text("${speed.toInt()}배", fontSize = 20.sp) })
        TextButton(onClick = onReset) { Text("처음으로", fontSize = 21.sp, color = Ink) }
    }
}

private fun clock(seconds: Double): String {
    val value = seconds.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(value / 60, value % 60)
}
