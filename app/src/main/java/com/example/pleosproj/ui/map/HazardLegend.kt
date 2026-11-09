package com.example.pleosproj.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.hazardName
import com.example.pleosproj.ui.state.HazardKind

@Composable
internal fun HazardLegend(kinds: List<HazardKind>, modifier: Modifier = Modifier) {
    if (kinds.isEmpty()) return
    Surface(modifier, shape = RoundedCornerShape(14.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("위험 구간", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            kinds.distinct().sortedBy { it.ordinal }.groupBy(::hazardLineColor).forEach { (color, group) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(width = 25.dp, height = 12.dp)) {
                        drawLine(Color(color), Offset(0f, center.y), Offset(size.width, center.y), 5.dp.toPx(), StrokeCap.Round)
                    }
                    Text(group.joinToString(" · ") { hazardName(it) }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
