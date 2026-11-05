package com.example.pleosproj.ui.map

import com.example.pleosproj.scenario.ScenarioEngine
import com.example.pleosproj.scenario.ScenarioParser
import com.example.pleosproj.ui.state.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RouteMotionTest {
    @Test fun fractionalPlaybackPositionsMatchScenarioEngineOnTheWholeRoute() {
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/rain-commute.json").readText())
        val engine = ScenarioEngine(scenario)
        val motion = RouteMotion(scenario.driveRoute.line.map { GeoPoint(it.lat, it.lng) })
        // 스냅샷 사이와 교차로를 포함해 엔진과 같은 도로 위 위치인지 확인한다.
        for (time in listOf(0.0, 0.016, 0.5, 122.25, 265.75, 439.125, 540.5, 899.99, 1079.5)) {
            val expected = engine.guidance(time)
            val actual = motion.at(time / scenario.driveRoute.durationSeconds)
            assertEquals(expected.myPosition.lat, actual.position.lat, 1e-9)
            assertEquals(expected.myPosition.lng, actual.position.lng, 1e-9)
            assertEquals(expected.heading, actual.heading, 1e-6)
        }
    }

    @Test fun cornerInterpolationStaysOnRoadInsteadOfCuttingAcrossTheBlock() {
        val motion = RouteMotion(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.001)))
        assertEquals(GeoPoint(0.0, 0.0005), motion.at(0.25).position)
        assertEquals(0.001, motion.at(0.75).position.lng, 1e-12)
        assertEquals(0.0005, motion.at(0.75).position.lat, 1e-12)
    }

    @Test fun timeClampsToDepartureAndArrivalAndDuplicatePointsStayFinite() {
        val start = GeoPoint(37.0, 127.0)
        val end = GeoPoint(37.001, 127.0)
        val motion = RouteMotion(listOf(start, start, end, end))
        assertEquals(start, motion.at(-0.5).position)
        assertEquals(end, motion.at(1.5).position)
        assertTrue(motion.at(0.5).heading.isFinite())
        assertTrue(motion.at(1.0).heading.isFinite())
        assertEquals(MapMotion(start, 0.0), RouteMotion(listOf(start, start)).at(0.5))
    }
}
