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
import com.example.pleosproj.ui.NavigationTransition
import com.example.pleosproj.ui.map.MapSurface
import com.example.pleosproj.ui.navigation.NavigationFlowHost
import com.example.pleosproj.ui.permissions.AccessStatus
import com.example.pleosproj.ui.permissions.AppAccess

@Composable
internal fun DemoApplication(surface: MapSurface, lifecycle: Lifecycle, permissions: List<AccessStatus>,
    onRequestPermission: (AppAccess) -> Unit, parked: Boolean?, speedKph: Double?) {
    var demo by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = demo) { demo = false }
    NavigationTransition(demo, Modifier.fillMaxSize()) { displayed ->
        if (!displayed) NavigationFlowHost(surface, lifecycle, parked, permissions, onRequestPermission, onDemo = { demo = true }, speedKph = speedKph)
        else {
            val context = LocalContext.current
            val result = remember { runCatching { DemoSession(context) } }
            val session = result.getOrNull()
            if (session == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("시연을 불러오지 못했어요", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { demo = false }) { Text("첫 화면", fontSize = 25.sp) }
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
                DemoHost(surface, session, onExit = { session.dispose(); demo = false })
            }
        }
    }
}
