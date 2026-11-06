package com.example.pleosproj.routing

import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.Place
import com.example.pleosproj.ui.state.SearchFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {

    // 카카오 로컬 키워드 검색 응답 모양(문서 기준). 좌표 없는 결과 하나를 일부러 넣었다
    private val json = javaClass.getResource("/api/kakao_keyword_samsung.json")!!.readText()

    @Test
    fun `검색 결과를 장소로 읽는다`() {
        val places = KakaoParser.places(json)
        assertEquals(2, places.size)
        val s = places[0]
        assertEquals("삼성역 2호선", s.name)
        assertEquals("서울 강남구 테헤란로 지하 538", s.address)
        assertEquals(GeoPoint(37.508844, 127.063160), s.position)
        assertEquals(3361, s.distanceMeters)
        assertEquals("지하철,전철", s.category)
        // 도로명 주소가 없으면 지번 주소
        assertEquals("서울 강남구 삼성동 159", places[1].address)
    }

    @Test
    fun `오류 응답을 화면용 이유로 바꾼다`() {
        val disabled = """{"errorType":"NotAuthorizedError","message":"App(SafeRoute) disabled OPEN_MAP_AND_LOCAL service."}"""
        assertEquals(SearchFailure.NOT_ALLOWED, PlaceSearch.failure(403, disabled).reason)
        assertEquals(SearchFailure.NOT_ALLOWED, PlaceSearch.failure(401, "{}").reason)
        assertEquals(SearchFailure.LIMIT, PlaceSearch.failure(429, "{}").reason)
        assertEquals(SearchFailure.NETWORK, PlaceSearch.failure(500, "").reason)
    }

    @Test
    fun `키가 없으면 요청하지 않고 알린다`() {
        val e = runCatching { PlaceSearch("").search("삼성역") }.exceptionOrNull() as PlaceSearchException
        assertEquals(SearchFailure.KEY_MISSING, e.reason)
    }

    @Test
    fun `최근 목적지를 저장 모양으로 바꿨다가 되돌린다`() {
        val p = Place("a", "삼성역", "서울 강남구", GeoPoint(37.5, 127.06), 3000, "지하철역")
        assertEquals(listOf(p.copy(distanceMeters = null)), RecentPlaces.decode(RecentPlaces.encode(listOf(p))))
        assertTrue(RecentPlaces.decode(null).isEmpty())
    }
}
