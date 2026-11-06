package com.example.pleosproj.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pleosproj.navi.GuidanceVoice
import com.example.pleosproj.scenario.ScenarioPlayer
import com.example.pleosproj.ui.NavigationTransition
import com.example.pleosproj.ui.briefing.BriefingScreen
import com.example.pleosproj.ui.compare.RouteCompareScreen
import com.example.pleosproj.ui.demo.DemoSpeech
import com.example.pleosproj.ui.demo.DemoVoiceController
import com.example.pleosproj.ui.guidance.GuidanceScreen
import com.example.pleosproj.ui.map.*
import com.example.pleosproj.ui.permissions.*
import com.example.pleosproj.ui.state.*

private val Ink = Color(0xFF232228)

@Composable
internal fun NavigationFlowHost(surface: MapSurface, lifecycle: Lifecycle, parked: Boolean?, permissions: List<AccessStatus>,
    onPermission: (AppAccess) -> Unit, onDemo: () -> Unit) {
    val context = LocalContext.current
    var loadAttempt by remember { mutableIntStateOf(0) }
    val result = remember(loadAttempt) { runCatching { NavigationExamples(ScenarioPlayer.load(context, "rain-commute")) } }
    val examples = result.getOrNull()
    if (examples == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("화면 예시를 불러오지 못했어요", fontSize = 34.sp)
            Button(onClick = { loadAttempt++ }) { Text("다시 시도", fontSize = 26.sp) }
        }
        return
    }
    val scope = rememberCoroutineScope()
    val flow = remember(examples) { NavigationFlowController(examples, scope) }
    val voice = remember(context) {
        DemoVoiceController {
            val delegate = GuidanceVoice(context)
            object : DemoSpeech {
                override var enabled: Boolean
                    get() = delegate.enabled
                    set(value) { delegate.enabled = value }
                override fun onState(state: GuidanceState) = delegate.onState(state)
                override fun speakBriefing(state: BriefingState) = delegate.speakBriefing(state)
                override fun release() = delegate.release()
            }
        }
    }
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    LaunchedEffect(parked, foreground) { flow.updateParked(if (foreground) parked else null) }
    DisposableEffect(flow, voice, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { foreground = true; voice.setForeground(true) }
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false; flow.updateParked(null); voice.setForeground(false)
            }
        }
        lifecycle.addObserver(observer)
        voice.setForeground(foreground)
        onDispose { lifecycle.removeObserver(observer); flow.dispose(); voice.release() }
    }
    LaunchedEffect(flow.page, flow.briefing) {
        when (flow.page) {
            NavigationPage.BRIEFING -> flow.briefing?.let(voice::briefing)
            NavigationPage.GUIDANCE -> {
                voice.beginGuidance(); voice.setPlaying(true)
            }
            else -> voice.release()
        }
    }
    LaunchedEffect(flow.guidance, flow.page) {
        if (flow.page == NavigationPage.GUIDANCE) flow.guidance?.let(voice::onState)
    }
    BackHandler(enabled = flow.page != NavigationPage.SEARCH || flow.query.isNotEmpty()) { flow.back() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(flow.notice) {
        flow.notice?.let { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it); flow.consumeNotice() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (flow.page != NavigationPage.SEARCH) TextButton(onClick = flow::back) { Text("뒤로", fontSize = 22.sp, color = Ink) }
                Text("SafeRoute", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                Column(Modifier.weight(1f)) {
                    Text(when (flow.page) {
                        NavigationPage.SEARCH -> "목적지 검색"
                        NavigationPage.COMPARE -> "경로 비교"
                        NavigationPage.BRIEFING -> "출발 전 브리핑"
                        NavigationPage.GUIDANCE -> "${flow.comparison?.candidates?.firstOrNull { it.id == flow.comparison?.selectedId }?.name.orEmpty()} 경로 안내"
                    }, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("예시 데이터 · 실제 검색·GPS와 연결되지 않았습니다", fontSize = 18.sp, color = Color(0xFF68666D))
                }
                if (flow.page == NavigationPage.GUIDANCE) {
                    TextButton(onClick = flow::advanceExample) { Text(if (flow.advanced) "출발 위치 예시" else "다음 안내 예시", fontSize = 21.sp, color = Ink) }
                    TextButton(onClick = { flow.endGuidance(arrived = true) }) { Text("도착 예시", fontSize = 21.sp, color = Ink) }
                } else if (flow.page == NavigationPage.BRIEFING) TextButton(onClick = voice::toggle) {
                    Text(if (voice.enabled) "음성 켜짐" else "음성 꺼짐", fontSize = 21.sp, color = Ink)
                }
                TextButton(onClick = onDemo) { Text("시연 모드", fontSize = 22.sp, color = Ink) }
            }
            NavigationTransition(flow.page, Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    NavigationPage.SEARCH -> DestinationSearchScreen(flow, permissions, onPermission)
                    NavigationPage.COMPARE -> flow.comparison?.let { comparison ->
                        Box(Modifier.fillMaxSize()) {
                            RouteCompareScreen(comparison,
                                onSelect = flow::selectRoute, onStart = flow::showBriefing,
                                onFitRoutes = surface::resetCamera,
                                startActionLabel = "출발 전 브리핑",
                                inputEnabled = flow.parked == true,
                                startNotice = if (flow.parked != true) "정차 상태를 확인하면 경로를 선택할 수 있어요" else null,
                                mapContent = { RouteCompareMap(surface, comparison, it) })
                        }
                    }
                    NavigationPage.BRIEFING -> flow.briefing?.let { briefing ->
                        BriefingScreen(briefing, flow.parked, flow::startGuidance, flow::showComparison,
                            onDrivingStarted = flow::drivingStarted,
                            voiceStatus = if (voice.enabled) "음성 안내 켜짐" else "음성 안내 꺼짐")
                    }
                    NavigationPage.GUIDANCE -> flow.guidance?.let { guidance ->
                        GuidanceScreen(guidance, voiceMuted = !voice.enabled,
                            onAcknowledge = flow::acknowledge, onDetour = flow::showComparison,
                            onOtherRoutes = flow::showComparison, onToggleVoice = voice::toggle,
                            onEnd = flow::requestEnd, onRecenter = surface::resetCamera,
                            onToggleViewpoint = { surface.showDriverView(!surface.isDriverView) },
                            driverView = surface.isDriverView,
                            mapContent = { GuidanceMap(surface, guidance, it) })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
    }
    if (flow.confirmEnd) AlertDialog(onDismissRequest = flow::dismissEnd,
        title = { Text("안내를 종료할까요?", fontSize = 30.sp) },
        text = { Text("${flow.comparison?.destinationName}까지의 예시 안내를 종료합니다.", fontSize = 24.sp) },
        confirmButton = { TextButton(onClick = { flow.endGuidance() }) { Text("안내 종료", fontSize = 25.sp) } },
        dismissButton = { TextButton(onClick = flow::dismissEnd) { Text("계속 안내", fontSize = 25.sp) } })
}
