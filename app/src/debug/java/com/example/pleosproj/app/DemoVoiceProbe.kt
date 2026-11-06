package com.example.pleosproj.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.pleosproj.navi.GuidanceVoice
import com.example.pleosproj.scenario.ScenarioPlayer

/**
 * 디버그 전용 확인 도구 (PLE-31): 시연 시나리오를 화면 없이 재생하며 안내 음성만 들어 본다.
 * 브리핑을 먼저 읽고, 재생하면서 회전 안내와 위험 경고를 말한다. `--es speed 6`으로 배속을 정한다.
 */
object DemoVoiceProbe {

    private const val TAG = "DemoVoice"
    private var player: ScenarioPlayer? = null
    private var voice: GuidanceVoice? = null

    val commands = setOf("demo_voice", "demo_voice_stop")

    fun run(context: Context, cmd: String, speed: String?) {
        Log.i(TAG, ">> $cmd")
        player?.stop()
        voice?.release()
        player = null
        voice = null
        if (cmd != "demo_voice") return
        val scenario = ScenarioPlayer.load(context, "rain-commute")
        val v = GuidanceVoice(context).also { voice = it }
        val p = ScenarioPlayer(scenario, speed?.toDoubleOrNull() ?: 4.0) { seconds, state ->
            v.onState(state)
            if (seconds.toInt() % 30 == 0) Log.i(TAG, "<< %.0f초 다음 %s %dm".format(seconds, state.nextTurn?.text, state.nextTurn?.distanceMeters))
        }.also { player = it }
        // 음성 준비를 기다렸다가 브리핑을 읽고, 다 읽을 즈음 재생한다
        val main = Handler(Looper.getMainLooper())
        main.postDelayed({ v.speakBriefing(p.engine.briefing()) }, 2_000)
        main.postDelayed({ p.play() }, 14_000)
    }
}
