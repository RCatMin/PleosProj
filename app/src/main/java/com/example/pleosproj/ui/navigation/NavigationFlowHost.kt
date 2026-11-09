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
import com.example.pleosproj.ui.NavigationTransition
import com.example.pleosproj.ui.briefing.BriefingScreen
import com.example.pleosproj.ui.compare.RouteCompareScreen
import com.example.pleosproj.ui.demo.DemoSpeech
import com.example.pleosproj.ui.demo.DemoVoiceController
import com.example.pleosproj.ui.guidance.GuidanceScreen
import com.example.pleosproj.ui.map.*
import com.example.pleosproj.ui.permissions.*
import com.example.pleosproj.ui.state.*

private val Ink @Composable get() = MaterialTheme.colorScheme.onSurface

@Composable
internal fun NavigationFlowHost(surface: MapSurface, lifecycle: Lifecycle, parked: Boolean?, permissions: List<AccessStatus>,
    onPermission: (AppAccess) -> Unit, onDemo: () -> Unit, speedKph: Double?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { NavigationFlowController(RealNavigationSource(context), scope) }
    // 브리핑만 화면에서 말한다. 주행 중 음성은 GuidanceSession 한 곳에서 낸다.
    val briefingVoice = remember {
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
    var confirmDemo by remember { mutableStateOf(false) }
    val access = permissions.filter { it.access != AppAccess.MICROPHONE }.map { it.access to (it.granted to it.approximateLocation) }
    var previousAccess by remember { mutableStateOf(access) }
    LaunchedEffect(access) {
        if (access != previousAccess) { previousAccess = access; briefingVoice.release(); flow.permissionsChanged() }
    }
    LaunchedEffect(parked, foreground) { flow.updateParked(if (foreground) parked else null) }
    DisposableEffect(flow, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { foreground = true; flow.updateForeground(true); briefingVoice.setForeground(true) }
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false; flow.updateParked(null); flow.updateForeground(false); briefingVoice.setForeground(false)
            }
        }
        lifecycle.addObserver(observer)
        flow.updateForeground(foreground)
        briefingVoice.setForeground(foreground)
        onDispose { lifecycle.removeObserver(observer); briefingVoice.release(); flow.dispose() }
    }
    LaunchedEffect(flow.page, flow.briefing) {
        briefingVoice.release()
        if (flow.page == NavigationPage.BRIEFING) {
            if (briefingVoice.enabled != flow.voiceEnabled) briefingVoice.toggle()
            flow.briefing?.let(briefingVoice::briefing)
        }
    }
    LaunchedEffect(flow.voiceEnabled) { if (briefingVoice.enabled != flow.voiceEnabled) briefingVoice.toggle() }
    fun startGuidance() { briefingVoice.release(); flow.startGuidance() }
    fun openDemo() { briefingVoice.release(); flow.dispose(); onDemo() }
    BackHandler(enabled = flow.page != NavigationPage.SEARCH || flow.query.isNotEmpty()) { flow.back() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(flow.notice) {
        flow.notice?.let { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it); flow.consumeNotice() }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            if (flow.page != NavigationPage.COMPARE && flow.page != NavigationPage.GUIDANCE) Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (flow.page != NavigationPage.SEARCH) TextButton(onClick = flow::back) { Text("뒤로", fontSize = 22.sp, color = Ink) }
                Text("SafeRoute", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                Column(Modifier.weight(1f)) {
                    Text(when (flow.page) {
                        NavigationPage.SEARCH -> "목적지 검색"
                        NavigationPage.PLANNING -> "경로 탐색"
                        NavigationPage.COMPARE -> "경로 비교"
                        NavigationPage.BRIEFING -> "출발 전 브리핑"
                        NavigationPage.GUIDANCE -> "${flow.routeName} 경로 안내"
                    }, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    val status = navigationStatus(flow)
                    status?.let { Text(it, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (flow.page == NavigationPage.BRIEFING) TextButton(onClick = flow::toggleVoice) {
                    Text(if (flow.voiceEnabled) "음성 켜짐" else "음성 꺼짐", fontSize = 21.sp, color = Ink)
                }
                TextButton(onClick = { if (flow.guiding) confirmDemo = true else openDemo() }) { Text("시연 모드", fontSize = 22.sp, color = Ink) }
            }
            flow.sessionFailure?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), fontSize = 22.sp, color = Color(0xFF9C2864))
                    TextButton(onClick = flow::retryConnection) { Text("다시 연결", fontSize = 23.sp) }
                }
            }
            NavigationTransition(flow.page, Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    NavigationPage.SEARCH -> DestinationSearchScreen(flow, permissions, onPermission)
                    NavigationPage.PLANNING -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (flow.planFailure == null) CircularProgressIndicator()
                        Spacer(Modifier.height(24.dp))
                        Text(flow.planFailure ?: "안전한 경로를 찾고 있어요", fontSize = 32.sp, color = Ink)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            OutlinedButton(onClick = flow::back) { Text("돌아가기", fontSize = 25.sp) }
                            if (flow.planFailure != null) Button(onClick = flow::retryPlan, enabled = flow.parked == true,
                                colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("다시 시도", fontSize = 25.sp) }
                        }
                    }
                    NavigationPage.COMPARE -> flow.comparison?.let { comparison ->
                        RouteCompareScreen(comparison, onSelect = flow::selectRoute, onStart = { id -> if (flow.comparingCurrent) flow.startComparedRoute(id) else flow.showBriefing(id) },
                            onFitRoutes = surface::fitRoute, startActionLabel = "안내 시작",
                            weatherByRoute = flow.plan?.briefings?.mapValues { it.value.weather }.orEmpty(),
                            departureTime = flow.plan?.departureTime,
                            currentRemainingSeconds = flow.comparisonRemainingSeconds,
                            onContinue = if (flow.comparingCurrent) flow::continueGuidance else null,
                            mapControls = { MapControls(surface, !flow.voiceEnabled, flow::toggleVoice) },
                            onBack = flow::back,
                            toolbar = { MapTripToolbar(flow, onDemo = { if (flow.guiding) confirmDemo = true else openDemo() }) },
                            inputEnabled = flow.parked == true,
                            startNotice = when {
                                flow.parked != true -> "정차 상태를 확인하면 경로를 선택할 수 있어요"
                                flow.plan?.weatherMissing == true -> "날씨 정보를 받지 못해 날씨 위험은 반영되지 않았습니다"
                                else -> navigationStatus(flow)
                            }, mapContent = { RouteCompareMap(surface, comparison, it) })
                    }
                    NavigationPage.BRIEFING -> flow.briefing?.let { briefing ->
                        BriefingScreen(briefing, flow.parked, ::startGuidance, flow::showComparison,
                            onDrivingStarted = { briefingVoice.release(); flow.drivingStarted() },
                            voiceStatus = if (flow.voiceEnabled) "음성 안내 켜짐" else "음성 안내 꺼짐")
                        if (flow.starting) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    NavigationPage.GUIDANCE -> flow.guidance?.let { guidance ->
                        GuidanceScreen(guidance, voiceMuted = !flow.voiceEnabled,
                            onAcknowledge = flow::acknowledge, onDetour = flow::acceptDetour,
                            onOtherRoutes = flow::showComparison, onToggleVoice = flow::toggleVoice,
                            onEnd = flow::requestEnd, onRecenter = surface::resetCamera,
                            onToggleViewpoint = { surface.showDriverView(!surface.isDriverView) },
                            driverView = surface.isDriverView, speedKph = speedKph, tollWon = flow.activeTollWon,
                            mapControls = { MapControls(surface, !flow.voiceEnabled, flow::toggleVoice) },
                            toolbar = { MapTripToolbar(flow, onDemo = { confirmDemo = true }) }, mapContent = { GuidanceMap(surface, guidance, it) })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
    }
    if (flow.confirmEnd) AlertDialog(onDismissRequest = flow::dismissEnd,
        title = { Text("안내를 종료할까요?", fontSize = 30.sp) },
        text = { Text("${flow.guidance?.destinationName ?: flow.comparison?.destinationName}까지의 안내를 종료합니다.", fontSize = 24.sp) },
        confirmButton = { TextButton(onClick = flow::endGuidance) { Text("안내 종료", fontSize = 25.sp) } },
        dismissButton = { TextButton(onClick = flow::dismissEnd) { Text("계속 안내", fontSize = 25.sp) } })
    if (confirmDemo) AlertDialog(onDismissRequest = { confirmDemo = false },
        title = { Text("시연 모드로 전환할까요?", fontSize = 30.sp) },
        text = { Text("현재 주행 안내를 종료하고 시연을 시작합니다.", fontSize = 24.sp) },
        confirmButton = { TextButton(onClick = { confirmDemo = false; openDemo() }) { Text("시연 시작", fontSize = 25.sp) } },
        dismissButton = { TextButton(onClick = { confirmDemo = false }) { Text("계속 안내", fontSize = 25.sp) } })
}


@Composable
private fun MapTripToolbar(flow: NavigationFlowController, onDemo: () -> Unit) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.widthIn(max = 280.dp)) {
                if (flow.guiding) {
                    Text(flow.guidance?.destinationName.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(flow.routeName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                navigationStatus(flow)?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            TextButton(onClick = onDemo) { Text("시연 모드", fontSize = 14.sp) }
        }
    }
}


private fun navigationStatus(flow: NavigationFlowController): String? = when {
    flow.detouring -> "위험 구간을 피하는 경로를 찾고 있어요"
    flow.plan?.fallbackReason != null -> "대체 경로 · 교통 미반영 (${flow.plan?.fallbackReason})"
    flow.plan?.weatherMissing == true -> "날씨 정보를 받지 못해 날씨 위험은 반영되지 않았습니다"
    else -> null
}
