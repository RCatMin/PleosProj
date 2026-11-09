package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.Vulnerability
import com.example.pleosproj.ui.state.FacilityKind
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.TurnKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * 카카오모빌리티 길찾기(`/v1/directions`) 응답을 [RoutePlan]으로 바꾼다 (ADR 0008).
 * 경로선은 도로(roads)의 꼭짓점을 이어 만들고, 회전 안내(guides)·통행료·교통 상태·지하차도 진입을 함께 읽는다.
 */
object KakaoNaviParser {

    class KakaoNaviException(val code: Int, message: String) : Exception(message)

    fun parse(json: String, label: String, idPrefix: String = "k"): List<RoutePlan> {
        val root = JSONObject(json)
        val routes = root.optJSONArray("routes") ?: throw KakaoNaviException(-1, root.optString("msg", "응답 형식 오류"))
        return (0 until routes.length()).mapNotNull { i ->
            val r = routes.getJSONObject(i)
            // 0이 성공. 출발지·도착지가 너무 가깝거나 길이 없으면 다른 코드가 온다
            if (r.optInt("result_code", -1) != 0) return@mapNotNull null
            route(r, if (i == 0) label else "$label ${i + 1}", "$idPrefix$i")
        }.also {
            if (it.isEmpty() && routes.length() > 0) {
                val r = routes.getJSONObject(0)
                throw KakaoNaviException(r.optInt("result_code"), r.optString("result_msg"))
            }
        }
    }

    private fun route(r: JSONObject, name: String, id: String): RoutePlan {
        val summary = r.getJSONObject("summary")
        val sections = r.getJSONArray("sections").objects()
        val line = mutableListOf<LatLng>()
        val roadMeters = linkedMapOf<String, Double>()
        val congestion = mutableListOf<Congestion>()
        var walked = 0.0
        for (s in sections) for (road in s.getJSONArray("roads").objects()) {
            val v = road.getJSONArray("vertexes")
            val pts = (0 until v.length() / 2).map { LatLng(v.getDouble(it * 2 + 1), v.getDouble(it * 2)) }
            pts.forEach { if (line.lastOrNull() != it) line += it }
            val d = road.optDouble("distance", 0.0)
            road.optString("name").takeIf { it.isNotBlank() }?.let { roadMeters[it] = (roadMeters[it] ?: 0.0) + d }
            // 교통 상태: 1 정체, 2 지체, 3 서행, 4 원활, 0 정보 없음
            when (road.optInt("traffic_state")) {
                1, 2 -> congestion += Congestion(walked, walked + d, heavy = true, road.optString("name"))
                3 -> congestion += Congestion(walked, walked + d, heavy = false, road.optString("name"))
            }
            walked += d
        }
        // 단계 거리 합과 경로선 길이가 조금 달라서, 위치는 경로선 길이에 맞춘다
        val length = RouteGeometry.lengthMeters(line)
        val scale = length / walked.coerceAtLeast(1.0)

        val turns = mutableListOf<RouteTurn>()
        val hazards = mutableListOf<RiskPoint>()
        val facilities = mutableListOf<FacilityPoint>()
        var at = 0.0
        for (s in sections) for (g in s.getJSONArray("guides").objects()) {
            at += g.optDouble("distance", 0.0)
            val type = g.optInt("type")
            val text = g.optString("guidance")
            if (type == 100) continue // 출발지
            facility(type, g.optString("name"))?.let { (k, n) ->
                if (facilities.none { it.name == n }) facilities += FacilityPoint(at * scale, k, n)
            }
            // 29는 IC·휴게소 등을 지난다는 안내("12시 방향")라 회전으로 넣지 않는다
            if (type == FACILITY_PASS) continue
            val kind = if (type == 101) TurnKind.ARRIVE else kind(text) ?: continue
            turns += RouteTurn(if (type == 101) length else at * scale, kind, if (type == 101) "목적지" else text)
            if (text.contains("지하차도 진입")) {
                val p = LatLng(g.getDouble("y"), g.getDouble("x"))
                val title = g.optString("name").ifBlank { "지하차도" }
                hazards += RiskPoint("$id-underpass-${turns.size}", HazardKind.FLOOD, p, title,
                    "경로 위 지하차도. 비가 많이 오면 물이 찰 수 있어요", vulnerability = Vulnerability.UNDERPASS)
            }
        }
        if (turns.lastOrNull()?.kind != TurnKind.ARRIVE) turns += RouteTurn(length, TurnKind.ARRIVE, "목적지")

        return RoutePlan(
            id = id,
            name = name,
            distanceMeters = summary.optInt("distance"),
            durationSeconds = summary.optInt("duration"),
            line = line,
            turns = turns,
            tollWon = summary.optJSONObject("fare")?.optInt("toll") ?: 0,
            roads = roadMeters,
            hazards = hazards,
            congestion = congestion.map { it.copy(startMeters = it.startMeters * scale, endMeters = it.endMeters * scale) },
            provider = "kakaomobility",
            facilities = facilities,
        )
    }

    private const val FACILITY_PASS = 29

    /**
     * 안내 지점이 시설이면 종류와 이름. 카카오 안내 type: 29 시설 통과, 82 분기점(JC), 84 톨게이트,
     * 12 고속도로 입구, 9 고속도로 출구. 이름 끝으로 IC·휴게소 등을 가른다
     */
    internal fun facility(type: Int, name: String): Pair<FacilityKind, String>? {
        if (name.isBlank()) return null
        val byName = when {
            name.endsWith("휴게소") -> FacilityKind.REST_AREA
            name.endsWith("졸음쉼터") -> FacilityKind.ROADSIDE_STOP
            name.endsWith("JC") -> FacilityKind.JC
            name.endsWith("TG") || name.endsWith("요금소") -> FacilityKind.TOLLGATE
            name.endsWith("IC") -> FacilityKind.IC
            else -> null
        }
        return when (type) {
            FACILITY_PASS, 9, 12 -> byName
            82 -> FacilityKind.JC
            84 -> FacilityKind.TOLLGATE
            else -> null
        }?.let { it to name }
    }

    /** 안내 문구로 회전 종류를 정한다. 안내할 필요가 없는 것(직진 등)은 null */
    internal fun kind(text: String): TurnKind? = when {
        // "서초IC 방면으로 지하차도 진입"처럼 IC가 들어 있어도 지하차도가 먼저다
        text.contains("지하차도 진입") -> TurnKind.STRAIGHT
        text.contains("톨게이트") || text.contains("요금소") -> TurnKind.TOLLGATE
        text.contains("고속도로 입구") || text.contains("고속도로 진입") || Regex("IC.*진입").containsMatchIn(text) -> TurnKind.ENTER_HIGHWAY
        text.contains("출구") || text.contains("고속도로 출구") -> TurnKind.EXIT_HIGHWAY
        text.contains("터널") -> TurnKind.TUNNEL
        text.endsWith("유턴") -> TurnKind.U_TURN
        text.endsWith("좌회전") -> TurnKind.LEFT
        text.endsWith("우회전") -> TurnKind.RIGHT
        text.endsWith("왼쪽 방향") || text.endsWith("왼쪽 직진") -> TurnKind.KEEP_LEFT
        text.endsWith("오른쪽 방향") || text.endsWith("오른쪽 직진") -> TurnKind.KEEP_RIGHT
        text.contains("시 방향") -> TurnKind.STRAIGHT // 회전교차로 "12시 방향"
        else -> null
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
