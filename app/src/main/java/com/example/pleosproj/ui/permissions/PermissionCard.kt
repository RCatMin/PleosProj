package com.example.pleosproj.ui.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PermissionCard(statuses: List<AccessStatus>, onRequest: (AppAccess) -> Unit) {
    val ink = Color(0xFF232228)
    val muted = Color(0xFF68666D)
    if (statuses.all { it.granted }) {
        Text("차량 속도·위치·마이크 권한 허용됨 · 시연은 재생 시간에 맞춰 진행됩니다",
            fontSize = 21.sp, color = muted)
        return
    }
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("앱 권한을 확인해 주세요", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = ink)
            Text("권한을 허용하지 않아도 시간에 맞춰 재생하는 시연은 이용할 수 있습니다",
                fontSize = 21.sp, lineHeight = 30.sp, color = muted)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                statuses.forEach { status ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(status.access.title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = ink)
                        Text(status.access.purpose, fontSize = 20.sp, color = muted)
                        Text(status.description, fontSize = 21.sp,
                            color = if (status.granted) androidx.compose.material3.MaterialTheme.colorScheme.primary else muted)
                        if (status.action != AccessAction.NONE) TextButton(onClick = { onRequest(status.access) }) {
                            Text(if (status.action == AccessAction.SETTINGS) "설정에서 변경" else "권한 허용",
                                fontSize = 21.sp, color = ink)
                        }
                    }
                }
            }
        }
    }
}
