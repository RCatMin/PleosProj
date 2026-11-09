package com.example.pleosproj.ui.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pleosproj.scenario.ScenarioPlayer
import com.example.pleosproj.ui.NavigationTransition
import com.example.pleosproj.ui.map.MapSurface
import com.example.pleosproj.ui.navigation.NavigationFlowHost
import com.example.pleosproj.ui.permissions.AccessStatus
import com.example.pleosproj.ui.permissions.AppAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DemoApplication(surface: MapSurface, lifecycle: Lifecycle, permissions: List<AccessStatus>,
    onRequestPermission: (AppAccess) -> Unit, parked: Boolean?, speedKph: Double?) {
    var demo by rememberSaveable { mutableStateOf(false) }
    var scenarioId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = demo) { if (scenarioId != null) scenarioId = null else demo = false }
    NavigationTransition(demo, Modifier.fillMaxSize()) { displayed ->
        if (!displayed) NavigationFlowHost(surface, lifecycle, parked, permissions, onRequestPermission,
            onDemo = { scenarioId = null; demo = true }, speedKph = speedKph)
        else NavigationTransition(scenarioId, Modifier.fillMaxSize()) { selected ->
            if (selected == null) ScenarioCatalog(onSelect = { scenarioId = it }, onExit = { demo = false })
            else key(selected) {
                SelectedDemo(surface, lifecycle, selected, onChooseScenario = { scenarioId = null }, onExit = { demo = false })
            }
        }
    }
}

@Composable
private fun ScenarioCatalog(onSelect: (String) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val catalog by produceState<ScenarioCatalogState>(ScenarioCatalogState.Loading, context, attempt) {
        value = ScenarioCatalogState.Loading
        value = try {
            val choices = withContext(Dispatchers.IO) {
                ScenarioPlayer.list(context).map { ScenarioChoice(it.id, it.title, it.summary) }
            }
            ScenarioCatalogState.Ready(choices)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ScenarioCatalogState.Failed
        }
    }
    ScenarioPickerScreen(catalog, onSelect, onRetry = { attempt++ }, onExit)
}

@Composable
private fun SelectedDemo(surface: MapSurface, lifecycle: Lifecycle, scenarioId: String,
    onChooseScenario: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val result = remember(context, scenarioId) { runCatching { DemoSession(context, scenarioId) } }
    val session = result.getOrNull()
    if (session == null) {
        BackHandler(onBack = onChooseScenario)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("시연을 불러오지 못했어요", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Button(onClick = onChooseScenario, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF23252B),
                    contentColor = androidx.compose.ui.graphics.Color.White)) { Text("시나리오 다시 선택", fontSize = 25.sp) }
                TextButton(onClick = onExit) { Text("첫 화면", fontSize = 25.sp) }
            }
        }
    } else {
        DisposableEffect(session, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) session.voice.setForeground(true)
                if (event == Lifecycle.Event.ON_STOP) { session.pause(); session.voice.setForeground(false) }
            }
            lifecycle.addObserver(observer)
            session.voice.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            onDispose { lifecycle.removeObserver(observer); session.dispose() }
        }
        DemoHost(surface, session, onChooseScenario = onChooseScenario, onExit = onExit)
    }
}
