package com.example.pleosproj.traffic

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.routing.KakaoRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezingSpotsTest {

    // 공공데이터포털 결빙 사고 다발지역 실제 응답: 성남시 분당구 2023년
    private val bundang = javaClass.getResource("/api/freezing_seongnam_bundang_2023.json")!!.readText()

    @Test
    fun `다발지역 자료를 해석한다`() {
        val spots = FreezingParser.parse(bundang, 2023)
        assertEquals(3, spots.size)
        val first = spots.first()
        assertEquals("분당구 궁내동(분당수서간고속도로 늘푸른고교 부근)", first.name)
        assertEquals(3, first.accidents)
        assertEquals(listOf(2023), first.years)
        assertTrue(first.position.lat in 37.3..37.5 && first.position.lng in 127.0..127.2)
    }

    @Test
    fun `자료가 없는 해는 빈 목록이다`() {
        val none = """{"resultCode":"03","resultMsg":"NODATA_ERROR","totalCount":0}"""
        assertTrue(FreezingParser.parse(none, 2025).isEmpty())
    }

    @Test
    fun `여러 해에 나온 같은 곳은 하나로 합친다`() {
        val a = FreezingSpot("백석읍(복지사거리 부근)", LatLng(37.7900, 127.0000), 3, listOf(2023))
        val b = a.copy(name = "백석읍 복지리(복지사거리 부근)", position = LatLng(37.7903, 127.0003), accidents = 4, years = listOf(2024))
        val far = a.copy(position = LatLng(37.80, 127.01))
        val merged = FreezingParser.merge(listOf(a, b, far))
        assertEquals(2, merged.size)
        val same = merged.first { it.years.size == 2 }
        assertEquals(7, same.accidents)
        assertEquals(listOf(2023, 2024), same.years)
        // 이름은 최근 해 것을 쓴다
        assertEquals("백석읍 복지리(복지사거리 부근)", same.name)
    }

    @Test
    fun `경로가 지나는 시군구마다 한 번씩만 묻는다`() {
        val asked = mutableListOf<Pair<String, Int>>()
        val spot = FreezingSpot("역삼동", LatLng(37.5031, 127.0354), 3, listOf(2024))
        val finder = FreezingSpotFinder(
            regionOf = { p -> if (p.lng < 127.05) "11680" else "11710" },
            spotsOf = { code, year -> synchronized(asked) { asked += code to year }; if (code == "11680" && year == 2024) listOf(spot) else emptyList() },
            years = listOf(2023, 2024),
        )
        val line = listOf(LatLng(37.50, 127.02), LatLng(37.50, 127.08))
        assertEquals(listOf(spot), finder.along(listOf(line)))
        assertEquals(setOf("11680" to 2023, "11680" to 2024, "11710" to 2023, "11710" to 2024), asked.toSet())
        assertEquals(4, asked.size)
        // 다시 물어도 받은 값을 쓴다
        finder.along(listOf(line))
        assertEquals(4, asked.size)
    }

    @Test
    fun `카카오 행정구역 응답에서 법정동 시군구 코드를 꺼낸다`() {
        val json = """{"documents":[
            {"region_type":"B","code":"4113510300","address_name":"경기도 성남시 분당구 정자동"},
            {"region_type":"H","code":"4113554500","address_name":"경기도 성남시 분당구 정자동"}]}"""
        assertEquals("41135", KakaoRegion.parse(json))
        assertNull(KakaoRegion.parse("""{"documents":[]}"""))
    }
}
