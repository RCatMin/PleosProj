package com.example.pleosproj.ui

import com.example.pleosproj.ui.state.HazardKind

// 지도·알림·진행 막대에서 같은 위험을 같은 색과 이름으로 표시한다. 위험 기호와 이름을 함께 써 경로·교통 정보와 구분한다.
fun hazardColor(kind: HazardKind): Int = when (kind) {
    HazardKind.FLOOD, HazardKind.HEAVY_RAIN -> 0xFFA65321.toInt()
    HazardKind.ICE, HazardKind.FOG -> 0xFF16776F.toInt()
    HazardKind.CLOSURE, HazardKind.ACCIDENT -> 0xFF9C2864.toInt()
    HazardKind.HEAVY_SNOW -> 0xFF2A70FF.toInt()
    HazardKind.CONSTRUCTION -> 0xFF886344.toInt()
}

fun hazardName(kind: HazardKind): String = when (kind) {
    HazardKind.FLOOD -> "침수"
    HazardKind.ICE -> "결빙"
    HazardKind.CLOSURE -> "통제"
    HazardKind.HEAVY_RAIN -> "호우"
    HazardKind.HEAVY_SNOW -> "대설"
    HazardKind.FOG -> "안개"
    HazardKind.ACCIDENT -> "사고"
    HazardKind.CONSTRUCTION -> "공사"
}
