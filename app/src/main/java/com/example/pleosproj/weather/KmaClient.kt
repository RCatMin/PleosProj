package com.example.pleosproj.weather

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 기상청 API 호출. 키는 공공데이터포털의 인증키를 그대로 받는다.
 * local.properties에는 Encoding 형태(% 포함)로 저장되어 있어 다시 인코딩하지 않는다.
 * 네트워크를 쓰므로 메인 스레드에서 부르지 않는다.
 */
class KmaClient(private val serviceKey: String) {

    private val date = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val hour = DateTimeFormatter.ofPattern("HH")

    /** 초단기실황. 매시 정각 관측분이 40분쯤 뒤에 올라오므로 45분 전 시각을 기준으로 잡는다. */
    fun now(grid: KmaGridPoint, at: LocalDateTime = LocalDateTime.now()): WeatherNow {
        val base = at.minusMinutes(45)
        return KmaParser.parseNow(get(url(
            "VilageFcstInfoService_2.0/getUltraSrtNcst",
            "numOfRows" to "20",
            "base_date" to base.format(date),
            "base_time" to base.format(hour) + "00",
            "nx" to grid.nx.toString(),
            "ny" to grid.ny.toString(),
        )))
    }

    /** 초단기예보(6시간). 매시 30분 발표분이 45분쯤 뒤에 올라오므로 50분 전 시각을 기준으로 잡는다. */
    fun forecast(grid: KmaGridPoint, at: LocalDateTime = LocalDateTime.now()): List<WeatherForecast> {
        val base = at.minusMinutes(50)
        return KmaParser.parseForecast(get(url(
            "VilageFcstInfoService_2.0/getUltraSrtFcst",
            "numOfRows" to "60",
            "base_date" to base.format(date),
            "base_time" to base.format(hour) + "30",
            "nx" to grid.nx.toString(),
            "ny" to grid.ny.toString(),
        )))
    }

    /** 기상특보 목록. stnId 108은 전국(기상청 본청)이다. */
    fun warnings(from: LocalDateTime, to: LocalDateTime = LocalDateTime.now(), stnId: Int = 108): List<WeatherWarning> =
        KmaParser.parseWarnings(get(url(
            "WthrWrnInfoService/getWthrWrnList",
            "numOfRows" to "50",
            "stnId" to stnId.toString(),
            "fromTmFc" to from.format(date),
            "toTmFc" to to.format(date),
        )))

    private fun url(path: String, vararg params: Pair<String, String>): String =
        "https://apis.data.go.kr/1360000/$path?serviceKey=$serviceKey&pageNo=1&dataType=JSON" +
            params.joinToString("") { (k, v) -> "&$k=$v" }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
