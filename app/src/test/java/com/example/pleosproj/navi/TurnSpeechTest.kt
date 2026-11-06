package com.example.pleosproj.navi

import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.TurnInstruction
import com.example.pleosproj.ui.state.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TurnSpeechTest {

    private fun right(m: Int) = TurnInstruction(TurnKind.RIGHT, m, "대왕판교IC 방면")

    @Test
    fun `1km, 300m, 바로 앞에서 한 번씩 말한다`() {
        val s = TurnSpeech()
        assertNull(s.next(right(1500)))
        assertEquals("1킬로미터 앞에서 대왕판교IC 방면으로 우회전입니다.", s.next(right(990))!!.text)
        assertNull(s.next(right(800)))
        assertEquals("300미터 앞에서 대왕판교IC 방면으로 우회전입니다.", s.next(right(290))!!.text)
        assertNull(s.next(right(200)))
        assertEquals("곧 대왕판교IC 방면으로 우회전입니다.", s.next(right(45))!!.text)
        assertNull(s.next(right(10)))
    }

    @Test
    fun `처음 보일 때 이미 가까우면 그 거리로 한 번 말하고 지난 기준은 건너뛴다`() {
        val s = TurnSpeech()
        assertEquals("700미터 앞에서 대왕판교IC 방면으로 우회전입니다.", s.next(right(700))!!.text)
        assertNull(s.next(right(650)))
        assertEquals("300미터 앞에서 대왕판교IC 방면으로 우회전입니다.", s.next(right(300))!!.text)
    }

    @Test
    fun `다음 회전으로 넘어가면 새로 말한다`() {
        val s = TurnSpeech()
        s.next(right(40))
        val left = TurnInstruction(TurnKind.LEFT, 250, "숭례문 방면")
        assertEquals("300미터 앞에서 숭례문 방면으로 좌회전입니다.", s.next(left)!!.text)
        // 이름이 같은 좌회전이 바로 이어지면 거리가 늘어난 것으로 새 회전을 알아챈다
        s.next(left.copy(distanceMeters = 30))
        assertEquals("300미터 앞에서 숭례문 방면으로 좌회전입니다.", s.next(left.copy(distanceMeters = 240))!!.text)
    }

    @Test
    fun `문구에 동작이 들어 있거나 이름만 있는 안내를 자연스럽게 읽는다`() {
        assertEquals("300미터 앞에서 봉은사로로 우회전입니다.",
            TurnSpeech.sentence(TurnInstruction(TurnKind.RIGHT, 300, "봉은사로로 우회전")))
        assertEquals("곧 대왕판교톨게이트입니다.",
            TurnSpeech.sentence(TurnInstruction(TurnKind.TOLLGATE, 40, "대왕판교톨게이트")))
        assertEquals("곧 목적지에 도착합니다.", TurnSpeech.sentence(TurnInstruction(TurnKind.ARRIVE, 30, "도착")))
        assertEquals("1.5킬로미터 앞에서 한남대교 방면으로 왼쪽 방향입니다.",
            TurnSpeech.sentence(TurnInstruction(TurnKind.KEEP_LEFT, 1490, "한남대교 방면")))
        // ㄹ 받침은 "로"
        assertEquals("300미터 앞에서 세종대로 방향 출구로 진출입니다.",
            TurnSpeech.sentence(TurnInstruction(TurnKind.EXIT_HIGHWAY, 300, "세종대로 방향 출구")))
    }

    @Test
    fun `출발 전 브리핑 문장`() {
        val b = BriefingState(
            destinationName = "삼성역", arrivalTime = LocalTime.of(18, 28), durationSeconds = 1080,
            riskLines = listOf("테헤란로 경로: 선릉역 저지대 도로 통제 예정", "15분 뒤 시간당 60mm 비 예보"),
            overallLevel = RiskLevel.MEDIUM, weather = emptyList(), speaking = true,
        )
        assertEquals(
            "삼성역까지 18분, 18시 28분 도착 예정입니다. 테헤란로 경로: 선릉역 저지대 도로 통제 예정. 15분 뒤 시간당 60밀리미터 비 예보. 안전 운전하세요.",
            GuidanceVoice.briefingSentence(b),
        )
    }
}
