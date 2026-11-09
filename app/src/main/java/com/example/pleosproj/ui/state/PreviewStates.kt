package com.example.pleosproj.ui.state

import java.time.LocalTime

/**
 * 화면을 먼저 만들 때 쓰는 예시 상태. 실제 데이터가 연결되기 전까지 화면은 이 값으로 그린다.
 * 경로선은 시나리오 "도심 폭우 퇴근길"(강남역 → 삼성역)과 같은 도로 경로다 ([PreviewRoutes]).
 * 위험 지점과 날씨 숫자는 예시일 뿐 실제 정보가 아니다.
 */
object PreviewStates {

    /** 추천 경로: 테헤란로 → 언주로 → 봉은사로 → 영동대로 */
    private val routeSafe = PreviewRoutes.bongeunsa

    /** 최단 경로: 테헤란로 직진. 선릉역 부근 통제로 제외 */
    private val routeClosed = PreviewRoutes.teheran

    /** 먼 경로: 언주로 → 도곡로 → 영동대로 */
    private val routeLong = PreviewRoutes.dogok

    private val flood = Hazard(
        id = "flood-1", kind = HazardKind.FLOOD, position = GeoPoint(37.501590, 127.067403),
        level = RiskLevel.HIGH, title = "대치 지하차도",
        detail = "과거 침수 이력, 시간당 42mm 예보", ratioOnRoute = null,
    )
    private val closure = Hazard(
        id = "closure-1", kind = HazardKind.CLOSURE, position = GeoPoint(37.504404, 127.049065),
        level = RiskLevel.HIGH, title = "선릉역 부근 통제",
        detail = "도로 침수로 양방향 통제 중", ratioOnRoute = null,
    )
    private val ice = Hazard(
        id = "ice-1", kind = HazardKind.ICE, position = GeoPoint(37.510080, 127.043170),
        level = RiskLevel.MEDIUM, title = "언덕길 결빙 주의",
        detail = "결빙 취약 구간, 기온 1℃", ratioOnRoute = 0.50,
    )

    private val weatherNow = WeatherCard("지금", 9, 60, 3.5, snow = false)
    private val weatherAhead = WeatherCard("10km 앞", 8, 80, 18.0, snow = false)
    private val weatherFar = WeatherCard("20km 앞", 7, 70, 6.0, snow = false)

    private fun forecastOf(distance: Int, minutes: Int) = listOf(
        RouteForecastPoint("출발", 0, LocalTime.of(19, 13), weatherNow.copy(label = "출발")),
        RouteForecastPoint("${"%.1f".format(distance / 2 / 1000.0)}km", distance / 2, LocalTime.of(19, 13).plusMinutes(minutes / 2L),
            weatherAhead.copy(label = "중간")),
        RouteForecastPoint("도착", distance, LocalTime.of(19, 13).plusMinutes(minutes.toLong()), weatherAhead.copy(label = "도착")),
    )

    /** 시나리오 모드 안내 화면: 경로선 있음, 위험 알림 있음 */
    val guidanceScenario = GuidanceState(
        mode = DriveMode.SCENARIO,
        destinationName = "삼성역",
        nextTurn = TurnInstruction(TurnKind.LEFT, 350, "언주로 방면"),
        upcomingTurns = listOf(
            TurnInstruction(TurnKind.RIGHT, 1130, "봉은사로 방면"),
            TurnInstruction(TurnKind.RIGHT, 3130, "영동대로 방면"),
        ),
        remainingMeters = 4010,
        remainingSeconds = 900,
        arrivalTime = LocalTime.of(19, 50),
        myPosition = GeoPoint(37.501330, 127.038970),
        heading = 68.0,
        routeLine = routeSafe,
        hazards = listOf(ice, flood, closure),
        progress = RouteProgress(
            currentRatio = 0.18,
            totalMeters = 4890,
            segments = listOf(
                ProgressSegment(SegmentKind.TURN, 0.25, 0.25, "좌회전"),
                ProgressSegment(SegmentKind.CONGESTION_SLOW, 0.30, 0.38),
                ProgressSegment(SegmentKind.TURN, 0.41, 0.41, "우회전"),
                ProgressSegment(SegmentKind.HAZARD, 0.48, 0.52, "결빙 주의", HazardKind.ICE),
                ProgressSegment(SegmentKind.CONGESTION_HEAVY, 0.62, 0.72),
                ProgressSegment(SegmentKind.TURN, 0.82, 0.82, "우회전"),
                ProgressSegment(SegmentKind.TURN, 0.955, 0.955, "좌회전"),
            ),
        ),
        weather = listOf(weatherNow, weatherAhead, weatherFar),
        alert = HazardAlert(
            hazardId = "ice-1", title = "앞쪽 결빙 주의",
            message = "1.6km 앞 언덕길, 기온 1℃", level = RiskLevel.MEDIUM, suggestsDetour = false,
        ),
        facilities = listOf(
            RouteFacility(FacilityKind.IC, "송파IC", 2400),
            RouteFacility(FacilityKind.JC, "서하남JC", 7200),
            RouteFacility(FacilityKind.REST_AREA, "구리휴게소", 18300),
        ),
    )

    /** 실제 주행 안내 화면: 경로선 없음 (ADR 0005), 우회 제안 알림 */
    val guidanceLive = guidanceScenario.copy(
        mode = DriveMode.LIVE,
        routeLine = null,
        alert = HazardAlert(
            hazardId = "closure-1", title = "앞쪽 도로 통제",
            message = "선릉역 부근 침수 통제, 우회할까요?", level = RiskLevel.HIGH, suggestsDetour = true,
        ),
    )

    /** 경로 비교: 최단 경로는 통제로 제외, 추천은 두 번째 */
    val routeCompare = RouteCompareState(
        destinationName = "삼성역",
        candidates = listOf(
            RouteCandidate(
                id = "a", name = "최단", durationSeconds = 32 * 60, distanceMeters = 3360, tollWon = 0,
                riskScore = 0.74, riskLevel = RiskLevel.HIGH, excluded = true,
                riskSummary = "선릉역 부근 침수 통제", routeLine = routeClosed, hazards = listOf(closure),
                forecast = forecastOf(3360, 32),
            ),
            RouteCandidate(
                id = "b", name = "추천", durationSeconds = 37 * 60, distanceMeters = 4890, tollWon = 0,
                riskScore = 0.21, riskLevel = RiskLevel.LOW, excluded = false,
                riskSummary = "결빙 주의 1곳", routeLine = routeSafe, hazards = listOf(ice),
                forecast = forecastOf(4890, 37),
            ),
            RouteCandidate(
                id = "c", name = "도곡로", durationSeconds = 44 * 60, distanceMeters = 5300, tollWon = 0,
                riskScore = 0.42, riskLevel = RiskLevel.MEDIUM, excluded = false,
                riskSummary = "대치 지하차도 지남, 시간 +38%", routeLine = routeLong, hazards = listOf(flood),
                forecast = forecastOf(5300, 44),
            ),
        ),
        selectedId = "b",
        recommendedId = "b",
        adviceWhenAllRisky = null,
        forecastBasis = "기상청 초단기예보 · 6시간 이내",
    )

    /** 출발 전 브리핑: 위험 있음 */
    val briefing = BriefingState(
        destinationName = "삼성역",
        arrivalTime = LocalTime.of(19, 50),
        durationSeconds = 37 * 60,
        riskLines = listOf("선릉역 부근 침수 통제 1곳", "30분 뒤 시간당 18mm 비 예보", "언덕길 결빙 주의"),
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

    /** 검색 결과 예시: "삼성역" */
    val places = listOf(
        Place("samsung-station", "삼성역 2호선", "서울 강남구 테헤란로 지하 538", GeoPoint(37.508844, 127.063160), 3360, "지하철역"),
        Place("coex", "코엑스", "서울 강남구 영동대로 513", GeoPoint(37.511800, 127.059200), 3120, "문화시설"),
        Place("samsung-1dong", "삼성1동주민센터", "서울 강남구 봉은사로 616", GeoPoint(37.514500, 127.061000), 3700, "공공기관"),
    )

    /** 최근 목적지 예시 */
    val recentPlaces = listOf(places[0], Place("seoul-city-hall", "서울특별시청", "서울 중구 세종대로 110", GeoPoint(37.566500, 126.978000), null, "공공기관"))
}
