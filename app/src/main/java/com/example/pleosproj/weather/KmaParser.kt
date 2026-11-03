package com.example.pleosproj.weather

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 기상청 API 응답(JSON)을 모델로 바꾼다. 네트워크와 분리해서 저장된 응답으로 테스트한다. */
object KmaParser {

    class ApiException(val code: String, message: String) : Exception("$code $message")

    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val timeFmt = DateTimeFormatter.ofPattern("HHmm")

    fun parseNow(json: String): WeatherNow {
        val items = items(json)
        val values = (0 until items.length()).associate {
            val o = items.getJSONObject(it); o.getString("category") to o.get("obsrValue").toString()
        }
        val first = items.getJSONObject(0)
        return WeatherNow(
            grid = KmaGridPoint(first.getInt("nx"), first.getInt("ny")),
            observedAt = dateTime(first.getString("baseDate"), first.getString("baseTime")),
            temperatureC = values["T1H"]?.toDoubleOrNull() ?: Double.NaN,
            rainMm = rainMm(values["RN1"]),
            precip = precip(values["PTY"]),
            humidity = values["REH"]?.toDoubleOrNull()?.toInt() ?: 0,
            windSpeed = values["WSD"]?.toDoubleOrNull() ?: 0.0,
        )
    }

    fun parseForecast(json: String): List<WeatherForecast> {
        val items = items(json)
        // 같은 시각의 여러 항목(category)을 한 칸으로 묶는다
        val byTime = linkedMapOf<Pair<String, String>, MutableMap<String, String>>()
        var grid = KmaGridPoint(0, 0)
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            grid = KmaGridPoint(o.getInt("nx"), o.getInt("ny"))
            byTime.getOrPut(o.getString("fcstDate") to o.getString("fcstTime")) { mutableMapOf() }[o.getString("category")] =
                o.get("fcstValue").toString()
        }
        return byTime.map { (key, v) ->
            WeatherForecast(
                grid = grid,
                time = dateTime(key.first, key.second),
                temperatureC = v["T1H"]?.toDoubleOrNull() ?: Double.NaN,
                rainMm = rainMm(v["RN1"]),
                precip = precip(v["PTY"]),
                sky = v["SKY"]?.toIntOrNull() ?: 0,
                lightning = v["LGT"]?.toDoubleOrNull() ?: 0.0,
            )
        }.sortedBy { it.time }
    }

    private val warningTitle = Regex("""/\s*(\S+?)(주의보|경보)\s*(발표|해제|변경)""")
    private val warningTime = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    fun parseWarnings(json: String): List<WeatherWarning> {
        val items = items(json, allowEmpty = true) ?: return emptyList()
        return (0 until items.length()).map {
            val o = items.getJSONObject(it)
            val title = o.getString("title")
            val m = warningTitle.find(title)
            WeatherWarning(
                issuedAt = LocalDateTime.parse(o.get("tmFc").toString(), warningTime),
                title = title,
                kind = m?.groupValues?.get(1),
                level = m?.groupValues?.get(2),
                active = m?.groupValues?.get(3) != "해제",
            )
        }
    }

    /**
     * RN1 값을 mm로 바꾼다. 실황은 숫자("0", "2.5")로, 예보는 "강수없음", "1mm 미만",
     * "30.0~50.0mm", "50.0mm 이상" 같은 문자열로 온다. 범위는 아래쪽 값을 쓴다.
     */
    fun rainMm(raw: String?): Double {
        if (raw.isNullOrBlank() || raw == "강수없음") return 0.0
        if (raw.contains("미만")) return 0.5
        return Regex("""[\d.]+""").find(raw)?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun precip(raw: String?): PrecipType = when (raw?.toIntOrNull()) {
        1 -> PrecipType.RAIN
        2 -> PrecipType.RAIN_SNOW
        3 -> PrecipType.SNOW
        4 -> PrecipType.SHOWER
        5 -> PrecipType.DRIZZLE
        6 -> PrecipType.DRIZZLE_SNOW
        7 -> PrecipType.SNOW_FLURRY
        else -> PrecipType.NONE
    }

    private fun dateTime(date: String, time: String): LocalDateTime =
        LocalDateTime.of(LocalDate.parse(date, dateFmt), LocalTime.parse(time.padStart(4, '0'), timeFmt))

    private fun items(json: String): JSONArray = items(json, allowEmpty = false)!!

    private fun items(json: String, allowEmpty: Boolean): JSONArray? {
        val response = JSONObject(json).getJSONObject("response")
        val header = response.getJSONObject("header")
        val code = header.getString("resultCode")
        // 03: 데이터 없음 (특보가 없는 기간 등)
        if (code == "03" && allowEmpty) return null
        if (code != "00") throw ApiException(code, header.optString("resultMsg"))
        return response.getJSONObject("body").getJSONObject("items").getJSONArray("item")
    }
}
