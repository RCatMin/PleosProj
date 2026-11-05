package com.example.pleosproj.ui.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.pleosproj.ui.NavigationPreviewHost
import com.example.pleosproj.ui.NavigationTransition
import com.example.pleosproj.ui.map.MapSurface
import com.example.pleosproj.ui.permissions.AccessStatus
import com.example.pleosproj.ui.permissions.AppAccess
import com.example.pleosproj.ui.permissions.PermissionCard

private enum class AppPage { HOME, DEMO, PREVIEW }
private val Ink = Color(0xFF232228)

@Composable
internal fun DemoApplication(surface: MapSurface, lifecycle: Lifecycle, permissions: List<AccessStatus>, onRequestPermission: (AppAccess) -> Unit) {
    var page by remember { mutableStateOf(AppPage.HOME) }
    BackHandler(enabled = page != AppPage.HOME) { page = AppPage.HOME }
    NavigationTransition(page, Modifier.fillMaxSize()) { displayed ->
        when (displayed) {
            AppPage.HOME -> DemoHome(permissions, onRequestPermission,
                onStart = { page = AppPage.DEMO }, onPreview = { page = AppPage.PREVIEW })
            AppPage.PREVIEW -> NavigationPreviewHost(surface)
            AppPage.DEMO -> {
                val context = LocalContext.current
                val result = remember { runCatching { DemoSession(context) } }
                val session = result.getOrNull()
                if (session == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("시연을 불러오지 못했어요", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            Text("첫 화면으로 돌아가 다시 시도해 주세요", fontSize = 25.sp)
                            Button(onClick = { page = AppPage.HOME }) { Text("첫 화면", fontSize = 25.sp) }
                        }
                    }
                } else {
                    DisposableEffect(session, lifecycle) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) session.playback.stop()
                        }
                        lifecycle.addObserver(observer)
                        onDispose { lifecycle.removeObserver(observer); session.playback.dispose() }
                    }
                    DemoHost(surface, session, onExit = { session.playback.stop(); page = AppPage.HOME })
                }
            }
        }
    }
}

@Composable
private fun DemoHome(permissions: List<AccessStatus>, onPermission: (AppAccess) -> Unit, onStart: () -> Unit, onPreview: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F8)).padding(52.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("SafeRoute", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Column(Modifier.widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Text("안전한 길을, 출발 전에", fontSize = 64.sp, lineHeight = 80.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("도심 폭우 퇴근길", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7145D6))
            Text("강남역에서 삼성역까지,\n침수 통제를 피해 봉은사로로 안내하는 시연입니다.",
                fontSize = 29.sp, lineHeight = 44.sp, color = Color(0xFF68666D))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Button(onClick = onStart, shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    contentPadding = PaddingValues(horizontal = 42.dp, vertical = 26.dp)) {
                    Text("시연 모드 시작", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onPreview, shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 34.dp, vertical = 26.dp)) {
                    Text("화면 예시 보기", fontSize = 27.sp, color = Ink)
                }
            }
        }
        PermissionCard(permissions, onPermission)
    }
}
