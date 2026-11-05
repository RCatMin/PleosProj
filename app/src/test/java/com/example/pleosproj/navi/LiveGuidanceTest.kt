package com.example.pleosproj.navi

import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.risk.WiperLevel
import com.example.pleosproj.ui.state.DriveMode
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.SegmentKind
import com.example.pleosproj.ui.state.TurnKind
import com.example.pleosproj.vehicle.VehicleSnapshot
import com.example.pleosproj.weather.KmaGridPoint
import com.example.pleosproj.weather.PrecipType
import com.example.pleosproj.weather.WeatherForecast
import com.example.pleosproj.weather.WeatherNow
import com.example.pleosproj.weather.WeatherWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class LiveGuidanceTest {

    private val now = LocalDateTime.of(2026, 11, 5, 18, 10)
    private val grid = KmaGridPoint(61, 126)

    // 출발점 기준 회전 안내: 700m 우회전 → 600m 지하차도 → 1200m 좌회전 → 500m 도착 (합 3000m)
    private val turns = listOf(
        NaviTurn(TurnKind.RIGHT, 700.0, "테헤란로 방면"),
        NaviTurn(TurnKind.STRAIGHT, 600.0, "삼성 지하차도", entersUnderpass = true),
        NaviTurn(TurnKind.LEFT, 1200.0, "봉은사로 방면"),
        NaviTurn(TurnKind.ARRIVE, 500.0, "도착"),
    )

    private fun navi(remain: Int, list: List<NaviTurn> = turns) = NaviSnapshot(
        guiding = true, destinationName = "삼성역", remainMeters = remain, remainSeconds = remain / 10,
        turns = list, lat = 37.5, lng = 127.03, heading = 90.0,
    )

    private fun rain(mm: Double) = WeatherNow(grid, now, 22.0, mm, if (mm > 0) PrecipType.RAIN else PrecipType.NONE, 90, 3.0)

    @Test
    fun `남은 거리로 다음 회전까지 거리를 잡는다`() {
        val g = LiveGuidance.build(LiveInputs(navi(remain = 2500), now))
        assertEquals(DriveMode.LIVE, g.mode)
        assertNull(g.routeLine)
        // 500m 왔으니 첫 우회전까지 200m
        assertEquals(TurnKind.RIGHT, g.nextTurn!!.kind)
        assertEquals(200, g.nextTurn!!.distanceMeters)
        assertEquals(listOf(800, 2000), g.upcomingTurns.map { it.distanceMeters })
        assertEquals(500.0 / 3000, g.progress.currentRatio, 1e-9)
        assertEquals(LocalTime.of(18, 14, 10), g.arrivalTime)
    }

    @Test
    fun `회전 안내 목록이 지금 위치 기준으로 줄어들어도 결과가 같다`() {
        // 500m 지나 다시 받은 목록: 첫 구간이 200m로 줄었다
        val shrunk = listOf(turns[0].copy(segmentMeters = 200.0)) + turns.drop(1)
        val g = LiveGuidance.build(LiveInputs(navi(remain = 2500, list = shrunk), now, routeTotalMeters = 3000.0))
        assertEquals(200, g.nextTurn!!.distanceMeters)
        assertEquals(500.0 / 3000, g.progress.currentRatio, 1e-9)
    }

    @Test
    fun `비가 오면 경로 위 지하차도를 침수 위험으로 알린다`() {
        val g = LiveGuidance.build(
            LiveInputs(navi(remain = 2500), now, weatherHere = rain(45.0),
                warnings = listOf(WeatherWarning(now, "호우경보", "호우", "경보", active = true)),
                vehicle = VehicleSnapshot(wiper = WiperLevel.HIGH)),
        )
        val underpass = g.hazards.first { it.id.startsWith("underpass") }
        assertEquals(HazardKind.FLOOD, underpass.kind)
        assertTrue(underpass.level >= RiskLevel.MEDIUM)
        assertEquals(1300.0 / 3000, underpass.ratioOnRoute!!, 1e-9)
        assertTrue(g.progress.segments.any { it.kind == SegmentKind.HAZARD && it.hazardKind == HazardKind.FLOOD })
        // 지하차도까지 800m → 알림
        val alert = g.alert!!
        assertEquals(underpass.id, alert.hazardId)
        assertTrue(alert.message.startsWith("800m 앞"))
    }

    @Test
    fun `맑으면 지하차도 위험은 낮고 알림이 없다`() {
        val g = LiveGuidance.build(LiveInputs(navi(remain = 2500), now, weatherHere = rain(0.0)))
        assertTrue(g.hazards.first { it.id.startsWith("underpass") }.level <= RiskLevel.LOW)
        assertNull(g.alert)
    }

    @Test
    fun `앞쪽 통제는 우회를 제안한다`() {
        // 동쪽(진행 방향) 약 900m
        val event = NaviTrafficEvent(37.5, 127.0402, HazardKind.CLOSURE, "침수로 전면 통제", closure = true)
        val g = LiveGuidance.build(LiveInputs(navi(remain = 2500).copy(events = listOf(event)), now, weatherHere = rain(45.0)))
        val alert = g.alert!!
        assertTrue(alert.suggestsDetour)
        assertTrue(alert.message.startsWith("900m 앞"))
    }

    @Test
    fun `뒤쪽 통제는 알리지 않는다`() {
        val event = NaviTrafficEvent(37.5, 127.0198, HazardKind.CLOSURE, "전면 통제", closure = true)
        val g = LiveGuidance.build(LiveInputs(navi(remain = 2500).copy(events = listOf(event)), now))
        assertNull(g.alert)
    }

    @Test
    fun `막힘 구간과 미끄러운 도로를 진행 막대에 놓는다`() {
        val n = navi(remain = 2500).copy(
            congestion = listOf(NaviCongestion(1500.0, 300.0, heavy = true, roadName = "봉은사로")),
            cautions = listOf(NaviCaution("c1", CautionKind.SLIPPERY_ROAD, remainMeters = 600)),
        )
        val g = LiveGuidance.build(LiveInputs(n, now, weatherHere = rain(5.0), vehicle = VehicleSnapshot(outsideTempC = -1.0)))
        val heavy = g.progress.segments.first { it.kind == SegmentKind.CONGESTION_HEAVY }
        assertEquals(0.5, heavy.startRatio, 1e-9)
        assertEquals(0.6, heavy.endRatio, 1e-9)
        val slippery = g.hazards.first { it.id == "caution-c1" }
        assertEquals(HazardKind.ICE, slippery.kind)
        assertEquals(1100.0 / 3000, slippery.ratioOnRoute!!, 1e-9)
        assertTrue(slippery.level >= RiskLevel.MEDIUM)
        assertEquals("caution-c1", g.alert!!.hazardId)
    }

    @Test
    fun `도착 무렵 목적지 예보를 날씨 카드에 넣는다`() {
        val forecasts = (0..5).map { h ->
            WeatherForecast(grid, now.withMinute(0).plusHours(h.toLong()), 21.0 - h, 10.0 * h, PrecipType.RAIN, 4, 0.0)
        }
        val g = LiveGuidance.build(
            LiveInputs(navi(remain = 2500).copy(remainSeconds = 3600), now, weatherHere = rain(3.0), forecastAtDestination = forecasts),
        )
        assertEquals(listOf("지금", "목적지"), g.weather.map { it.label })
        // 19:10 도착 → 19시 예보
        assertEquals(10.0, g.weather[1].rainMmPerHour, 1e-9)
    }

    @Test
    fun `특보 단계는 경보를 우선한다`() {
        val w = listOf(
            WeatherWarning(now, "호우주의보", "호우", "주의보", true),
            WeatherWarning(now, "호우경보", "호우", "경보", true),
            WeatherWarning(now, "풍랑경보", "풍랑", "경보", true),
        )
        assertEquals(WarningLevel.WARNING, LiveGuidance.warningLevel(w))
        // 풍랑 특보는 도로 위험과 관계없어 보지 않는다
        assertEquals(WarningLevel.NONE, LiveGuidance.warningLevel(w.drop(2)))
        assertEquals(WarningLevel.ADVISORY, LiveGuidance.warningLevel(w.take(1)))
    }

    @Test
    fun `반올림된 남은 거리 대신 회전 안내 합계를 쓴다`() {
        // 기본 내비는 21770m를 22000m로 준다
        val list = listOf(NaviTurn(TurnKind.RIGHT, 700.0, "대왕판교IC 방면"), NaviTurn(TurnKind.ARRIVE, 21070.0, "도착"))
        val g = LiveGuidance.build(LiveInputs(navi(remain = 22000, list = list), now))
        assertEquals(700, g.nextTurn!!.distanceMeters)
        assertEquals(0.0, g.progress.currentRatio, 1e-9)
        // 화면에 보이는 남은 거리는 기본 내비 값 그대로
        assertEquals(22000, g.remainingMeters)
    }
}
