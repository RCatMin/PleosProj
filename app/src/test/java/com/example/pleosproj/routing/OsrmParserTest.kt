package com.example.pleosproj.routing

import com.example.pleosproj.ui.state.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmParserTest {

    private fun fixture(name: String) = javaClass.getResource("/api/$name")!!.readText()

    @Test
    fun `성남에서 삼성역까지 후보 경로 두 개를 읽는다`() {
        val routes = OsrmParser.parse(fixture("osrm_seongnam_samsung.json"))
        assertEquals(2, routes.size)
        val first = routes[0]
        assertEquals("r0", first.id)
        assertEquals("경부고속도로 경유", first.name)
        assertTrue(first.line.size > 50)
        assertTrue(first.distanceMeters in 15000..16500)
        // 첫 회전은 창업로 우회전, 고속도로 진입은 이어지는 길 이름으로 안내한다
        assertEquals(TurnKind.RIGHT, first.turns[0].kind)
        assertEquals("창업로 방면", first.turns[0].text)
        assertTrue(first.turns.any { it.kind == TurnKind.ENTER_HIGHWAY && it.text == "경부고속도로 방면" })
        assertEquals(TurnKind.ARRIVE, first.turns.last().kind)
        // 회전 위치는 출발점부터 차례로 늘어나고 경로 길이 안에 있다
        val at = first.turns.map { it.atMeters }
        assertEquals(at.sorted(), at)
        assertEquals(at.last(), com.example.pleosproj.risk.RouteGeometry.lengthMeters(first.line), 1.0)
    }

    @Test
    fun `직진과 이름만 바뀌는 길은 안내하지 않는다`() {
        assertEquals(null, OsrmParser.kind("new name", "straight"))
        assertEquals(null, OsrmParser.kind("turn", "straight"))
        assertEquals(TurnKind.KEEP_LEFT, OsrmParser.kind("merge", "slight left"))
        assertEquals(TurnKind.EXIT_HIGHWAY, OsrmParser.kind("off ramp", "slight right"))
    }

    @Test
    fun `경로가 없으면 빈 목록, 오류면 예외`() {
        assertTrue(OsrmParser.parse("""{"code":"NoRoute","routes":[]}""").isEmpty())
        val e = runCatching { OsrmParser.parse("""{"code":"InvalidQuery","message":"bad"}""") }.exceptionOrNull()
        assertTrue(e is OsrmParser.OsrmException)
    }
}
