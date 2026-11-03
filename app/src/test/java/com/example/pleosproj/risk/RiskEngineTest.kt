package com.example.pleosproj.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    private fun seg(seconds: Double, c: SegmentConditions = SegmentConditions()) =
        RouteSegment(lengthMeters = 1000.0, travelSeconds = seconds, conditions = c)

    @Test
    fun 맑은_날_보통_도로는_위험_없음() {
        val score = RiskEngine.segmentScore(SegmentConditions())
        assertEquals(0.0, score, 1e-9)
        assertEquals(RiskBand.NONE, RiskEngine.band(score))
    }

    @Test
    fun 통제_구간은_1점이고_경로는_제외된다() {
        assertEquals(1.0, RiskEngine.segmentScore(SegmentConditions(closed = true)), 0.0)
        val a = RiskEngine.assess(RouteInput("a", listOf(seg(60.0), seg(60.0, SegmentConditions(closed = true)))))
        assertTrue(a.excluded)
    }

    @Test
    fun 폭우_속_지하차도는_비가_없는_지하차도보다_훨씬_위험하다() {
        val dry = RiskEngine.segmentScore(SegmentConditions(vulnerability = Vulnerability.UNDERPASS))
        val wet = RiskEngine.segmentScore(
            SegmentConditions(rainMmPerHour = 40.0, warning = WarningLevel.WARNING, vulnerability = Vulnerability.UNDERPASS)
        )
        assertEquals(0.0, dry, 1e-9)
        assertTrue("폭우 지하차도 점수 $wet", wet >= 0.8)
        assertEquals(RiskBand.HIGH, RiskEngine.band(wet))
    }

    @Test
    fun 와이퍼가_최고_단계면_같은_예보에서_점수가_오른다() {
        val c = SegmentConditions(rainMmPerHour = 5.0, vulnerability = Vulnerability.RIVERSIDE)
        val plain = RiskEngine.segmentScore(c)
        val wiperHigh = RiskEngine.segmentScore(c, VehicleSignals(wiper = WiperLevel.HIGH))
        val wiperOff = RiskEngine.segmentScore(c, VehicleSignals(wiper = WiperLevel.OFF))
        assertTrue(wiperHigh > plain)
        assertTrue(wiperOff < plain)
        assertEquals(plain * 1.5, wiperHigh, 1e-9)
        assertEquals(plain * 0.8, wiperOff, 1e-9)
    }

    @Test
    fun 차에서_읽은_외기온도가_예보보다_우선한다() {
        val c = SegmentConditions(temperatureC = 5.0, icingZone = true)
        assertEquals(0.0, RiskEngine.segmentScore(c), 1e-9)
        assertTrue(RiskEngine.segmentScore(c, VehicleSignals(outsideTempC = -1.0)) > 0.0)
    }

    @Test
    fun 같은_비에서_세단이_SUV보다_침수에_민감하다() {
        val c = SegmentConditions(rainMmPerHour = 20.0, vulnerability = Vulnerability.UNDERPASS)
        val sedan = RiskEngine.segmentScore(c, VehicleSignals(bodyType = BodyType.SEDAN))
        val suv = RiskEngine.segmentScore(c, VehicleSignals(bodyType = BodyType.SUV))
        assertTrue(sedan > suv)
    }

    @Test
    fun 경로_점수는_위험한_구간에_오래_머물수록_커진다() {
        val danger = SegmentConditions(rainMmPerHour = 30.0)
        val short = RiskEngine.assess(RouteInput("s", listOf(seg(60.0, danger), seg(540.0))))
        val long = RiskEngine.assess(RouteInput("l", listOf(seg(300.0, danger), seg(300.0))))
        assertTrue(long.score > short.score)
    }

    @Test
    fun 기획서_예시처럼_통제_경로를_빼고_시간_기준_안에서_가장_안전한_경로를_고른다() {
        val rain = SegmentConditions(rainMmPerHour = 25.0, warning = WarningLevel.ADVISORY)
        // A: 가장 빠르지만 지하차도 통제
        val a = RouteInput("a", listOf(seg(960.0, rain), seg(960.0, rain.copy(closed = true))))
        // B: 37분, 하천변을 피해 간선도로
        val b = RouteInput("b", listOf(seg(1110.0, rain), seg(1110.0)))
        // C: 44분, 가장 안전하지만 B보다 20% 넘게 느림
        val c = RouteInput("c", listOf(seg(1320.0), seg(1320.0)))

        val r = RiskEngine.recommend(listOf(a, b, c))
        assertTrue(r.assessments.first { it.id == "a" }.excluded)
        assertEquals("b", r.recommendedId)
        assertFalse(r.suggestPostpone)
    }

    @Test
    fun 남은_경로가_모두_위험하면_출발_연기를_권한다() {
        val storm = SegmentConditions(rainMmPerHour = 50.0, warning = WarningLevel.WARNING, vulnerability = Vulnerability.UNDERPASS)
        val r = RiskEngine.recommend(listOf(RouteInput("x", listOf(seg(600.0, storm))), RouteInput("y", listOf(seg(620.0, storm)))))
        assertTrue(r.suggestPostpone)
        assertEquals("x", r.recommendedId)
    }

    @Test
    fun 모든_경로가_통제면_추천이_없다() {
        val r = RiskEngine.recommend(listOf(RouteInput("x", listOf(seg(600.0, SegmentConditions(closed = true))))))
        assertNull(r.recommendedId)
        assertTrue(r.suggestPostpone)
    }
}
