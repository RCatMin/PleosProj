package com.example.pleosproj.app

import android.util.Log
import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.routing.Routing
import kotlin.concurrent.thread

/**
 * 디버그 전용 확인 도구 (PLE-36): 실제 OSRM·기상청·ITS로 후보 경로와 위험도를 계산해 로그로 본다.
 * 예: `--es cmd route_plan --es to 37.5088,127.0631 --es from 37.4986,127.0296`
 * `adb logcat -s RoutingProbe Routing`으로 본다.
 */
object RoutingProbe {

    private const val TAG = "RoutingProbe"
    val commands = setOf("route_plan")

    fun run(from: String?, to: String?, name: String?) {
        val start = parse(from) ?: LatLng(37.4986, 127.0296)
        val end = parse(to) ?: LatLng(37.5088, 127.0631)
        thread(name = "routing-probe") {
            val began = System.currentTimeMillis()
            runCatching { Routing.create().plan(start, end, name ?: "삼성역", VehicleSignals()) }
                .onSuccess { r ->
                    Log.i(TAG, "<< ${System.currentTimeMillis() - began}ms, 후보 ${r.routes.size}개, 날씨 누락=${r.weatherMissing}, 추천=${r.compare.recommendedId}")
                    r.compare.candidates.forEach { c ->
                        Log.i(TAG, "<< ${c.id} ${c.name} ${c.distanceMeters}m ${c.durationSeconds / 60}분 위험 %.2f(${c.riskLevel}) 제외=${c.excluded} \"${c.riskSummary}\" 위험지점 ${c.hazards.size}".format(c.riskScore))
                    }
                    r.routes.first().turns.take(5).forEach { Log.i(TAG, "<<   ${it.atMeters.toInt()}m ${it.kind} ${it.text}") }
                }
                .onFailure { Log.e(TAG, "<< 실패: ${it.message}", it) }
        }
    }

    private fun parse(s: String?) = s?.split(",")?.takeIf { it.size == 2 }?.let { LatLng(it[0].toDouble(), it[1].toDouble()) }
}
