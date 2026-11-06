package com.example.pleosproj.navi

import com.example.pleosproj.ui.state.HazardAlert
import com.example.pleosproj.ui.state.RiskLevel

/** 음성으로 말할 문장. [flush]가 true면 말하던 것을 끊고 바로 말한다 */
data class Utterance(val text: String, val flush: Boolean)

/**
 * 실제 주행 위험 알림 중 무엇을 언제 말할지 정한다. 1초마다 같은 알림이 와도 한 번만 말하고,
 * 알림이 사라졌다 다시 생기거나 더 위험해지면 다시 말한다.
 */
class AlertSpeech {

    private var lastId: String? = null
    private var lastLevel: RiskLevel? = null

    /** 이번 알림에서 말할 문장. 말할 게 없으면 null */
    fun next(alert: HazardAlert?): Utterance? {
        if (alert == null) {
            lastId = null
            lastLevel = null
            return null
        }
        val sameAlert = alert.hazardId == lastId
        val worse = lastLevel?.let { alert.level > it } ?: true
        lastId = alert.hazardId
        lastLevel = alert.level
        if (sameAlert && !worse) return null
        return Utterance(sentence(alert), flush = alert.level == RiskLevel.HIGH || alert.suggestsDetour)
    }

    companion object {
        /** 화면 문구를 읽기 좋게 바꾼다. "800m 앞" → "800미터 앞", "45mm" → "45밀리미터" */
        fun sentence(alert: HazardAlert): String {
            val message = alert.message
                .replace(Regex("(\\d+)mm")) { "${it.groupValues[1]}밀리미터" }
                .replace(Regex("(\\d+)m(?![a-z])")) { "${it.groupValues[1]}미터" }
            return "${alert.title}. $message"
        }
    }
}
