package com.example.pleosproj.scenario

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime

class RainAirportDriveScenarioTest {

    private val json = File("src/main/assets/scenarios/rain-airport-drive.json").readText()

    /** 시연을 11시 56분 40초에 시작했다고 본다 */
    private val scenario = ScenarioParser.parse(json, now = LocalTime.of(11, 56, 40))
    private val engine = ScenarioEngine(scenario)
    private val drive = scenario.driveRoute
    private val driveLength = RouteGeometry.lengthMeters(drive.line)

    /** 주행 경로 위에 있어야 하는 위험 지점 (주행 순서) */
    private val onDrive = listOf(
        "irwon-underpass", "sadang-low", "sillim-dorimcheon", "gaebong-underpass", "sinwol-visibility",
    )

    private fun passSeconds(id: String): Double {
        val h = scenario.hazards.first { it.id == id }
        val (along, _) = RouteGeometry.project(drive.line, h.position)
        return along / driveLength * drive.durationSeconds
    }

    @Test
    fun `출발 시각은 시나리오를 읽은 지금이고 도착은 1시간 뒤다`() {
        assertEquals(LocalTime.of(11, 56), scenario.startTime)
        assertEquals(3600, drive.durationSeconds)
        assertEquals(LocalTime.of(12, 56), engine.guidance(0.0).arrivalTime)
        assertEquals(LocalTime.of(12, 56), engine.briefing().arrivalTime)
        // 다른 시각에 읽으면 그 시각이 출발 시각이 된다
        assertEquals(LocalTime.of(15, 30), ScenarioParser.parse(json, now = LocalTime.of(15, 30, 59)).startTime)
    }

    @Test
    fun `고정 출발 시각을 쓰는 기존 시나리오는 지금 시각과 상관없다`() {
        val rain = File("src/main/assets/scenarios/rain-commute.json").readText()
        assertEquals(LocalTime.of(18, 10), ScenarioParser.parse(rain, now = LocalTime.of(9, 0)).startTime)
    }

    @Test
    fun `원티드그라운드에서 김포공항까지 가는 시나리오를 읽는다`() {
        assertEquals("폭우 속 김포공항 가는 길", scenario.title)
        assertEquals("김포공항", scenario.destinationName)
        assertTrue(scenario.summary.contains("원티드그라운드"))
        assertTrue(scenario.summary.contains("시연용 가정"))
        assertEquals(TurnKind.ARRIVE, drive.turns.last().kind)
        assertTrue(driveLength in 30_000.0..45_000.0)
        // 출발점은 원티드그라운드(백제고분로37길 22)에서 200m 안쪽이다
        assertTrue(RouteGeometry.distanceMeters(drive.line.first(), LatLng(37.5037, 127.1040)) < 200.0)
    }

    @Test
    fun `한강변 두 길은 지날 때 통제되어 빠지고 남부순환로 길만 추천된다`() {
        val compare = engine.compare()
        assertEquals("nambu", compare.recommendedId)
        assertEquals(drive.id, compare.recommendedId)
        val byId = compare.candidates.associateBy { it.id }
        listOf("olympic", "gangbyeon").forEach {
            assertTrue(it, byId.getValue(it).excluded)
            assertTrue(byId.getValue(it).riskSummary, byId.getValue(it).riskSummary.contains("통제"))
        }
        assertTrue(!byId.getValue("nambu").excluded)
        // 통제 없이 갈 수 있는 길은 하나뿐이다
        assertEquals(1, compare.candidates.count { !it.excluded })
    }

    @Test
    fun `위험 지점 다섯 곳이 주행 경로 위에 떨어져 있고 지나기 직전에 경고가 뜬다`() {
        assertNull(engine.guidance(0.0).alert)
        val along = onDrive.map { id ->
            val (a, off) = RouteGeometry.project(drive.line, scenario.hazards.first { it.id == id }.position)
            assertTrue("$id 경로에서 ${off}m", off <= 150.0)
            val alert = engine.guidance(passSeconds(id) - 30).alert
            assertEquals(id, alert?.hazardId)
            assertTrue(id, alert!!.level >= RiskLevel.MEDIUM)
            a
        }
        // 경고 거리(1.2km)보다 멀리 떨어져 경고가 겹치지 않는다
        along.zipWithNext().forEach { (a, b) -> assertTrue(b - a > 2_000.0) }
        // 통제 지점은 주행 경로에서 멀다
        scenario.hazards.filter { it.id !in onDrive }.forEach {
            assertTrue(it.id, RouteGeometry.project(drive.line, it.position).second > 150.0)
        }
    }

    @Test
    fun `위험 지점은 약 8·24·33·44·51분에 지난다`() {
        val minutes = onDrive.map { passSeconds(it) / 60 }
        listOf(8, 24, 33, 44, 51).zip(minutes).forEach { (want, got) -> assertEquals(want.toDouble(), got, 2.0) }
    }

    @Test
    fun `60분 끝까지 경로를 따라가 도착한다`() {
        listOf(0.0, 900.0, 1800.0, 2700.0, 3599.0).forEach { t ->
            val g = engine.guidance(t)
            val (along, off) = RouteGeometry.project(drive.line, LatLng(g.myPosition.lat, g.myPosition.lng))
            assertEquals(driveLength * t / 3600, along, 30.0)
            assertTrue(off < 10.0)
        }
        val end = engine.guidance(3600.0)
        assertEquals(0, end.remainingSeconds)
        assertEquals(1.0, end.progress.currentRatio, 1e-9)
    }
}
