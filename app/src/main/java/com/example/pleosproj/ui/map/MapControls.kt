package com.example.pleosproj.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MapControls(surface: MapSurface, voiceMuted: Boolean, onToggleVoice: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp) {
            Column {
                MapControl("지도 확대", "plus", joined = true) { surface.zoomBy(1.0) }
                HorizontalDivider(Modifier.width(46.dp).padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MapControl("지도 축소", "minus", joined = true) { surface.zoomBy(-1.0) }
            }
        }
        Box {
            MapControl("지도 보기 방식 · ${surface.viewpoint.label}", "compass") { menu = "view" }
            Surface(Modifier.align(Alignment.BottomCenter).offset(y = 2.dp), color = com.example.pleosproj.ui.theme.RoutePurple,
                contentColor = androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(4.dp)) {
                Text(when (surface.viewpoint) { MapViewpoint.OVERHEAD -> "3D"; MapViewpoint.NORTH_UP -> "N"; MapViewpoint.DRIVER -> "앞" },
                    Modifier.padding(horizontal = 3.dp), fontSize = 9.sp)
            }
            DropdownMenu(expanded = menu == "view", onDismissRequest = { menu = null }) {
                MapViewpoint.entries.forEach { value ->
                    DropdownMenuItem(text = { Text((if (surface.viewpoint == value) "✓  " else "    ") + value.label, fontSize = 17.sp) },
                        onClick = { surface.showViewpoint(value); menu = null })
                }
                DropdownMenuItem(text = { Text("전체 경로 보기", fontSize = 17.sp) }, onClick = { surface.fitRoute(); menu = null })
            }
        }
        MapControl("내 위치로", "location", onClick = surface::resetCamera)
        Box {
            MapControl("지도 종류 · ${surface.appearance.label}", "layers") { menu = "style" }
            DropdownMenu(expanded = menu == "style", onDismissRequest = { menu = null }) {
                MapAppearance.entries.forEach { value ->
                    DropdownMenuItem(text = { Text((if (surface.appearance == value) "✓  " else "    ") + value.label, fontSize = 17.sp) },
                        onClick = { surface.showAppearance(value); menu = null })
                }
            }
        }
        MapControl(if (voiceMuted) "음성 안내 켜기" else "음성 안내 끄기", if (voiceMuted) "muted" else "voice", onClick = onToggleVoice)
        MapControl(if (surface.night) "낮 화면으로" else "밤 화면으로", if (surface.night) "day" else "night", onClick = surface::toggleNight)
    }
}

@Composable
private fun MapControl(label: String, glyph: String, joined: Boolean = false, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Surface(shape = RoundedCornerShape(if (joined) 0.dp else 16.dp), shadowElevation = if (joined) 0.dp else 2.dp) {
        IconButton(onClick = onClick, modifier = Modifier.size(46.dp).semantics { contentDescription = label }) {
            Canvas(Modifier.size(22.dp)) {
                val w = size.width; val h = size.height; val c = center
                val stroke = 2.2.dp.toPx()
                fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(ink, Offset(w*x,h*y), Offset(w*xx,h*yy), stroke)
                when (glyph) {
                    "plus", "minus" -> { line(.15f,.5f,.85f,.5f); if (glyph == "plus") line(.5f,.15f,.5f,.85f) }
                    "location", "day" -> {
                        drawCircle(ink,w*.29f,c,style=Stroke(stroke)); drawCircle(ink,w*.09f,c,style=Stroke(stroke))
                        line(.5f,0f,.5f,.16f);line(.5f,.84f,.5f,1f);line(0f,.5f,.16f,.5f);line(.84f,.5f,1f,.5f)
                    }
                    "compass" -> {
                        drawCircle(ink,w*.44f,c,style=Stroke(stroke))
                        drawPath(Path().apply { moveTo(w*.5f,h*.15f);lineTo(w*.67f,h*.55f);lineTo(w*.5f,h*.85f);lineTo(w*.33f,h*.45f);close() },ink,style=Stroke(stroke))
                    }
                    "layers" -> {
                        drawPath(Path().apply { moveTo(w*.08f,h*.38f);lineTo(w*.5f,h*.1f);lineTo(w*.92f,h*.38f);lineTo(w*.5f,h*.66f);close() },ink,style=Stroke(stroke))
                        line(.08f,.6f,.5f,.9f);line(.5f,.9f,.92f,.6f)
                    }
                    "night" -> drawPath(Path().apply { moveTo(w*.62f,h*.12f);cubicTo(w*.08f,h*.12f,w*.12f,h*.91f,w*.66f,h*.86f);cubicTo(w*.35f,h*.69f,w*.33f,h*.35f,w*.62f,h*.12f);close() },ink,style=Stroke(stroke))
                    else -> {
                        drawPath(Path().apply { moveTo(w*.08f,h*.35f);lineTo(w*.28f,h*.35f);lineTo(w*.54f,h*.12f);lineTo(w*.54f,h*.88f);lineTo(w*.28f,h*.65f);lineTo(w*.08f,h*.65f);close() },ink,style=Stroke(stroke))
                        if (glyph == "muted") { line(.68f,.35f,.94f,.65f);line(.68f,.65f,.94f,.35f) }
                        else drawArc(ink,-60f,120f,false,Offset(w*.4f,h*.16f),androidx.compose.ui.geometry.Size(w*.58f,h*.68f),style=Stroke(stroke))
                    }
                }
            }
        }
    }
}
