package com.example.pleosproj.scenario

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.Vulnerability
import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.risk.WiperLevel
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.TurnKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** assets/scenarios 폴더의 JSON 파일을 [Scenario]로 읽는다. 형식은 docs/scenario-format.md 참고. */
object ScenarioParser {

    /**
     * `startTime`이 `"now"`인 시나리오는 [now]를 분 단위로 잘라 출발 시각으로 쓴다.
     * 시연하는 시각에 맞춰 도착 예정 시각이 나오게 하려는 것이다.
     */
    fun parse(json: String, now: LocalTime = LocalTime.now()): Scenario {
        val o = JSONObject(json)
        return Scenario(
            id = o.getString("id"),
            title = o.getString("title"),
            summary = o.optString("summary"),
            startTime = o.getString("startTime").let {
                if (it == "now") now.truncatedTo(ChronoUnit.MINUTES) else LocalTime.parse(it)
            },
            destinationName = o.getString("destinationName"),
            routes = o.getJSONArray("routes").map { route(it) },
            driveRouteId = o.getString("driveRouteId"),
            hazards = o.getJSONArray("hazards").map { hazard(it) },
            weather = o.getJSONArray("weather").map {
                WeatherKey(
                    atSeconds = it.getInt("atSeconds"),
                    rainMmPerHour = it.optDouble("rainMm", 0.0),
                    snowing = it.optBoolean("snow", false),
                    temperatureC = it.getDouble("tempC"),
                    warning = WarningLevel.valueOf(it.optString("warning", "NONE")),
                )
            }.sortedBy { it.atSeconds },
            vehicle = o.optJSONArray("vehicle")?.map {
                VehicleKey(it.getInt("atSeconds"), WiperLevel.valueOf(it.getString("wiper")))
            }?.sortedBy { it.atSeconds } ?: listOf(VehicleKey(0, WiperLevel.OFF)),
        )
    }

    private fun route(o: JSONObject) = ScenarioRoute(
        id = o.getString("id"),
        name = o.getString("name"),
        durationSeconds = o.getInt("durationSeconds"),
        tollWon = o.optInt("tollWon", 0),
        line = o.getJSONArray("line").let { arr ->
            (0 until arr.length()).map { arr.getJSONArray(it).let { p -> LatLng(p.getDouble(0), p.getDouble(1)) } }
        },
        turns = o.optJSONArray("turns")?.map {
            ScenarioTurn(it.getDouble("atMeters"), TurnKind.valueOf(it.getString("kind")), it.getString("text"))
        } ?: emptyList(),
    )

    private fun hazard(o: JSONObject) = ScenarioHazard(
        id = o.getString("id"),
        kind = HazardKind.valueOf(o.getString("kind")),
        position = LatLng(o.getDouble("lat"), o.getDouble("lng")),
        title = o.getString("title"),
        detail = o.optString("detail"),
        vulnerability = Vulnerability.valueOf(o.optString("vulnerability", "NONE")),
        icingZone = o.optBoolean("icingZone", false),
        statistical = o.optBoolean("statistical", false),
        closedFromSeconds = if (o.has("closedFromSeconds")) o.getInt("closedFromSeconds") else null,
    )

    private fun <T> JSONArray.map(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }
}
