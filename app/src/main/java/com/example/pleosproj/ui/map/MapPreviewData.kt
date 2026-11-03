package com.example.pleosproj.ui.map

// PLE-10 표시 확인용 고정 좌표다. 실제 경로 계산과 위험 판단에는 사용하지 않는다.
internal object MapPreviewData {
    val start = 37.5176 to 127.0406
    val destination = 37.5111 to 127.0592
    val routes = listOf(
        listOf(start, 37.5154 to 127.0418, 37.5104 to 127.0437,
            37.5085 to 127.0450, 37.5100 to 127.0522, destination),
        listOf(start, 37.5202 to 127.0397, 37.5225 to 127.0508,
            37.5149 to 127.0553, destination),
    )
    val hazards = listOf(
        Triple(37.5202 to 127.0443, "침수", "#A65321"),
        Triple(37.5149 to 127.0553, "통제", "#9C2864"),
        Triple(37.5085 to 127.0450, "결빙", "#16776F"),
    )
}
