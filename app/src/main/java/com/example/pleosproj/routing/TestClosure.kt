package com.example.pleosproj.routing

/**
 * 디버그 빌드에서 "우회할까요?"를 직접 눌러 보기 위한 시험용 통제. 디버그 명령(`closure_ahead`)만 값을 넣고,
 * 릴리스 빌드에는 값을 넣는 곳이 없어 늘 null이다. 값이 있으면 안내 중인 [GuidanceSession]이 곧바로
 * 지금 위치에서 그만큼 앞에 통제 지점을 둔다.
 */
object TestClosure {
    @Volatile var aheadMeters: Double? = null
}
