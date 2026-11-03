package com.example.pleosproj.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MapPreviewScreen(surface: MapSurface) {
    var showRoutes by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(showRoutes) { surface.setRoutesVisible(showRoutes) }
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F8))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SafeRoute", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("서울 · 강남구   /   지도 미리보기", fontSize = 17.sp, color = Color(0xFF63606A))
            }
            Surface(color = Color(0xFFECE7F7), shape = RoundedCornerShape(12.dp)) {
                Text("예시 데이터", Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color(0xFF6740B7), fontSize = 17.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { surface.view }, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopEnd).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showRoutes = !showRoutes },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202024)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)) {
                    Text(if (showRoutes) "경로선 숨기기" else "경로선 보기", fontSize = 18.sp)
                }
                FilledTonalButton(onClick = { surface.showDriverView(!surface.isDriverView) },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)) {
                    Text(if (surface.isDriverView) "위에서 보기" else "운전자 시점", fontSize = 18.sp)
                }
                FilledTonalButton(onClick = surface::resetCamera,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)) {
                    Text(if (surface.isDriverView) "내 위치로" else "경로 전체로", fontSize = 18.sp)
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp),
                color = Color.White.copy(alpha = 0.94f), shape = RoundedCornerShape(4.dp)) {
                Text(buildAnnotatedString {
                    withLink(LinkAnnotation.Url("https://openfreemap.org/")) { append("© OpenFreeMap") }
                    append(" · ")
                    withLink(LinkAnnotation.Url("https://openmaptiles.org/")) { append("© OpenMapTiles") }
                    append(" · ")
                    withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright")) {
                        append("© OpenStreetMap")
                    }
                }, Modifier.padding(8.dp), fontSize = 12.sp)
            }
            surface.loadMessage?.let { message ->
                Surface(Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    color = Color.White, shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, fontSize = 18.sp)
                        if (message.contains("못했어요")) {
                            TextButton(onClick = surface::reload) { Text("다시 시도", fontSize = 18.sp) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("선택 경로", Color(0xFF7145D6))
            Legend("대안 경로", Color(0xFFB6A5D8))
            Legend("1 침수", Color(0xFFA65321))
            Legend("2 통제", Color(0xFF9C2864))
            Legend("3 결빙", Color(0xFF16776F))
        }
        Text("고정 좌표로 표시한 예시입니다. 실제 주행 안내와 위험 정보가 아닙니다.",
            Modifier.padding(start = 28.dp, end = 28.dp, bottom = 18.dp),
            fontSize = 16.sp, color = Color(0xFF63606A))
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(6.dp)))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
