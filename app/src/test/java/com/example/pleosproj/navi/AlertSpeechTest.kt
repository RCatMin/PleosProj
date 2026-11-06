package com.example.pleosproj.navi

import com.example.pleosproj.ui.state.HazardAlert
import com.example.pleosproj.ui.state.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSpeechTest {

    private val underpass = HazardAlert("underpass-1", "앞쪽 침수 우려", "800m 앞 지하차도", RiskLevel.MEDIUM, suggestsDetour = false)

    @Test
    fun `새 알림은 한 번만 말한다`() {
        val s = AlertSpeech()
        assertNotNull(s.next(underpass))
        // 1초 뒤 거리만 줄어든 같은 알림
        assertNull(s.next(underpass.copy(message = "700m 앞 지하차도")))
        assertNull(s.next(underpass))
    }

    @Test
    fun `사라졌다 다시 생기면 다시 말한다`() {
        val s = AlertSpeech()
        s.next(underpass)
        s.next(null)
        assertNotNull(s.next(underpass))
    }

    @Test
    fun `같은 알림이 더 위험해지면 다시 말하고 끊고 말한다`() {
        val s = AlertSpeech()
        s.next(underpass)
        val u = s.next(underpass.copy(level = RiskLevel.HIGH))!!
        assertTrue(u.flush)
    }

    @Test
    fun `다른 알림으로 바뀌면 말한다`() {
        val s = AlertSpeech()
        s.next(underpass)
        val closure = HazardAlert("its-0", "앞쪽 도로 통제", "900m 앞 도로 통제, 우회할까요?", RiskLevel.HIGH, suggestsDetour = true)
        val u = s.next(closure)!!
        assertEquals("900미터 앞 도로 구간이 통제되어 있습니다. 우회할까요?", u.text)
        assertTrue(u.flush)
    }

    @Test
    fun `보통 위험은 끊지 않고 이어서 말한다`() {
        assertFalse(AlertSpeech().next(underpass)!!.flush)
    }

    @Test
    fun `운전자에게 말하듯 존댓말 문장으로 바꾼다`() {
        assertEquals("800미터 앞 지하차도에 침수 우려가 있습니다. 속도를 줄여 주세요.", AlertSpeech.sentence(underpass))
        val rain = HazardAlert("weather-here", "지금 위치 강한 비", "시간당 45mm, 특보(경보) 발표 중. 속도를 줄이세요", RiskLevel.MEDIUM, false)
        assertEquals("지금 시간당 45밀리미터의 강한 비가 내리고 있습니다. 속도를 줄여 주세요.", AlertSpeech.sentence(rain))
        val ice = HazardAlert("caution-c1", "앞쪽 미끄럼 주의", "600m 앞 미끄러운 도로", RiskLevel.MEDIUM, false)
        assertEquals("600미터 앞 미끄러운 도로입니다. 길이 미끄러우니 천천히 가 주세요.", AlertSpeech.sentence(ice))
    }
}
