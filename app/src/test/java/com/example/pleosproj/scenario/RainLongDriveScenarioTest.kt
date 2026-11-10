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

class RainLongDriveScenarioTest {

    private val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/rain-long-drive.json").readText())
    private val engine = ScenarioEngine(scenario)
    private val drive = scenario.driveRoute
    private val driveLength = RouteGeometry.lengthMeters(drive.line)

    /** 주행 경로 위에 있어야 하는 위험 지점 다섯 곳 (주행 순서) */
    private val onDrive = listOf(
        "yuljeon-downpour", "hogye-underpass", "seoksu-anyangcheon", "sindaebang-low", "yeouidaebang-visibility",
    )

    /** 위험 지점을 지나는 재생 시각(초) */
    private fun passSeconds(id: String): Double {
        val h = scenario.hazards.first { it.id == id }
        val (along, _) = RouteGeometry.project(drive.line, h.position)
        return along / driveLength * drive.durationSeconds
    }

    @Test
    fun `1시간 장거리 시나리오를 읽는다`() {
        assertEquals("집중호우 속 장거리 귀가", scenario.title)
        assertTrue(scenario.summary.contains("시연용 가정"))
        assertEquals(3600, drive.durationSeconds)
        assertEquals(TurnKind.ARRIVE, drive.turns.last().kind)
        // 수도권 30~50km 경로
        assertTrue(driveLength in 30_000.0..50_000.0)
    }

    @Test
    fun `도착 시각은 출발 1시간 뒤다`() {
        assertEquals(scenario.startTime.plusHours(1), engine.guidance(0.0).arrivalTime)
        assertEquals(LocalTime.of(19, 0), engine.briefing().arrivalTime)
        val end = engine.guidance(3600.0)
        assertEquals(0, end.remainingSeconds)
        assertEquals(1.0, end.progress.currentRatio, 1e-9)
    }

    @Test
    fun `지날 때 통제되는 서부간선 길은 빼고 주행 경로를 추천한다`() {
        val compare = engine.compare()
        assertEquals(drive.id, compare.recommendedId)
        assertEquals(drive.id, compare.selectedId)
        val byId = compare.candidates.associateBy { it.id }
        assertTrue(byId.getValue("seobu").excluded)
        assertTrue(byId.getValue("seobu").riskSummary, byId.getValue("seobu").riskSummary.contains("통제"))
        assertTrue(!byId.getValue(drive.id).excluded)
    }

    @Test
    fun `서로 다른 위험 지점 다섯 곳이 주행 경로 위에 떨어져 있다`() {
        val ids = scenario.hazards.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val along = onDrive.map { id ->
            val (a, off) = RouteGeometry.project(drive.line, scenario.hazards.first { it.id == id }.position)
            assertTrue("$id 경로에서 ${off}m", off <= 150.0)
            a
        }
        // 주행 순서대로 있고, 경고 거리(1.2km)보다 멀리 떨어져 경고가 겹치지 않는다
        along.zipWithNext().forEach { (a, b) -> assertTrue(b - a > 2_000.0) }
        // 다른 경로에만 있는 지점은 주행 경로에서 멀다
        scenario.hazards.filter { it.id !in onDrive }.forEach {
            assertTrue(it.id, RouteGeometry.project(drive.line, it.position).second > 150.0)
        }
    }

    @Test
    fun `위험 지점마다 지나기 직전에 그 지점 경고가 뜬다`() {
        assertNull(engine.guidance(0.0).alert)
        onDrive.forEach { id ->
            // 지나기 30초 전 (약 300m 앞)
            val alert = engine.guidance(passSeconds(id) - 30).alert
            assertEquals(id, alert?.hazardId)
            assertTrue(id, alert!!.level >= RiskLevel.MEDIUM)
        }
    }

    @Test
    fun `위험 지점은 약 8·20·32·44·54분에 지난다`() {
        val minutes = onDrive.map { passSeconds(it) / 60 }
        listOf(8, 20, 32, 44, 54).zip(minutes).forEach { (want, got) -> assertEquals(want.toDouble(), got, 4.0) }
    }

    @Test
    fun `안내 중 날씨는 지금·10km 앞·20km 앞·목적지를 지날 시각 기준으로 보여 준다`() {
        val start = engine.guidance(0.0).weather
        assertEquals(listOf("지금", "10km 앞", "20km 앞", "목적지"), start.map { it.label })
        // 출발할 때는 시간당 15mm, 10km 앞(약 15분 뒤) 40mm, 20km 앞(약 30분 뒤) 50mm, 목적지(1시간 뒤) 65mm
        assertEquals(listOf(15.0, 40.0, 50.0, 65.0), start.map { it.rainMmPerHour })
        // 남은 거리가 20km보다 짧으면 20km 앞 카드는 넣지 않는다
        assertEquals(listOf("지금", "10km 앞", "목적지"), engine.guidance(2400.0).weather.map { it.label })
    }

    @Test
    fun `진행률을 옮겨도 위치는 그 시점의 경로 위에 있다`() {
        listOf(0.0, 900.0, 1800.0, 2700.0, 3599.0).forEach { t ->
            val g = engine.guidance(t)
            val (along, off) = RouteGeometry.project(drive.line, LatLng(g.myPosition.lat, g.myPosition.lng))
            assertEquals(driveLength * t / 3600, along, 30.0)
            assertTrue(off < 10.0)
        }
        // 비가 거세진 뒤에는 와이퍼가 빠르다
        assertEquals(com.example.pleosproj.risk.WiperLevel.HIGH, engine.vehicleAt(1800.0).wiper)
    }
}
