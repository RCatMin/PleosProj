package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.ui.state.DriveMode
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.SegmentKind
import com.example.pleosproj.ui.state.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RouteGuidanceTest {

    private val now = LocalDateTime.of(2026, 11, 6, 18, 10)
    // 실제 OSRM 응답의 성남 → 삼성역 경부고속도로 경로 (약 15.7km)
    private val route = OsrmParser.parse(javaClass.getResource("/api/osrm_seongnam_samsung.json")!!.readText())[0]
    private val length = RouteGeometry.lengthMeters(route.line)
    private fun at(m: Double) = RouteGeometry.pointAt(route.line, m)
    private val rain = WeatherAlong { _, _ -> WeatherSample(45.0, false, 20.0, WarningLevel.WARNING) }
    private val dry = WeatherAlong { _, _ -> WeatherSample(0.0, false, 20.0, WarningLevel.NONE) }

    private fun guidance(hazards: List<RiskPoint> = emptyList(), weather: WeatherAlong = dry) =
        RouteGuidance(route, "삼성역", hazards, weather, now, VehicleSignals())

    @Test
    fun `진행 거리로 다음 회전과 남은 거리를 만든다`() {
        val g = guidance().state(100.0, at(100.0), null, VehicleSignals(), now)
        assertEquals(DriveMode.LIVE, g.mode)
        assertNotNull(g.routeLine)
        assertEquals(route.turns.first { it.atMeters > 100 }.kind, g.nextTurn!!.kind)
        assertEquals(length - 100, g.remainingMeters.toDouble(), 1.0)
        assertEquals(100 / length, g.progress.currentRatio, 1e-6)
        assertTrue(g.progress.segments.any { it.kind == SegmentKind.TURN })
        assertEquals(listOf("지금", "10km 앞", "목적지"), g.weather.map { it.label })
    }

    @Test
    fun `앞쪽 통제 지점은 우회를 제안한다`() {
        val closure = RiskPoint("its-0", HazardKind.CLOSURE, at(1500.0), "창업로 통제", closedFromSeconds = 0)
        val g = guidance(listOf(closure)).state(800.0, at(800.0), null, VehicleSignals(), now)
        val alert = g.alert!!
        assertTrue(alert.suggestsDetour)
        assertTrue(alert.message, alert.message.startsWith("700m 앞 창업로 통제"))
        // 지나가면 알림이 사라진다
        assertNull(guidance(listOf(closure)).state(1600.0, at(1600.0), null, VehicleSignals(), now).alert)
    }

    @Test
    fun `비가 오면 경로 위 침수 지점을 위험 구간으로 표시한다`() {
        val underpass = RiskPoint("u", HazardKind.FLOOD, at(5000.0), "지하차도", vulnerability = com.example.pleosproj.risk.Vulnerability.UNDERPASS)
        val g = guidance(listOf(underpass), rain).state(4200.0, at(4200.0), null, VehicleSignals(), now)
        assertTrue(g.progress.segments.any { it.kind == SegmentKind.HAZARD })
        assertEquals("u", g.alert!!.hazardId)
        assertFalse(g.alert!!.suggestsDetour)
    }

    @Test
    fun `위치를 경로에 맞추고 이탈과 도착을 판단한다`() {
        val t = RouteTracker(route, offRouteMeters = 50.0, offRouteSeconds = 10.0)
        assertEquals(1000.0, t.update(at(1000.0), 0.0).traveled, 15.0)
        // 경로에서 200m쯤 떨어진 곳: 10초 넘게 있어야 이탈
        val away = LatLng(at(1200.0).lat + 0.002, at(1200.0).lng)
        assertFalse(t.update(away, 1.0).offRoute)
        assertTrue(t.update(away, 12.0).offRoute)
        // 다시 경로로 돌아오면 이탈이 풀린다
        assertFalse(t.update(at(1300.0), 13.0).offRoute)
        assertTrue(t.update(route.line.last(), 20.0).arrived)
    }

    @Test
    fun `신호가 잠깐 뒤로 튀어도 진행 거리가 줄지 않는다`() {
        val t = RouteTracker(route)
        t.update(at(2000.0), 0.0)
        assertEquals(2000.0, t.update(at(1990.0), 1.0).traveled, 15.0)
    }

    @Test
    fun `앞으로 지날 시설을 남은 거리와 함께 넘긴다`() {
        val withFacilities = route.copy(facilities = listOf(
            FacilityPoint(500.0, com.example.pleosproj.ui.state.FacilityKind.IC, "판교IC"),
            FacilityPoint(3000.0, com.example.pleosproj.ui.state.FacilityKind.TOLLGATE, "서울TG"),
        ))
        val g = RouteGuidance(withFacilities, "삼성역", emptyList(), dry, now, VehicleSignals()).state(1000.0, at(1000.0), null, VehicleSignals(), now)
        assertEquals(listOf("서울TG"), g.facilities.map { it.name })
        assertEquals(2000, g.facilities.first().distanceMeters)
    }

    @Test
    fun `목적지보다 먼 거리의 날씨 카드는 넣지 않는다`() {
        val g = guidance().state(length - 5000, at(length - 5000), null, VehicleSignals(), now)
        assertEquals(listOf("지금", "목적지"), g.weather.map { it.label })
    }
}
