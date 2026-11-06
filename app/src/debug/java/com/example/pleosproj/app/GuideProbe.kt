package com.example.pleosproj.app

import android.content.Context
import android.util.Log
import com.example.pleosproj.routing.GuidanceSession
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.Place
import org.json.JSONArray
import java.io.File

/**
 * 디버그 전용 확인 도구 (PLE-37): 화면 없이 우리 안내 엔진을 돌린다.
 * `guide_start`: 지금 위치에서 목적지(`--es to 위도,경도 --es name 이름`, 기본 삼성역)까지 경로를 계산하고
 * 추천 경로로 안내를 시작한다. 고른 경로선은 앱 파일 `files/route.json`에 남겨
 * `scripts/replay_route.py`가 그 길을 따라 에뮬레이터 위치를 움직이게 한다.
 * `adb logcat -s GuideProbe GuidanceSession Routing`으로 본다.
 */
object GuideProbe {

    private const val TAG = "GuideProbe"
    private var session: GuidanceSession? = null
    private var count = 0

    val commands = setOf("guide_start", "guide_stop", "guide_detour", "guide_voice_off")

    fun run(context: Context, cmd: String, to: String?, name: String?) {
        Log.i(TAG, ">> $cmd")
        when (cmd) {
            "guide_stop" -> { session?.stop(); session = null }
            "guide_voice_off" -> session?.voiceEnabled = false
            "guide_detour" -> lastAlert?.let { session?.acceptDetour(it) }
            "guide_start" -> {
                session?.stop()
                count = 0
                val s = GuidanceSession(context, onState = ::log, onEvent = { Log.i(TAG, "<< 이벤트 $it") }).also { session = it; it.start() }
                val (lat, lng) = to?.split(",")?.map { it.toDouble() } ?: listOf(37.508844, 127.063160)
                val place = Place("probe", name ?: "삼성역", "", GeoPoint(lat, lng), null)
                // 위치를 받을 시간을 조금 준다
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    s.plan(place) { result ->
                        result.onSuccess { r ->
                            r.compare.candidates.forEach { c -> Log.i(TAG, "<< 후보 ${c.id} ${c.name} ${c.distanceMeters}m 위험 ${c.riskLevel} 제외=${c.excluded}") }
                            val pick = r.compare.recommendedId ?: r.routes.first().id
                            val route = r.routes.first { it.id == pick }
                            File(context.filesDir, "route.json").writeText(JSONArray(route.line.map { JSONArray(listOf(it.lat, it.lng)) }).toString())
                            Log.i(TAG, "<< 안내 시작 $pick ${route.name} 경로선 ${route.line.size}점, ${route.durationSeconds}초")
                            s.startGuidance(pick)
                        }.onFailure { Log.e(TAG, "<< 경로 계산 실패: ${it.message}") }
                    }
                }, 2_000)
            }
        }
    }

    private var lastAlert: String? = null

    private fun log(state: GuidanceState?) {
        lastAlert = state?.alert?.takeIf { it.suggestsDetour }?.hazardId
        if (count++ % 5 != 0) return
        if (state == null) return Log.i(TAG, "<< 안내 없음").let { }
        Log.i(TAG, "<< 남은 ${state.remainingMeters}m 진행 %.1f%% 다음 ${state.nextTurn?.kind} ${state.nextTurn?.distanceMeters}m ${state.nextTurn?.text} 알림 ${state.alert?.title}".format(state.progress.currentRatio * 100))
    }
}
