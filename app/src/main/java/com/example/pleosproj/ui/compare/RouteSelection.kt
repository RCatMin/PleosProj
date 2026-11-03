package com.example.pleosproj.ui.compare

import com.example.pleosproj.ui.state.RouteCandidate
import com.example.pleosproj.ui.state.RouteCompareState

// 추천 여부와 위험 점수는 데이터 계층의 값을 쓴다. 화면에서는 선택 가능한 후보만 확인한다.
internal fun RouteCompareState.selectedRoute(): RouteCandidate? =
    candidates.firstOrNull { it.id == selectedId && !it.excluded }
        ?: candidates.firstOrNull { it.id == recommendedId && !it.excluded }
        ?: candidates.firstOrNull { !it.excluded }
