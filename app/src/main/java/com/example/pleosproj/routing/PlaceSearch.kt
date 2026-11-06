package com.example.pleosproj.routing

import com.example.pleosproj.BuildConfig
import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.Place
import com.example.pleosproj.ui.state.SearchFailure
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 검색이 안 됐을 때. [reason]으로 화면 안내를 정한다 */
class PlaceSearchException(val reason: SearchFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 목적지 검색 (ADR 0007). 카카오 로컬 API 키워드 검색으로 장소 이름·주소·좌표·거리를 받는다.
 * 네트워크 스레드에서 부른다. 실패하면 [PlaceSearchException]을 던진다.
 */
class PlaceSearch(private val restKey: String, private val baseUrl: String = "https://dapi.kakao.com") {

    /** [near]가 있으면 그 위치에서의 거리를 함께 받는다 */
    fun search(query: String, near: LatLng? = null, size: Int = 15): List<Place> {
        if (restKey.isBlank()) throw PlaceSearchException(SearchFailure.KEY_MISSING, "검색 키가 없습니다")
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val params = buildString {
            append("query=").append(URLEncoder.encode(q, "UTF-8"))
            append("&size=").append(size.coerceIn(1, 15))
            near?.let { append("&x=").append(it.lng).append("&y=").append(it.lat) }
        }
        val (code, body) = get("$baseUrl/v2/local/search/keyword.json?$params")
        if (code !in 200..299) throw failure(code, body)
        return KakaoParser.places(body)
    }

    private fun get(url: String): Pair<Int, String> = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("Authorization", "KakaoAK $restKey")
        try {
            val code = conn.responseCode
            code to ((if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    } catch (e: java.io.IOException) {
        throw PlaceSearchException(SearchFailure.NETWORK, "검색 서버에 연결하지 못했습니다", e)
    }

    companion object {
        fun create() = PlaceSearch(BuildConfig.KAKAO_REST_KEY)

        /** HTTP 오류를 화면용 이유로 바꾼다 */
        internal fun failure(code: Int, body: String): PlaceSearchException {
            val message = runCatching { JSONObject(body).optString("message") }.getOrDefault("").ifBlank { "HTTP $code" }
            val reason = when (code) {
                // 401 키 거절, 403 카카오맵(로컬) 사용 설정 꺼짐
                401, 403 -> SearchFailure.NOT_ALLOWED
                429 -> SearchFailure.LIMIT
                else -> SearchFailure.NETWORK
            }
            return PlaceSearchException(reason, message)
        }
    }
}

/** 카카오 로컬 키워드 검색 응답 해석 */
internal object KakaoParser {

    fun places(json: String): List<Place> {
        val docs = JSONObject(json).optJSONArray("documents") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val d = docs.getJSONObject(i)
            val lat = d.optString("y").toDoubleOrNull() ?: return@mapNotNull null
            val lng = d.optString("x").toDoubleOrNull() ?: return@mapNotNull null
            Place(
                id = d.optString("id").ifBlank { "kakao-$i" },
                name = d.optString("place_name"),
                address = d.optString("road_address_name").ifBlank { d.optString("address_name") },
                position = GeoPoint(lat, lng),
                distanceMeters = d.optString("distance").toIntOrNull(),
                // "교통,수송 > 지하철,전철 > 수도권2호선"에서 가운데 분류를 쓴다
                category = d.optString("category_name").split(">").map { it.trim() }.let { it.getOrNull(1) ?: it.firstOrNull().orEmpty() },
            )
        }
    }
}
