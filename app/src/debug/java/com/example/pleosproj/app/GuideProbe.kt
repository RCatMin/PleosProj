package com.example.pleosproj.app

import android.content.Context
import android.util.Log
import com.example.pleosproj.BuildConfig
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.routing.FallbackRouteSource
import com.example.pleosproj.routing.GuidanceSession
import com.example.pleosproj.routing.KakaoNaviClient
import com.example.pleosproj.routing.OsrmClient
import com.example.pleosproj.routing.RouteSource
import com.example.pleosproj.routing.Routing
import com.example.pleosproj.routing.TestClosure
import com.example.pleosproj.traffic.TrafficEvent
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
 * `guide_closure`: `guide_start`와 같지만, 추천 경로 40% 지점에 시험용 전면 통제를 넣고 그 경로로 안내한다.
 * 통제 앞에서 "우회할까요?" 알림이 뜨면 `guide_detour`로 우회를 확인한다 (실제 ITS 통제가 경로 위에 있을 일이 드물어서).
 * `closure_ahead`: 화면에서 시작한 안내 중에 지금 위치 앞(`--es to 미터`, 기본 1500m)에 시험용 통제를 넣는다.
 * `adb logcat -s GuideProbe GuidanceSession Routing`으로 본다.
 */
object GuideProbe {

    private const val TAG = "GuideProbe"
    private var session: GuidanceSession? = null
    private var count = 0

    val commands = setOf("guide_start", "guide_closure", "guide_stop", "guide_detour", "guide_voice_off", "closure_ahead")

    fun run(context: Context, cmd: String, to: String?, name: String?) {
        Log.i(TAG, ">> $cmd")
        when (cmd) {
            "guide_stop" -> { session?.stop(); session = null }
            "guide_voice_off" -> session?.voiceEnabled = false
            "guide_detour" -> lastAlert?.let { session?.acceptDetour(it) }
            "guide_closure" -> closure(context, to, name)
            // 화면에서 시작한 안내에도 쓸 수 있다: 지금 위치에서 `--es to 1500`m 앞에 시험용 통제를 둔다
            "closure_ahead" -> TestClosure.aheadMeters = to?.toDoubleOrNull() ?: 1500.0
            "guide_start" -> {
                session?.stop()
                count = 0
                val s = GuidanceSession(context, onState = ::log, onEvent = { Log.i(TAG, "<< 이벤트 $it") }).also { session = it; it.start() }
                val place = place(to, name)
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

    private fun place(to: String?, name: String?): Place {
        val (lat, lng) = to?.split(",")?.map { it.toDouble() } ?: listOf(37.508844, 127.063160)
        return Place("probe", name ?: "삼성역", "", GeoPoint(lat, lng), null)
    }

    private fun routeSource(): RouteSource =
        BuildConfig.KAKAO_REST_KEY.takeIf { it.isNotBlank() }?.let { FallbackRouteSource(KakaoNaviClient(it), OsrmClient()) } ?: OsrmClient()

    /** 추천 경로를 먼저 받아 40% 지점에 시험용 통제를 두고, 그 통제를 지나는 경로로 안내를 시작한다 */
    private fun closure(context: Context, to: String?, name: String?) {
        session?.stop()
        count = 0
        val place = place(to, name)
        val source = routeSource()
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val first = GuidanceSession(context, onState = {}, routing = Routing(source, null, null, null)).also { it.start() }
        main.postDelayed({
            first.plan(place) { result ->
                first.stop()
                val r = result.getOrElse { return@plan Log.e(TAG, "<< 경로 계산 실패: ${it.message}").let { } }
                val base = r.routes.first { it.id == (r.compare.recommendedId ?: r.routes.first().id) }
                val at = RouteGeometry.pointAt(base.line, RouteGeometry.lengthMeters(base.line) * 0.4)
                Log.i(TAG, "<< 시험용 통제 ${at.lat},${at.lng} (${base.name} 40% 지점)")
                val event = TrafficEvent("일반도로", "통제", "전면통제", "시험 도로", at.lat, at.lng, null, null, "시험용 전면 통제", true)
                val routing = Routing(source, null, null, events = { _, _, _, _ -> listOf(event) })
                val s = GuidanceSession(context, onState = ::log, onEvent = { Log.i(TAG, "<< 이벤트 $it") }, routing = routing)
                    .also { session = it; it.start() }
                main.postDelayed({
                    s.plan(place) { second ->
                        second.onSuccess { p ->
                            p.compare.candidates.forEach { c -> Log.i(TAG, "<< 후보 ${c.id} ${c.name} 제외=${c.excluded} ${c.riskSummary}") }
                            // 통제를 지나는(후보에서 빠진) 경로로 일부러 안내한다
                            val pick = p.compare.candidates.firstOrNull { it.excluded }?.id ?: p.routes.first().id
                            val route = p.routes.first { it.id == pick }
                            File(context.filesDir, "route.json").writeText(JSONArray(route.line.map { JSONArray(listOf(it.lat, it.lng)) }).toString())
                            Log.i(TAG, "<< 통제 경로로 안내 시작 $pick ${route.name} 경로선 ${route.line.size}점")
                            s.startGuidance(pick)
                        }.onFailure { Log.e(TAG, "<< 경로 계산 실패: ${it.message}") }
                    }
                }, 2_000)
            }
        }, 2_000)
    }

    private var lastAlert: String? = null

    private fun log(state: GuidanceState?) {
        lastAlert = state?.alert?.takeIf { it.suggestsDetour }?.hazardId
        if (count++ % 5 != 0) return
        if (state == null) return Log.i(TAG, "<< 안내 없음").let { }
        Log.i(TAG, "<< 경로선 ${state.routeLine?.size}점 남은 ${state.remainingMeters}m 진행 %.1f%% 다음 ${state.nextTurn?.kind} ${state.nextTurn?.distanceMeters}m ${state.nextTurn?.text} 알림 ${state.alert?.title}".format(state.progress.currentRatio * 100))
    }
}
