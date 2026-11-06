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
        assertEquals("앞쪽 도로 통제. 900미터 앞 도로 통제, 우회할까요?", u.text)
        assertTrue(u.flush)
    }

    @Test
    fun `보통 위험은 끊지 않고 이어서 말한다`() {
        assertFalse(AlertSpeech().next(underpass)!!.flush)
    }

    @Test
    fun `단위를 읽기 좋게 바꾼다`() {
        val rain = HazardAlert("weather-here", "지금 위치 강한 비", "시간당 45mm, 와이퍼 최고 단계. 속도를 줄이세요", RiskLevel.MEDIUM, false)
        assertEquals("지금 위치 강한 비. 시간당 45밀리미터, 와이퍼 최고 단계. 속도를 줄이세요", AlertSpeech.sentence(rain))
        assertEquals("앞쪽 침수 우려. 800미터 앞 지하차도", AlertSpeech.sentence(underpass))
    }
}
