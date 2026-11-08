package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 카카오모빌리티 길찾기 연결 (ADR 0008). 교통이 반영된 시간·통행료·교통 상태를 준다.
 * 대안 경로를 요청하면 추천·최단 시간·최단 거리 세 기준으로 받아 "추천 길 / 가장 빠른 길 / 가장 짧은 길"로 이름 붙인다.
 * 네트워크 스레드에서 부른다. 키는 카카오 개발자 REST 키(`KAKAO_REST_KEY`)를 쓴다.
 */
class KakaoNaviClient(private val restKey: String, private val baseUrl: String = "https://apis-navi.kakaomobility.com") : RouteSource {

    override fun route(points: List<LatLng>, alternatives: Boolean, startBearing: Double?): List<RoutePlan> {
        require(points.size >= 2) { "출발지와 목적지가 있어야 한다" }
        val priorities = if (alternatives) listOf("RECOMMEND" to "추천 길", "TIME" to "가장 빠른 길", "DISTANCE" to "가장 짧은 길")
        else listOf("RECOMMEND" to "추천 길")
        return priorities.flatMapIndexed { i, (priority, label) ->
            KakaoNaviParser.parse(get(points, priority, startBearing, alternatives && i == 0), label, idPrefix = "k$i-")
        }
    }

    private fun get(points: List<LatLng>, priority: String, bearing: Double?, alternatives: Boolean): String {
        fun xy(p: LatLng) = "%.6f,%.6f".format(p.lng, p.lat)
        val origin = xy(points.first()) + (bearing?.let { ",angle=${Math.floorMod(it.toInt(), 360)}" } ?: "")
        val params = buildList {
            add("origin" to origin)
            add("destination" to xy(points.last()))
            if (points.size > 2) add("waypoints" to points.subList(1, points.size - 1).joinToString("|") { xy(it) })
            add("priority" to priority)
            add("alternatives" to alternatives.toString())
            add("summary" to "false")
        }.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val conn = URL("$baseUrl/v1/directions?$params").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("Authorization", "KakaoAK $restKey")
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw KakaoNaviParser.KakaoNaviException(code, "카카오모빌리티 HTTP $code ${body.take(120)}")
            return body
        } finally {
            conn.disconnect()
        }
    }
}

/** 먼저 [primary]로 계산하고, 실패하면 [fallback]으로 계산한다. 대체 여부는 [RoutePlan.provider]로 알 수 있다 */
class FallbackRouteSource(private val primary: RouteSource, private val fallback: RouteSource) : RouteSource {
    override fun route(points: List<LatLng>, alternatives: Boolean, startBearing: Double?): List<RoutePlan> =
        try {
            primary.route(points, alternatives, startBearing).ifEmpty { fallback.route(points, alternatives, startBearing) }
        } catch (e: Exception) {
            runCatching { android.util.Log.w("Routing", "카카오모빌리티 실패, OSRM으로 계산: ${e.message}") }
            fallback.route(points, alternatives, startBearing).map { it.copy(provider = "osrm") }
        }
}
