package com.example.pleosproj.navi

import com.example.pleosproj.ui.state.TurnInstruction
import com.example.pleosproj.ui.state.TurnKind
import kotlin.math.roundToInt

/**
 * 회전 안내를 언제 말할지 정한다. 다음 회전까지 1km, 300m, 바로 앞(50m)에 들어설 때 한 번씩 말한다.
 * 회전 안내가 처음 보일 때 이미 가까우면 그 거리 기준으로 한 번 말하고, 지나간 기준은 건너뛴다.
 */
class TurnSpeech(private val thresholds: List<Int> = listOf(1000, 300, 50)) {

    private var key: String? = null
    private var lastDistance = Int.MAX_VALUE
    private val spoken = mutableSetOf<Int>()

    fun next(turn: TurnInstruction?): Utterance? {
        if (turn == null) {
            key = null
            return null
        }
        val k = "${turn.kind}|${turn.text}"
        // 이름이 같은 회전이 이어질 때(예: "숭례문 방면" 좌회전 두 번)는 거리가 다시 늘어난 것으로 새 회전을 알아챈다
        if (k != key || turn.distanceMeters > lastDistance + 100) {
            key = k
            spoken.clear()
        }
        lastDistance = turn.distanceMeters
        val due = thresholds.filter { turn.distanceMeters <= it && it !in spoken }.minOrNull() ?: return null
        // 이번 기준과 그보다 먼 기준은 모두 말한 것으로 본다
        spoken += thresholds.filter { it >= due }
        // 기준 거리를 막 지날 때는 기준 거리로("300미터 앞"), 처음 볼 때 이미 훨씬 가까우면 실제 거리로 말한다
        val say = if (turn.distanceMeters >= due * 0.8) due else turn.distanceMeters
        return Utterance(sentence(turn.copy(distanceMeters = say)), flush = false)
    }

    companion object {
        /** 예: "300미터 앞에서 대왕판교IC 방면으로 우회전입니다." / "곧 봉은사로로 우회전입니다." */
        fun sentence(t: TurnInstruction): String {
            val near = t.distanceMeters <= 60
            if (t.kind == TurnKind.ARRIVE) {
                return if (near) "곧 목적지에 도착합니다." else "${distance(t.distanceMeters)} 앞에 목적지가 있습니다."
            }
            val where = if (near) "곧" else "${distance(t.distanceMeters)} 앞에서"
            val text = t.text.trim()
            val action = action(t.kind)
            return when {
                text.isEmpty() -> "$where ${action ?: "안내 지점"}입니다."
                // "봉은사로로 우회전"처럼 동작이 이미 들어 있는 문구
                action == null || text.endsWith(action) -> "$where ${text}입니다."
                else -> "$where ${text}${toward(text)} ${action}입니다."
            }
        }

        private fun action(kind: TurnKind): String? = when (kind) {
            TurnKind.LEFT -> "좌회전"
            TurnKind.RIGHT -> "우회전"
            TurnKind.SHARP_LEFT -> "급좌회전"
            TurnKind.SHARP_RIGHT -> "급우회전"
            TurnKind.KEEP_LEFT -> "왼쪽 방향"
            TurnKind.KEEP_RIGHT -> "오른쪽 방향"
            TurnKind.STRAIGHT -> "직진"
            TurnKind.U_TURN -> "유턴"
            TurnKind.ENTER_HIGHWAY -> "진입"
            TurnKind.EXIT_HIGHWAY -> "진출"
            // 요금소·터널은 이름만 말한다
            TurnKind.TOLLGATE, TurnKind.TUNNEL, TurnKind.ARRIVE, TurnKind.UNKNOWN -> null
        }

        /** 1000m 이상은 킬로미터로 읽는다 */
        internal fun distance(m: Int): String = when {
            m >= 1000 -> {
                val km = (m / 100.0).roundToInt() / 10.0
                (if (km % 1.0 == 0.0) km.toInt().toString() else km.toString()) + "킬로미터"
            }
            else -> "${((m / 100.0).roundToInt() * 100).coerceAtLeast(100)}미터"
        }

        /** 받침이 있으면 "으로", 없거나 ㄹ 받침·한글이 아니면 "로" */
        private fun toward(text: String): String {
            val c = text.last()
            if (c !in '가'..'힣') return "로"
            val batchim = (c.code - 0xAC00) % 28
            return if (batchim == 0 || batchim == 8) "로" else "으로"
        }
    }
}
