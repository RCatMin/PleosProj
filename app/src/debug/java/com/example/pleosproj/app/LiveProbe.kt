package com.example.pleosproj.app

import android.content.Context
import android.util.Log
import com.example.pleosproj.navi.LiveSession
import com.example.pleosproj.ui.state.GuidanceState

/**
 * 디버그 전용 확인 도구 (PLE-29): 실제 주행 세션이 만든 안내 화면 상태를 5초마다 로그로 남긴다.
 * `--es rain 45`를 주면 지금 위치에 시간당 45mm 비가 온다고 보고, `--es voice off`면 음성을 끈다.
 * `adb logcat -s LiveProbe LiveSession NaviSource Voices`로 본다.
 */
object LiveProbe {

    private const val TAG = "LiveProbe"
    private var session: LiveSession? = null
    private var count = 0

    val commands = setOf("live_start", "live_stop")

    fun run(context: Context, cmd: String, rain: String? = null, voice: String? = null) {
        Log.i(TAG, ">> $cmd")
        session?.stop()
        session = null
        if (cmd == "live_start") {
            count = 0
            session = LiveSession(context) { state -> if (count++ % 5 == 0) log(state) }.also {
                it.simulatedRainMm = rain?.toDoubleOrNull()
                it.voiceEnabled = voice != "off"
                it.start()
            }
        }
    }

    private fun log(s: GuidanceState?) {
        if (s == null) return Log.i(TAG, "<< 기본 내비 안내 없음").let { }
        Log.i(TAG, "<< ${s.destinationName} 남은 ${s.remainingMeters}m ${s.remainingSeconds}s 도착 ${s.arrivalTime} 진행 %.1f%%".format(s.progress.currentRatio * 100))
        Log.i(TAG, "<< 다음 ${s.nextTurn} / 이어서 ${s.upcomingTurns.map { "${it.kind} ${it.distanceMeters}m" }}")
        Log.i(TAG, "<< 막대 ${s.progress.segments.groupingBy { it.kind }.eachCount()} 위험 ${s.hazards.map { "${it.title}(${it.level})" }}")
        Log.i(TAG, "<< 날씨 ${s.weather.map { "${it.label} ${it.temperatureC}℃ ${it.rainMmPerHour}mm" }} 알림 ${s.alert?.let { "${it.title}: ${it.message}" }}")
    }
}
