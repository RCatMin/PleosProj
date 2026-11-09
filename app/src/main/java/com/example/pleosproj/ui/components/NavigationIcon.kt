package com.example.pleosproj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class NavigationIconKind { CAR, OTHER_ROUTES, MICROPHONE }

@Composable
internal fun NavigationIcon(kind: NavigationIconKind, modifier: Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = 1.9.dp.toPx()
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(w*x,h*y),Offset(w*xx,h*yy),stroke, StrokeCap.Round)
        when (kind) {
            NavigationIconKind.CAR -> {
                drawPath(Path().apply { moveTo(w*.12f,h*.79f);lineTo(w*.12f,h*.43f);lineTo(w*.24f,h*.17f);lineTo(w*.76f,h*.17f);lineTo(w*.88f,h*.43f);lineTo(w*.88f,h*.79f);close() },color,style=Stroke(stroke))
                line(.15f,.43f,.85f,.43f);line(.22f,.78f,.22f,.94f);line(.78f,.78f,.78f,.94f)
                line(.24f,.61f,.32f,.61f);line(.68f,.61f,.76f,.61f)
            }
            NavigationIconKind.OTHER_ROUTES -> {
                line(.5f,.9f,.5f,.51f);line(.5f,.51f,.18f,.19f);line(.5f,.51f,.82f,.19f)
                line(.18f,.19f,.18f,.43f);line(.18f,.19f,.42f,.19f)
                line(.82f,.19f,.58f,.19f);line(.82f,.19f,.82f,.43f)
            }
            NavigationIconKind.MICROPHONE -> {
                drawRoundRect(color, Offset(w*.35f,h*.08f), Size(w*.3f,h*.52f), androidx.compose.ui.geometry.CornerRadius(w*.15f),style=Stroke(stroke))
                drawArc(color,0f,180f,false,Offset(w*.18f,h*.3f),Size(w*.64f,h*.46f),style=Stroke(stroke))
                line(.5f,.76f,.5f,.94f);line(.34f,.94f,.66f,.94f)
            }
        }
    }
}
