package com.example.pleosproj.scenario

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime

class ScenarioEngineTest {

    private val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/rain-commute.json").readText())
    private val engine = ScenarioEngine(scenario)

    @Test
    fun `시나리오 파일을 읽는다`() {
        assertEquals("도심 폭우 퇴근길", scenario.title)
        assertEquals(LocalTime.of(18, 10), scenario.startTime)
        assertEquals(3, scenario.routes.size)
        assertEquals("bongeunsa", scenario.driveRoute.id)
        assertEquals(TurnKind.RIGHT, scenario.driveRoute.turns.first().kind)
    }

    @Test
    fun `지날 때 통제되는 경로는 빼고 봉은사로를 추천한다`() {
        val compare = engine.compare()
        val teheran = compare.candidates.first { it.id == "teheran" }
        assertTrue(teheran.excluded)
        assertTrue(teheran.riskSummary.contains("통제"))
        assertEquals("bongeunsa", compare.recommendedId)
        assertNull(compare.adviceWhenAllRisky)
    }

    @Test
    fun `출발하면 첫 회전까지 거리와 남은 시간을 보여 준다`() {
        val g = engine.guidance(0.0)
        assertEquals(TurnKind.RIGHT, g.nextTurn!!.kind)
        assertEquals(782.0, g.nextTurn!!.distanceMeters.toDouble(), 1.0)
        assertEquals(1080, g.remainingSeconds)
        assertEquals(LocalTime.of(18, 28), g.arrivalTime)
        assertEquals(0.0, g.progress.currentRatio, 1e-9)
        assertNotNull(g.routeLine)
    }

    @Test
    fun `중간 지점에서 위치와 진행률이 맞는다`() {
        val g = engine.guidance(540.0)
        assertEquals(0.5, g.progress.currentRatio, 1e-9)
        val here = LatLng(g.myPosition.lat, g.myPosition.lng)
        val (along, off) = RouteGeometry.project(scenario.driveRoute.line, here)
        assertEquals(RouteGeometry.lengthMeters(scenario.driveRoute.line) / 2, along, 30.0)
        // project()가 20m 간격으로 찾으므로 10m 안쪽이면 경로 위다
        assertTrue(off < 15)
    }

    @Test
    fun `도착하면 남은 거리가 0이다`() {
        val g = engine.guidance(2000.0)
        assertEquals(0, g.remainingMeters)
        assertEquals(0, g.remainingSeconds)
        assertNull(g.nextTurn)
    }

    @Test
    fun `물 고임 구간에 가까워지면 알림을 띄운다`() {
        val seonjeong = scenario.hazards.first { it.id == "seonjeong-water" }
        val along = RouteGeometry.project(scenario.driveRoute.line, seonjeong.position).first
        val length = RouteGeometry.lengthMeters(scenario.driveRoute.line)
        // 500m 앞까지 왔을 때
        val t = (along - 500) / length * scenario.driveRoute.durationSeconds
        val alert = engine.guidance(t).alert
        assertNotNull(alert)
        assertEquals("seonjeong-water", alert!!.hazardId)
        assertTrue(alert.message.startsWith("500m 앞"))
        // 지나간 뒤에는 사라진다
        val after = (along + 100) / length * scenario.driveRoute.durationSeconds
        assertTrue(engine.guidance(after).alert?.hazardId != "seonjeong-water")
    }

    @Test
    fun `통제 시각이 지나면 위험 지점이 통제로 바뀐다`() {
        val before = engine.guidance(0.0).hazards.first { it.id == "seolleung-low" }
        val after = engine.guidance(600.0).hazards.first { it.id == "seolleung-low" }
        assertEquals(HazardKind.FLOOD, before.kind)
        assertEquals(HazardKind.CLOSURE, after.kind)
        assertEquals(RiskLevel.HIGH, after.level)
        // 운전 경로 밖이라 진행 막대에는 없다
        assertNull(after.ratioOnRoute)
    }

    @Test
    fun `폭우 구간이 진행 막대에 위험 구간으로 표시된다`() {
        val segs = engine.guidance(400.0).progress.segments
        assertTrue(segs.any { it.kind == com.example.pleosproj.ui.state.SegmentKind.HAZARD })
    }

    @Test
    fun `브리핑에 통제와 강한 비를 알린다`() {
        val b = engine.briefing()
        assertEquals("삼성역", b.destinationName)
        assertEquals(1080, b.durationSeconds)
        assertTrue(b.riskLines.any { it.contains("통제") })
        assertTrue(b.riskLines.any { it.contains("60mm") })
    }
}
