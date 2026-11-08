package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 카카오 로컬 좌표→행정구역 변환. 결빙 사고 다발지역을 시·군·구 단위로 받을 때 쓴다. 메인 스레드에서 부르지 않는다 */
class KakaoRegion(private val restKey: String, private val baseUrl: String = "https://dapi.kakao.com") {

    /** [p]가 있는 시·군·구의 법정동 코드 앞 5자리 (예: 서울 강남구 11680). 바다 등 행정구역이 없으면 null */
    fun sigunguAt(p: LatLng): String? {
        val conn = URL("$baseUrl/v2/local/geo/coord2regioncode.json?x=${p.lng}&y=${p.lat}").openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "KakaoAK $restKey")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return parse(body)
    }

    companion object {
        /** 법정동(B) 코드를 쓴다. 행정동(H) 코드는 다발지역 자료의 구 코드와 다를 수 있다 */
        fun parse(json: String): String? {
            val docs = JSONObject(json).optJSONArray("documents") ?: return null
            return (0 until docs.length()).map { docs.getJSONObject(it) }
                .firstOrNull { it.optString("region_type") == "B" }?.optString("code")?.takeIf { it.length >= 5 }?.take(5)
        }
    }
}
