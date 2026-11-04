package com.example.pleosproj.app

import android.content.Context
import android.util.Log
import com.example.pleosproj.location.LocationFix
import com.example.pleosproj.location.LocationListener
import com.example.pleosproj.location.Locations
import com.example.pleosproj.scenario.HazardVoiceAlerts
import com.example.pleosproj.scenario.ScenarioPlayer
import com.example.pleosproj.voice.VoiceInput
import com.example.pleosproj.voice.VoiceOutput
import com.example.pleosproj.voice.Voices

/**
 * 디버그 전용 확인 도구 (PLE-16): 위치·음성 구현 선택과 위험 지점 음성 경고를 확인한다.
 * `--es mode android`를 주면 Pleos SDK를 건너뛰고 안드로이드 기본 구현을 쓴다.
 * `adb logcat -s LocVoice Locations Voices HazardAlerts`로 본다.
 */
object LocationVoiceProbe {

    private const val TAG = "LocVoice"
    private var locations: Locations? = null
    private var voice: VoiceOutput? = null
    private var input: VoiceInput? = null
    private var alerts: HazardVoiceAlerts? = null

    val commands = setOf("loc_start", "loc_stop", "voice_say", "voice_listen", "hazard_watch", "hazard_stop")

    fun run(context: Context, cmd: String, mode: String?, text: String?) {
        Log.i(TAG, ">> $cmd ${mode.orEmpty()}")
        val pleos = mode != "android"
        runCatching {
            when (cmd) {
                "loc_start" -> {
                    stopAll()
                    locations = Locations(context, pleos).also { it.start(logger) }
                }
                "loc_stop", "hazard_stop" -> stopAll()
                "voice_say" -> withVoice(context, pleos) { it.speak(text ?: "전방 삼백 미터, 침수 위험 구간입니다.") }
                "voice_listen" -> Voices.input(context, pleos) { stt ->
                    input?.release()
                    input = stt
                    stt.listen(object : VoiceInput.Listener {
                        override fun onPartial(text: String) = Log.i(TAG, "<< 듣는 중: $text").let { }
                        override fun onResult(text: String) = Log.i(TAG, "<< 인식 결과: $text").let { }
                        override fun onError(reason: String) = Log.e(TAG, "<< 인식 실패: $reason").let { }
                    })
                }
                "hazard_watch" -> {
                    stopAll()
                    val scenario = ScenarioPlayer.load(context, "rain-commute")
                    withVoice(context, pleos) { v ->
                        val loc = Locations(context, pleos).also { locations = it }
                        alerts = HazardVoiceAlerts(loc, v, scenario.hazards, onLocation = ::logFix).also { it.start() }
                        Log.i(TAG, "<< 위험 지점 ${scenario.hazards.size}곳 감시 (${loc.name}, ${v.name})")
                    }
                }
            }
        }.onFailure { Log.e(TAG, "'$cmd' 실패", it) }
    }

    private fun withVoice(context: Context, pleos: Boolean, use: (VoiceOutput) -> Unit) {
        val v = voice
        if (v != null && (v.name.startsWith("Pleos") == pleos)) return use(v)
        v?.release()
        Voices.output(context, pleos) { voice = it; use(it) }
    }

    private var lastLog = 0L

    private fun logFix(fix: LocationFix) {
        // 1초마다 오므로 5초에 한 번만 남긴다
        if (fix.timeMillis - lastLog < 5_000) return
        lastLog = fix.timeMillis
        Log.i(TAG, "<< 위치 %.5f, %.5f".format(fix.lat, fix.lng))
    }

    private val logger = object : LocationListener {
        override fun onLocation(fix: LocationFix) = logFix(fix)
        override fun onFailure(error: Throwable) = Log.e(TAG, "<< 위치 실패: ${error.message}").let { }
    }

    private fun stopAll() {
        alerts?.stop()
        alerts = null
        locations?.release()
        locations = null
        lastLog = 0L
    }
}
