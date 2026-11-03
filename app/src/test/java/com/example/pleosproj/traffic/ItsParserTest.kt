package com.example.pleosproj.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItsParserTest {

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader?.getResource("api/$name")) { "$name 없음" }.readText()

    @Test
    fun 강남_일대_돌발상황을_좌표와_함께_읽는다() {
        val events = ItsParser.parseEvents(fixture("its_event_gangnam.json"))
        assertEquals(26, events.size)
        assertTrue(events.all { it.lat in 37.0..38.0 && it.lng in 126.5..127.5 })
        assertTrue(events.any { it.eventType == "공사" })
    }

    @Test
    fun 통제_여부를_구분한다() {
        val json = """{"header":{"resultCode":0,"resultMsg":"SUCCESS"},"body":{"items":[
            {"type":"국도","eventType":"기상","eventDetailType":"통제","coordX":"127.04","coordY":"37.52",
             "roadName":"올림픽대로","message":"침수로 전면통제","startDate":"20261103090000","endDate":"","lanesBlocked":"전차로"},
            {"type":"국도","eventType":"공사","eventDetailType":"","coordX":"127.05","coordY":"37.51",
             "roadName":"테헤란로","message":"1차로 공사","startDate":"20261103090000","endDate":"","lanesBlocked":""}]}}"""
        val (closed, open) = ItsParser.parseEvents(json)
        assertTrue(closed.isClosure)
        assertTrue(closed.lanesBlocked)
        assertFalse(open.isClosure)
    }
}
