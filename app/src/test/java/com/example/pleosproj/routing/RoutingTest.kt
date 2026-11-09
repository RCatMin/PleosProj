package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.traffic.FreezingSpot
import com.example.pleosproj.traffic.TrafficEvent
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.weather.PrecipType
import com.example.pleosproj.weather.WeatherForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RoutingTest {

    private val now = LocalDateTime.of(2026, 11, 6, 18, 10)
    private val from = LatLng(37.4127, 127.0947)
    private val to = LatLng(37.5088, 127.0631)
    // 성남 → 삼성역 실제 OSRM 응답: 경부고속도로(빠름), 대왕판교로·남부순환로(느림)
    private val osrm = OsrmParser.parse(javaClass.getResource("/api/osrm_seongnam_samsung.json")!!.readText())

    private val requests = mutableListOf<List<LatLng>>()
    private val source = RouteSource { points, _, _ ->
        requests += points
        // 바로 가는 요청에만 두 경로를 주고, 경유 요청은 경로 없음으로 둔다
        if (points.size == 2) osrm else emptyList()
    }

    private fun rain(mm: Double) = { grid: com.example.pleosproj.weather.KmaGridPoint ->
        (0..5).map { h -> WeatherForecast(grid, now.withMinute(0).plusHours(h.toLong()), 18.0, mm, if (mm > 0) PrecipType.RAIN else PrecipType.NONE, 4, 0.0) }
    }

    @Test
    fun `후보 경로를 받아 위험도를 비교한다`() {
        val r = Routing(source, rain(0.0), null, null).plan(from, to, "삼성역", VehicleSignals(), now = now)
        assertEquals(2, r.routes.size)
        assertEquals(listOf("route-0", "route-1"), r.compare.candidates.map { it.id })
        // 두 경로가 서로 다른 길 이름으로 구별된다
        assertEquals("경부고속도로 경유", r.compare.candidates[0].name)
        assertNotEquals(r.compare.candidates[0].name, r.compare.candidates[1].name)
        assertTrue(r.compare.candidates.all { it.routeLine.isNotEmpty() })
        // 맑으면 빠른 길을 추천한다
        assertEquals("route-0", r.compare.recommendedId)
        // 서로 다른 후보가 이미 두 개라 경유 지점으로 더 묻지 않는다
        assertEquals(1, requests.size)
    }

    @Test
    fun `빠른 길에 통제가 있으면 빼고 다른 길을 추천한다`() {
        val fast = osrm[0]
        val mid = RouteGeometry.pointAt(fast.line, RouteGeometry.lengthMeters(fast.line) / 2)
        val closure = TrafficEvent("고속도로", "통제", "공사통제", "경부고속도로", mid.lat, mid.lng, null, null, "전면 통제", true)
        val r = Routing(source, rain(30.0), null, { _, _, _, _ -> listOf(closure) }).plan(from, to, "삼성역", VehicleSignals(), now = now)
        val first = r.compare.candidates.first { it.id == "route-0" }
        assertTrue(first.excluded)
        assertTrue(first.riskSummary.endsWith("통제"))
        assertTrue(first.hazards.any { it.kind == HazardKind.CLOSURE })
        assertEquals("route-1", r.compare.recommendedId)
    }

    @Test
    fun `날씨를 못 받으면 비 없이 계산하고 알린다`() {
        val r = Routing(source, { throw java.io.IOException("끊김") }, null, null).plan(from, to, "삼성역", VehicleSignals(), now = now)
        assertTrue(r.weatherMissing)
        assertEquals(2, r.routes.size)
    }

    @Test(expected = RoutingException::class)
    fun `길찾기 서버에 연결하지 못하면 이유를 알린다`() {
        Routing({ _, _, _ -> throw java.io.IOException("끊김") }, null, null, null).plan(from, to, "삼성역", VehicleSignals())
    }

    @Test
    fun `피해야 할 지점을 지나지 않는 경로를 고른다`() {
        val fast = osrm[0]
        val avoid = RouteGeometry.pointAt(fast.line, RouteGeometry.lengthMeters(fast.line) / 2)
        val detour = Routing(source, null, null, null).detour(from, to, avoid)!!
        assertNotEquals(fast.line, detour.line)
        assertTrue(Candidates.avoids(detour, avoid))
    }

    @Test
    fun `겹치는 경로는 하나만 남긴다`() {
        val a = osrm[0]
        val copy = a.copy(id = "copy", durationSeconds = a.durationSeconds + 10)
        assertEquals(listOf(a.id, osrm[1].id), Candidates.pickDistinct(listOf(copy, a, osrm[1])).map { it.id })
        assertTrue(Candidates.overlap(a, copy) > 0.99)
        assertTrue(Candidates.overlap(a, osrm[1]) < 0.5)
    }

    @Test
    fun `경로가 결빙 사고 다발지역을 지나면 위험 지점과 요약 개수에 나온다`() {
        val fast = osrm[0]
        val onRoute = RouteGeometry.pointAt(fast.line, RouteGeometry.lengthMeters(fast.line) * 0.3)
        // 경로에서 150m쯤 떨어진 다발지역 중심(원 안을 지남)과 멀리 떨어진 곳
        val near = FreezingSpot("궁내동", LatLng(onRoute.lat + 0.0013, onRoute.lng), 3, listOf(2023))
        val far = FreezingSpot("먼 곳", LatLng(37.30, 126.90), 5, listOf(2023))
        val r = Routing(source, rain(0.0), null, null, freezing = { listOf(near, far) }).plan(from, to, "삼성역", VehicleSignals(), now = now)
        assertEquals(1, r.hazards.count { it.kind == HazardKind.ICE })
        val hit = r.compare.candidates.filter { c -> c.hazards.any { it.kind == HazardKind.ICE } }
        assertTrue(hit.isNotEmpty())
        hit.forEach { assertTrue(it.riskSummary, it.riskSummary.endsWith("결빙 사고가 잦은 곳 1곳")) }
        val ice = hit.first().hazards.first { it.kind == HazardKind.ICE }
        assertTrue(ice.detail.contains("도로교통공단"))
        // 18℃에서는 위험하지 않다
        assertEquals(com.example.pleosproj.ui.state.RiskLevel.NONE, ice.level)
    }

    @Test
    fun `영하이면 결빙 사고 다발지역의 위험도가 오른다`() {
        val fast = osrm[0]
        val spot = FreezingSpot("궁내동", RouteGeometry.pointAt(fast.line, RouteGeometry.lengthMeters(fast.line) * 0.3), 3, listOf(2023))
        val cold = { grid: com.example.pleosproj.weather.KmaGridPoint ->
            (0..5).map { h -> WeatherForecast(grid, now.withMinute(0).plusHours(h.toLong()), -3.0, 0.0, PrecipType.NONE, 1, 0.0) }
        }
        val warm = Routing(source, rain(0.0), null, null, freezing = { listOf(spot) }).plan(from, to, "삼성역", VehicleSignals(), now = now)
        val r = Routing(source, cold, null, null, freezing = { listOf(spot) }).plan(from, to, "삼성역", VehicleSignals(), now = now)
        val ice = r.compare.candidates.flatMap { it.hazards }.first { it.kind == HazardKind.ICE }
        assertTrue(ice.level >= com.example.pleosproj.ui.state.RiskLevel.MEDIUM)
        val id = r.compare.candidates.first { c -> c.hazards.any { it.kind == HazardKind.ICE } }.id
        assertTrue(r.compare.candidates.first { it.id == id }.riskScore > warm.compare.candidates.first { it.id == id }.riskScore)
    }

    @Test
    fun `결빙 다발지역을 못 받아도 경로는 계산한다`() {
        val r = Routing(source, rain(0.0), null, null, freezing = { throw java.io.IOException("끊김") }).plan(from, to, "삼성역", VehicleSignals(), now = now)
        assertEquals(2, r.routes.size)
    }

    @Test
    fun `통제 경로 요약에 통제를 한 번만 붙인다`() {
        val fast = osrm[0]
        val at = RouteGeometry.pointAt(fast.line, RouteGeometry.lengthMeters(fast.line) / 2)
        val closure = TrafficEvent("일반도로", "통제", "전면통제", "테헤란로", at.lat, at.lng, null, null, "전면 통제", true)
        val r = Routing(source, null, null, events = { _, _, _, _ -> listOf(closure) }).plan(from, to, "삼성역", VehicleSignals(), now = now)
        val excluded = r.compare.candidates.first { it.excluded }
        assertEquals("테헤란로 통제", excluded.riskSummary)
    }
}
