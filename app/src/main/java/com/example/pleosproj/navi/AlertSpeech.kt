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
        private val ahead = Regex("""^(\d+)m 앞 ([^,]+)""")
        private val rain = Regex("""(\d+)mm""")

        /**
         * 화면 문구를 그대로 읽으면 딱딱하므로, 운전자에게 말하듯 끝까지 존댓말 문장으로 바꾼다.
         * 예: "앞쪽 침수 우려 / 800m 앞 지하차도" → "800미터 앞 지하차도에 침수 우려가 있습니다. 속도를 줄여 주세요."
         */
        fun sentence(alert: HazardAlert): String {
            val m = ahead.find(alert.message)
            val where = m?.let { "${TurnSpeech.distance(it.groupValues[1].toInt())} 앞 ${it.groupValues[2].trim()}" }
            return when {
                // "도로 통제", "테헤란로 통제"처럼 이름에 이미 "통제"가 붙어 있으면 떼고 말한다
                alert.suggestsDetour && m != null ->
                    "${TurnSpeech.distance(m.groupValues[1].toInt())} 앞 ${m.groupValues[2].trim().removeSuffix("통제").trim().ifEmpty { "도로" }} 구간이 통제되어 있습니다. 우회할까요?"
                where != null && alert.title.contains("침수") -> "${where}에 침수 우려가 있습니다. 속도를 줄여 주세요."
                // "300미터 앞 결빙 사고가 잦은 곳입니다. 속도를 줄여 주세요."
                where != null && alert.title.contains("결빙") -> "${where}입니다. 길이 얼었을 수 있으니 속도를 줄여 주세요."
                where != null && alert.title.contains("미끄럼") -> "${where}입니다. 길이 미끄러우니 천천히 가 주세요."
                where != null && alert.title.contains("안개") -> "${where}입니다. 앞차와 거리를 넉넉히 두세요."
                where != null -> "${where}입니다. 주의해서 지나가 주세요."
                alert.title.contains("눈") -> "지금 눈이 내리고 있습니다. 속도를 줄여 주세요."
                rain.containsMatchIn(alert.message) ->
                    "지금 시간당 ${rain.find(alert.message)!!.groupValues[1]}밀리미터의 강한 비가 내리고 있습니다. 속도를 줄여 주세요."
                else -> "${alert.title}입니다. 주의해 주세요."
            }
        }
    }
}
