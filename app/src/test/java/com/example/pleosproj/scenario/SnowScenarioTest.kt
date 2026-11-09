package com.example.pleosproj.scenario

import com.example.pleosproj.navi.AlertSpeech
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SnowScenarioTest {

    private val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/snow-commute.json").readText())
    private val engine = ScenarioEngine(scenario)

    @Test
    fun `겨울 시나리오를 읽는다`() {
        assertEquals("눈 오는 퇴근길", scenario.title)
        assertTrue(scenario.summary.isNotBlank())
        assertEquals(2, scenario.routes.size)
        assertTrue(scenario.weather.all { it.snowing && it.temperatureC < 0 })
        assertTrue(scenario.hazards.all { it.kind == HazardKind.ICE && it.icingZone && it.statistical })
    }

    @Test
    fun `결빙 사고가 잦은 곳을 덜 지나는 한남대교 길을 추천한다`() {
        val compare = engine.compare()
        assertEquals("hannam", compare.recommendedId)
        val byId = compare.candidates.associateBy { it.id }
        assertTrue(byId.getValue("seongsu").riskSummary, byId.getValue("seongsu").riskSummary.endsWith("결빙 사고가 잦은 곳 3곳"))
        assertTrue(byId.getValue("hannam").riskSummary.endsWith("결빙 사고가 잦은 곳 1곳"))
        // 더 빠른 성수대교 길보다 위험도가 낮다
        assertTrue(byId.getValue("seongsu").durationSeconds < byId.getValue("hannam").durationSeconds)
        assertTrue(byId.getValue("hannam").riskScore < byId.getValue("seongsu").riskScore)
    }

    @Test
    fun `주행 중간 결빙 지점에 가까워지면 경고하고 음성으로 말한다`() {
        // 출발할 때는 경고가 없다
        assertNull(engine.guidance(0.0).alert)
        // 결빙 지점(한남오거리)은 주행 경로의 절반쯤에 있다
        val alert = engine.guidance(scenario.driveRoute.durationSeconds * 0.5).alert
        assertNotNull(alert)
        assertEquals("앞쪽 결빙 주의", alert!!.title)
        assertTrue(alert.level >= RiskLevel.MEDIUM)
        assertTrue(AlertSpeech.sentence(alert), AlertSpeech.sentence(alert).contains("결빙 사고가 잦은 곳입니다"))
    }
}
