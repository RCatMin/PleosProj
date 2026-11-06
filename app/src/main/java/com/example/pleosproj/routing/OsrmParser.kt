package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.ui.state.TurnKind
import org.json.JSONArray
import org.json.JSONObject

/** OSRM `route` 응답(geometries=geojson, steps=true)을 [RoutePlan]으로 바꾼다 */
object OsrmParser {

    class OsrmException(message: String) : Exception(message)

    fun parse(json: String, idPrefix: String = "r"): List<RoutePlan> {
        val root = JSONObject(json)
        val code = root.optString("code")
        if (code == "NoRoute") return emptyList()
        if (code != "Ok") throw OsrmException("OSRM 오류: $code ${root.optString("message")}")
        val routes = root.getJSONArray("routes")
        return (0 until routes.length()).map { route(routes.getJSONObject(it), "$idPrefix$it") }
    }

    private fun route(r: JSONObject, id: String): RoutePlan {
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        // geojson은 [경도, 위도] 순서다
        val line = (0 until coords.length()).map { coords.getJSONArray(it).let { p -> LatLng(p.getDouble(1), p.getDouble(0)) } }
        val steps = r.getJSONArray("legs").flatSteps()
        val distance = r.getDouble("distance")
        // 단계 거리 합과 경로선 길이가 조금 달라서, 회전 위치는 경로선 길이에 맞춰 늘이거나 줄인다
        val scale = RouteGeometry.lengthMeters(line) / distance.coerceAtLeast(1.0)

        val turns = mutableListOf<RouteTurn>()
        var walked = 0.0
        steps.forEachIndexed { i, s ->
            val m = s.getJSONObject("maneuver")
            val kind = kind(m.optString("type"), m.optString("modifier", ""))
            if (kind != null) {
                val name = s.optString("name").ifBlank { nextName(steps, i) }
                turns += RouteTurn(walked * scale, kind, text(kind, name))
            }
            walked += s.getDouble("distance")
        }
        val total = RouteGeometry.lengthMeters(line)
        // 목적지가 길 건너편이라 도착 직전에 생기는 유턴은 안내하지 않는다
        turns.removeAll { it.kind == TurnKind.U_TURN && total - it.atMeters < 200 }
        turns += RouteTurn(total, TurnKind.ARRIVE, "목적지")
        return RoutePlan(
            id = id,
            name = mainRoad(steps)?.let { "$it 경유" } ?: "추천 경로",
            distanceMeters = distance.toInt(),
            durationSeconds = r.getDouble("duration").toInt(),
            line = line,
            turns = turns,
            roads = steps.filter { it.optString("name").isNotBlank() }
                .groupBy { it.optString("name") }
                .mapValues { (_, s) -> s.sumOf { it.getDouble("distance") } },
        )
    }

    private fun JSONArray.flatSteps(): List<JSONObject> =
        (0 until length()).flatMap { leg -> getJSONObject(leg).getJSONArray("steps").let { s -> (0 until s.length()).map(s::getJSONObject) } }

    /** 안내할 동작만 고른다. 직진·이름만 바뀌는 길·출발·경유지 도착은 안내하지 않는다 */
    internal fun kind(type: String, modifier: String): TurnKind? = when (type) {
        "depart", "arrive", "new name", "notification" -> null
        "on ramp" -> TurnKind.ENTER_HIGHWAY
        "off ramp" -> TurnKind.EXIT_HIGHWAY
        "turn", "end of road", "fork", "merge", "continue", "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
            when (modifier) {
                "left" -> TurnKind.LEFT
                "right" -> TurnKind.RIGHT
                "slight left" -> TurnKind.KEEP_LEFT
                "slight right" -> TurnKind.KEEP_RIGHT
                "sharp left" -> TurnKind.SHARP_LEFT
                "sharp right" -> TurnKind.SHARP_RIGHT
                "uturn" -> TurnKind.U_TURN
                else -> null
            }
        else -> null
    }

    /** 기본 내비와 같은 모양의 안내 문구. 예: "언주로 방면" */
    private fun text(kind: TurnKind, name: String): String = when {
        name.isBlank() -> ""
        kind == TurnKind.U_TURN -> name
        else -> "$name 방면"
    }

    /** 이름 없는 진입로 등은 이어지는 길 이름으로 안내한다 */
    private fun nextName(steps: List<JSONObject>, from: Int): String =
        steps.drop(from + 1).firstOrNull { it.optString("name").isNotBlank() }?.optString("name").orEmpty()

    /** 가장 오래 달리는 길 이름 */
    private fun mainRoad(steps: List<JSONObject>): String? =
        steps.filter { it.optString("name").isNotBlank() }
            .groupBy { it.optString("name") }
            .maxByOrNull { (_, s) -> s.sumOf { it.getDouble("distance") } }?.key
}
