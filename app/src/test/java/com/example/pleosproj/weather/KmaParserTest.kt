package com.example.pleosproj.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KmaParserTest {

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader?.getResource("api/$name")) { "$name 없음" }.readText()

    @Test
    fun 초단기실황에서_기온과_강수를_꺼낸다() {
        val now = KmaParser.parseNow(fixture("kma_ultra_ncst.json"))
        assertEquals(KmaGridPoint(61, 125), now.grid)
        assertFalse(now.temperatureC.isNaN())
        assertEquals(PrecipType.NONE, now.precip)
        assertEquals(0.0, now.rainMm, 0.0)
    }

    @Test
    fun 초단기예보는_시각별로_묶여_6칸이_된다() {
        val forecast = KmaParser.parseForecast(fixture("kma_ultra_fcst.json"))
        assertEquals(6, forecast.size)
        assertTrue(forecast.zipWithNext().all { (a, b) -> a.time < b.time })
        assertTrue(forecast.all { !it.temperatureC.isNaN() })
    }

    @Test
    fun 특보_제목에서_종류와_발표_해제를_읽는다() {
        val warnings = KmaParser.parseWarnings(fixture("kma_warning_list.json"))
        assertTrue(warnings.isNotEmpty())
        val first = warnings.first()
        assertEquals("풍랑", first.kind)
        assertEquals("주의보", first.level)
        assertFalse(first.active)
    }

    @Test
    fun 특보가_없으면_빈_목록() {
        val empty = """{"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"}}}"""
        assertTrue(KmaParser.parseWarnings(empty).isEmpty())
    }

    @Test
    fun 예보의_강수량_문자열을_mm로_바꾼다() {
        assertEquals(0.0, KmaParser.rainMm("강수없음"), 0.0)
        assertEquals(0.5, KmaParser.rainMm("1mm 미만"), 0.0)
        assertEquals(7.0, KmaParser.rainMm("7.0mm"), 0.0)
        assertEquals(30.0, KmaParser.rainMm("30.0~50.0mm"), 0.0)
        assertEquals(50.0, KmaParser.rainMm("50.0mm 이상"), 0.0)
        assertEquals(2.5, KmaParser.rainMm("2.5"), 0.0)
    }
}
