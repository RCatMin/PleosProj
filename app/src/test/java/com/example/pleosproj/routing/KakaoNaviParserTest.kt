package com.example.pleosproj.routing

import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.Vulnerability
import com.example.pleosproj.ui.state.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoNaviParserTest {

    private fun fixture(p: String) = javaClass.getResource("/api/kakaonavi_seongnam_samsung_$p.json")!!.readText()

    @Test
    fun `성남에서 삼성역 추천 길을 읽는다`() {
        val r = KakaoNaviParser.parse(fixture("RECOMMEND"), "추천 길").first()
        assertEquals("추천 길", r.name)
        assertEquals("kakaomobility", r.provider)
        assertEquals(13925, r.distanceMeters)
        assertEquals(3230, r.durationSeconds)
        assertEquals(0, r.tollWon)
        assertTrue(r.line.size > 50)
        // 회전 위치는 차례로 늘어나고 마지막은 도착
        val at = r.turns.map { it.atMeters }
        assertEquals(at.sorted(), at)
        assertEquals(TurnKind.ARRIVE, r.turns.last().kind)
        assertEquals(RouteGeometry.lengthMeters(r.line), at.last(), 1.0)
        assertTrue(r.turns.any { it.kind == TurnKind.RIGHT && it.text == "코엑스 방면으로 우회전" })
    }

    @Test
    fun `지하차도 진입을 침수 취약 지점으로 읽는다`() {
        val r = KakaoNaviParser.parse(fixture("RECOMMEND"), "추천 길").first()
        val u = r.hazards.single { it.title == "대치지하차도" }
        assertEquals(Vulnerability.UNDERPASS, u.vulnerability)
        // 지점이 경로선 위에 있다
        assertTrue(RouteGeometry.project(r.line, u.position).second < 30)
    }

    @Test
    fun `교통 상태로 막히는 구간을 만든다`() {
        val r = KakaoNaviParser.parse(fixture("RECOMMEND"), "추천 길").first()
        assertTrue(r.congestion.isNotEmpty())
        assertTrue(r.congestion.all { it.endMeters > it.startMeters && it.endMeters <= RouteGeometry.lengthMeters(r.line) + 1 })
    }

    @Test
    fun `안내 문구로 회전 종류를 정한다`() {
        assertEquals(TurnKind.LEFT, KakaoNaviParser.kind("좌회전"))
        assertEquals(TurnKind.KEEP_RIGHT, KakaoNaviParser.kind("판교 방면으로 오른쪽 방향"))
        assertEquals(TurnKind.ENTER_HIGHWAY, KakaoNaviParser.kind("분당수서로 방면으로 도시고속도로 입구"))
        assertEquals(TurnKind.STRAIGHT, KakaoNaviParser.kind("학여울역 서초IC 방면으로 지하차도 진입"))
        assertEquals(TurnKind.TOLLGATE, KakaoNaviParser.kind("성남톨게이트"))
        assertEquals(null, KakaoNaviParser.kind("직진"))
    }

    @Test
    fun `길찾기 실패 코드는 이유와 함께 알린다`() {
        val json = """{"trans_id":"x","routes":[{"result_code":104,"result_msg":"출발지와 도착지가 5 m 이내로 설정된 경우 경로를 탐색할 수 없음"}]}"""
        val e = runCatching { KakaoNaviParser.parse(json, "추천 길") }.exceptionOrNull() as KakaoNaviParser.KakaoNaviException
        assertEquals(104, e.code)
    }

    @Test
    fun `카카오가 실패하면 OSRM으로 계산한다`() {
        val osrm = OsrmParser.parse(javaClass.getResource("/api/osrm_seongnam_samsung.json")!!.readText())
        val source = FallbackRouteSource({ _, _, _ -> throw KakaoNaviParser.KakaoNaviException(429, "한도 초과") }, { _, _, _ -> osrm })
        val got = source.route(listOf(osrm[0].line.first(), osrm[0].line.last()), true, null)
        assertTrue(got.all { it.provider == "osrm" })
    }

    @Test
    fun `후보 경로에 성격으로 이름을 붙인다`() {
        val base = KakaoNaviParser.parse(fixture("RECOMMEND"), "추천 길").first()
        val a = base.copy(id = "a", durationSeconds = 600, distanceMeters = 4000)
        val b = base.copy(id = "b", durationSeconds = 700, distanceMeters = 3000)
        val c = base.copy(id = "c", durationSeconds = 800, distanceMeters = 5000)
        assertEquals(listOf("추천 길", "가장 짧은 길", "빠른 순서 3위"), Candidates.nameByRole(listOf(a, b, c)).map { it.name })
        // 첫 경로가 가장 빠르지 않으면 가장 빠른 길을 따로 표시한다
        assertEquals(listOf("가장 짧은 길", "가장 빠른 길"), Candidates.nameByRole(listOf(b, a)).map { it.name })
    }
}
