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
import com.example.pleosproj.ui.map.MapControls
import com.example.pleosproj.ui.map.RouteCompareMap
import kotlinx.coroutines.launch

private enum class DemoPage { BRIEFING, COMPARE, GUIDANCE, ENDED }
private val Ink @Composable get() = MaterialTheme.colorScheme.onSurface

@Composable
internal fun DemoHost(surface: MapSurface, session: DemoSession, onExit: () -> Unit) {
    val playback = session.playback
    val motionFrame = remember(session) { { session.motionFrame() } }
    var page by remember { mutableStateOf(DemoPage.BRIEFING) }
    var selectedId by remember { mutableStateOf(session.comparison.selectedId) }
    var acknowledgedId by remember { mutableStateOf<String?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val state = playback.guidance.let { if (it.alert?.hazardId == acknowledgedId) it.copy(alert = null) else it }
    fun compare() { session.pause(); page = DemoPage.COMPARE }
    fun start() { session.reset(); acknowledgedId = null; page = DemoPage.GUIDANCE; session.play() }
    fun message(text: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) } }
    BackHandler {
        when (page) {
            DemoPage.BRIEFING -> onExit()
            DemoPage.COMPARE -> { session.pause(); page = DemoPage.BRIEFING }
            DemoPage.GUIDANCE -> { session.pause(); confirmEnd = true }
            DemoPage.ENDED -> onExit()
        }
    }
    LaunchedEffect(playback.finished) {
        // 도착 안내를 말하는 도중 음성을 꺼서 마지막 문장을 끊지 않는다.
        if (playback.finished && page == DemoPage.GUIDANCE) page = DemoPage.ENDED
    }
    LaunchedEffect(session, page) {
        if (page == DemoPage.BRIEFING) session.voice.briefing(session.briefing)
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            if (page != DemoPage.COMPARE && page != DemoPage.GUIDANCE) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Text("SafeRoute", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                Column(Modifier.weight(1f)) {
                    Text("시연 · ${session.title}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("위험·날씨는 시나리오 데이터입니다", fontSize = 18.sp, color = Color(0xFF68666D))
                }
                if (page == DemoPage.GUIDANCE) PlaybackControls(session,
                    onReset = { acknowledgedId = null; session.reset() })
                else TextButton(onClick = session.voice::toggle) {
                    Text(if (session.voice.enabled) "음성 켜짐" else "음성 꺼짐", fontSize = 21.sp, color = Ink)
                }
                TextButton(onClick = { session.pause(); session.voice.release(); onExit() }) { Text("첫 화면", fontSize = 21.sp, color = Ink) }
            }
            NavigationTransition(page, Modifier.weight(1f).fillMaxWidth()) { displayed ->
                when (displayed) {
                    DemoPage.BRIEFING -> BriefingScreen(session.briefing, isParked = true,
                        onStart = { start() }, onCompare = { compare() }, onDrivingStarted = { start() },
                        voiceStatus = if (session.voice.enabled) "음성 안내 켜짐" else "음성 안내 꺼짐")
                    DemoPage.COMPARE -> RouteCompareScreen(session.comparison.copy(selectedId = selectedId),
                        onSelect = { id -> if (session.comparison.candidates.any { it.id == id && !it.excluded }) selectedId = id },
                        onStart = { start() }, onFitRoutes = surface::fitRoute,
                        weatherByRoute = session.comparison.candidates.associate { it.id to session.briefing.weather },
                        mapControls = { MapControls(surface, !session.voice.enabled, session.voice::toggle) },
                        onBack = { page = DemoPage.BRIEFING },
                        toolbar = { DemoMapToolbar(session, onReset = { acknowledgedId = null; session.reset() }, onExit = onExit) },
                        startNotice = "시연 안내는 ${session.driveRouteName} 경로로 처음부터 진행합니다",
                        mapContent = { RouteCompareMap(surface, session.comparison.copy(selectedId = selectedId), it) })
                    DemoPage.GUIDANCE -> GuidanceScreen(state, voiceMuted = !session.voice.enabled,
                        onAcknowledge = { acknowledgedId = it },
                        onDetour = { message("시연은 정해진 ${session.driveRouteName} 경로로 진행합니다") },
                        onOtherRoutes = { compare() }, onToggleVoice = session.voice::toggle,
                        onEnd = { session.pause(); confirmEnd = true },
                        onToggleViewpoint = { surface.showDriverView(!surface.isDriverView) },
                        onRecenter = surface::resetCamera, driverView = surface.isDriverView,
                        tollWon = session.comparison.candidates.firstOrNull { it.name == session.driveRouteName }?.tollWon,
                        mapControls = { MapControls(surface, !session.voice.enabled, session.voice::toggle) },
                        toolbar = { DemoMapToolbar(session, onReset = { acknowledgedId = null; session.reset() }, onExit = { session.pause(); session.voice.release(); onExit() }) },
                        mapContent = { GuidanceMap(surface, state, it, motionFrame) })
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
        confirmButton = { TextButton(onClick = { confirmEnd = false; session.pause(); page = DemoPage.ENDED }) { Text("안내 종료", fontSize = 24.sp) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("계속 보기", fontSize = 24.sp) } })
}

@Composable
private fun PlaybackControls(session: DemoSession, onReset: () -> Unit) {
    val playback = session.playback
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${clock(playback.seconds)} / ${clock(playback.durationSeconds.toDouble())}", fontSize = 20.sp, color = Ink)
        FilledTonalButton(onClick = { if (playback.playing) session.pause() else session.play() },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Ink, contentColor = Color.White)) {
            Text(if (playback.playing) "일시정지" else "재생", fontSize = 21.sp)
        }
        for (speed in listOf(1.0, 2.0, 4.0)) FilterChip(selected = playback.speed == speed,
            onClick = { session.changeSpeed(speed) }, label = { Text("${speed.toInt()}배", fontSize = 20.sp) })
        TextButton(onClick = onReset) { Text("처음으로", fontSize = 21.sp, color = Ink) }
    }
}

private fun clock(seconds: Double): String {
    val value = seconds.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(value / 60, value % 60)
}


@Composable
private fun DemoMapToolbar(session: DemoSession, onReset: () -> Unit, onExit: () -> Unit) {
    val playback = session.playback
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text("시연 · 위험·날씨는 예시", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${clock(playback.seconds)} / ${clock(playback.durationSeconds.toDouble())}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { if (playback.playing) session.pause() else session.play() }) {
                Text(if (playback.playing) "일시정지" else "재생", fontSize = 14.sp)
            }
            for (speed in listOf(1.0, 2.0, 4.0)) FilterChip(selected = playback.speed == speed,
                onClick = { session.changeSpeed(speed) }, label = { Text("${speed.toInt()}배", fontSize = 12.sp) })
            TextButton(onClick = onReset) { Text("처음으로", fontSize = 12.sp) }
            TextButton(onClick = onExit) { Text("첫 화면", fontSize = 12.sp) }
        }
    }
}
