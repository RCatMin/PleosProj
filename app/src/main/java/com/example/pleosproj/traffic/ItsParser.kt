package com.example.pleosproj.traffic

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** ITS 돌발상황 응답(JSON)을 모델로 바꾼다. */
object ItsParser {

    class ApiException(message: String) : Exception(message)

    private val time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parseEvents(json: String): List<TrafficEvent> {
        val root = JSONObject(json)
        val header = root.getJSONObject("header")
        if (header.optInt("resultCode", -1) != 0) throw ApiException(header.optString("resultMsg"))
        val items = root.optJSONObject("body")?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull {
            val o = items.getJSONObject(it)
            val lat = o.optString("coordY").toDoubleOrNull() ?: return@mapNotNull null
            val lng = o.optString("coordX").toDoubleOrNull() ?: return@mapNotNull null
            TrafficEvent(
                roadType = o.optString("type"),
                eventType = o.optString("eventType"),
                detailType = o.optString("eventDetailType"),
                roadName = o.optString("roadName"),
                lat = lat,
                lng = lng,
                start = parseTime(o.optString("startDate")),
                end = parseTime(o.optString("endDate")),
                message = o.optString("message").trim(),
                lanesBlocked = o.optString("lanesBlocked").isNotBlank(),
            )
        }
    }

    private fun parseTime(raw: String): LocalDateTime? =
        raw.takeIf { it.length == 14 }?.let { runCatching { LocalDateTime.parse(it, time) }.getOrNull() }
}
