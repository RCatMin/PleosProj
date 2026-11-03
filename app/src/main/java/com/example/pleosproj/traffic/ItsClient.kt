package com.example.pleosproj.traffic

import java.net.HttpURLConnection
import java.net.URL

/** ITS 국가교통정보센터 돌발상황 API. 네트워크를 쓰므로 메인 스레드에서 부르지 않는다. */
class ItsClient(private val apiKey: String) {

    /** 사각형 범위 안의 돌발상황을 가져온다. */
    fun events(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List<TrafficEvent> {
        val url = "https://openapi.its.go.kr:9443/eventInfo?apiKey=$apiKey&type=all&eventType=all" +
            "&minX=$minLng&maxX=$maxLng&minY=$minLat&maxY=$maxLat&getType=json"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return ItsParser.parseEvents(body)
    }
}
