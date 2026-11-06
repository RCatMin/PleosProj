package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import java.net.HttpURLConnection
import java.net.URL

/** 경로를 계산해 주는 곳. 테스트에서는 가짜로 바꾼다 */
fun interface RouteSource {
    /**
     * [points](출발지, 경유지…, 목적지)를 지나는 경로. [alternatives]면 대안 경로도 함께 받는다.
     * [startBearing]은 출발할 때 달리는 방향(도). 반대 차로에 붙어 유턴부터 하는 경로를 막는다.
     */
    fun route(points: List<LatLng>, alternatives: Boolean, startBearing: Double?): List<RoutePlan>
}

/**
 * OSRM 공개 서버 연결 (ADR 0007). 공개 서버 이용 수칙에 따라 요청을 1초에 한 번 이하로 보내고,
 * 같은 요청은 10분 동안 다시 보내지 않는다. 네트워크 스레드에서 부른다.
 */
class OsrmClient(private val baseUrl: String = "https://router.project-osrm.org") : RouteSource {

    private val cache = object : LinkedHashMap<String, Pair<Long, List<RoutePlan>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<RoutePlan>>>?) = size > 30
    }
    private var lastRequestAt = 0L

    override fun route(points: List<LatLng>, alternatives: Boolean, startBearing: Double?): List<RoutePlan> {
        require(points.size >= 2) { "출발지와 목적지가 있어야 한다" }
        val coords = points.joinToString(";") { "%.6f,%.6f".format(it.lng, it.lat) }
        val bearings = startBearing?.let { b -> "&bearings=${Math.floorMod(b.toInt(), 360)},60" + ";".repeat(points.size - 1) }.orEmpty()
        val url = "$baseUrl/route/v1/driving/$coords?overview=full&geometries=geojson&steps=true" +
            "&alternatives=${if (alternatives) 3 else "false"}$bearings"

        synchronized(this) {
            cache[url]?.let { (at, routes) -> if (System.currentTimeMillis() - at < 10 * 60_000) return routes }
            val wait = 1_000 - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) Thread.sleep(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        val routes = OsrmParser.parse(get(url), idPrefix = "r${url.hashCode().toUInt().toString(36)}-")
        synchronized(this) { cache[url] = System.currentTimeMillis() to routes }
        return routes
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "SafeRoute/0.1 (Pleos app contest)")
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            // 400대 응답도 OSRM 오류 JSON이면 해석기에서 이유를 알려 준다
            if (code !in 200..299 && !body.trimStart().startsWith("{")) throw OsrmParser.OsrmException("OSRM HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
