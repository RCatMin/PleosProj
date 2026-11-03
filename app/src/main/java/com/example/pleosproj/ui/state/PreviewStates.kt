package com.example.pleosproj.ui.state

import java.time.LocalTime

/**
 * 화면을 먼저 만들 때 쓰는 예시 상태. 실제 데이터가 연결되기 전까지 화면은 이 값으로 그린다.
 * 좌표는 PLE-10 지도 미리보기와 같은 서울 강남 일대다. 숫자는 예시일 뿐 실제 날씨·경로가 아니다.
 */
object PreviewStates {

    private val start = GeoPoint(37.5176, 127.0406)
    private val destination = GeoPoint(37.5111, 127.0592)

    private val routeSafe = listOf(
        start, GeoPoint(37.5154, 127.0418), GeoPoint(37.5104, 127.0437),
        GeoPoint(37.5085, 127.0450), GeoPoint(37.5100, 127.0522), destination,
    )
    private val routeClosed = listOf(
        start, GeoPoint(37.5202, 127.0397), GeoPoint(37.5225, 127.0508),
        GeoPoint(37.5149, 127.0553), destination,
    )
    private val routeLong = listOf(
        start, GeoPoint(37.5130, 127.0380), GeoPoint(37.5060, 127.0420),
        GeoPoint(37.5050, 127.0560), destination,
    )

    private val flood = Hazard(
        id = "flood-1", kind = HazardKind.FLOOD, position = GeoPoint(37.5202, 127.0443),
        level = RiskLevel.HIGH, title = "학동 지하차도",
        detail = "과거 침수 이력, 시간당 42mm 예보", ratioOnRoute = null,
    )
    private val closure = Hazard(
        id = "closure-1", kind = HazardKind.CLOSURE, position = GeoPoint(37.5149, 127.0553),
        level = RiskLevel.HIGH, title = "봉은사로 일부 통제",
        detail = "도로 침수로 양방향 통제 중", ratioOnRoute = null,
    )
    private val ice = Hazard(
        id = "ice-1", kind = HazardKind.ICE, position = GeoPoint(37.5085, 127.0450),
        level = RiskLevel.MEDIUM, title = "언덕길 결빙 주의",
        detail = "결빙 취약 구간, 기온 1℃", ratioOnRoute = 0.48,
    )

    private val weatherNow = WeatherCard("지금", 9, 60, 3.5, snow = false)
    private val weatherAhead = WeatherCard("10km 앞", 8, 80, 18.0, snow = false)

    /** 시나리오 모드 안내 화면: 경로선 있음, 위험 알림 있음 */
    val guidanceScenario = GuidanceState(
        mode = DriveMode.SCENARIO,
        destinationName = "삼성역",
        nextTurn = TurnInstruction(TurnKind.LEFT, 390, "선릉로 방면"),
        upcomingTurns = listOf(
            TurnInstruction(TurnKind.RIGHT, 1200, "테헤란로 방면"),
            TurnInstruction(TurnKind.ARRIVE, 2100, "도착"),
        ),
        remainingMeters = 3200,
        remainingSeconds = 900,
        arrivalTime = LocalTime.of(19, 50),
        myPosition = GeoPoint(37.5154, 127.0418),
        heading = 165.0,
        routeLine = routeSafe,
        hazards = listOf(ice, flood, closure),
        progress = RouteProgress(
            currentRatio = 0.18,
            totalMeters = 3900,
            segments = listOf(
                ProgressSegment(SegmentKind.TURN, 0.28, 0.28, "좌회전"),
                ProgressSegment(SegmentKind.CONGESTION_SLOW, 0.32, 0.40),
                ProgressSegment(SegmentKind.HAZARD, 0.46, 0.50, "결빙 주의", HazardKind.ICE),
                ProgressSegment(SegmentKind.TURN, 0.60, 0.60, "우회전"),
                ProgressSegment(SegmentKind.CONGESTION_HEAVY, 0.70, 0.78),
            ),
        ),
        weather = listOf(weatherNow, weatherAhead),
        alert = HazardAlert(
            hazardId = "ice-1", title = "앞쪽 결빙 주의",
            message = "1.1km 앞 언덕길, 기온 1℃", level = RiskLevel.MEDIUM, suggestsDetour = false,
        ),
    )

    /** 실제 주행 안내 화면: 경로선 없음 (ADR 0005), 우회 제안 알림 */
    val guidanceLive = guidanceScenario.copy(
        mode = DriveMode.LIVE,
        routeLine = null,
        alert = HazardAlert(
            hazardId = "closure-1", title = "앞쪽 도로 통제",
            message = "봉은사로 침수 통제, 우회할까요?", level = RiskLevel.HIGH, suggestsDetour = true,
        ),
    )

    /** 경로 비교: 최단 경로는 통제로 제외, 추천은 두 번째 */
    val routeCompare = RouteCompareState(
        destinationName = "삼성역",
        candidates = listOf(
            RouteCandidate(
                id = "a", name = "최단", durationSeconds = 32 * 60, distanceMeters = 3600, tollWon = 0,
                riskScore = 0.74, riskLevel = RiskLevel.HIGH, excluded = true,
                riskSummary = "봉은사로 침수 통제", routeLine = routeClosed, hazards = listOf(flood, closure),
            ),
            RouteCandidate(
                id = "b", name = "추천", durationSeconds = 37 * 60, distanceMeters = 3900, tollWon = 0,
                riskScore = 0.21, riskLevel = RiskLevel.LOW, excluded = false,
                riskSummary = "결빙 주의 1곳", routeLine = routeSafe, hazards = listOf(ice),
            ),
            RouteCandidate(
                id = "c", name = "도산대로", durationSeconds = 44 * 60, distanceMeters = 4800, tollWon = 0,
                riskScore = 0.15, riskLevel = RiskLevel.LOW, excluded = false,
                riskSummary = "위험 구간 없음, 시간 +38%", routeLine = routeLong, hazards = emptyList(),
            ),
        ),
        selectedId = "b",
        recommendedId = "b",
        adviceWhenAllRisky = null,
    )

    /** 출발 전 브리핑: 위험 있음 */
    val briefing = BriefingState(
        destinationName = "삼성역",
        arrivalTime = LocalTime.of(19, 50),
        durationSeconds = 37 * 60,
        riskLines = listOf("봉은사로 침수 통제 1곳", "30분 뒤 시간당 18mm 비 예보", "언덕길 결빙 주의"),
        overallLevel = RiskLevel.MEDIUM,
        weather = listOf(weatherNow, weatherAhead),
        speaking = true,
    )

    /** 출발 전 브리핑: 위험 없음 */
    val briefingClear = briefing.copy(
        riskLines = emptyList(),
        overallLevel = RiskLevel.NONE,
        weather = listOf(WeatherCard("지금", 14, 10, 0.0, snow = false)),
        speaking = false,
    )
}
