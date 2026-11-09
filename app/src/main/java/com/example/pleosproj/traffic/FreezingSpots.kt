package com.example.pleosproj.traffic

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 도로교통공단 결빙 교통사고 다발지역 한 곳. 반경 약 200m 원 안에서 한 해 결빙 사고가 여러 번 난 곳이다.
 * 통계로 뽑은 지점이므로, 여기에 없다고 결빙 위험이 없다는 뜻은 아니다.
 */
data class FreezingSpot(
    val name: String,
    val position: LatLng,
    /** 사고 건수 (여러 해에 걸쳐 나오면 합친다) */
    val accidents: Int,
    /** 다발지역으로 뽑힌 해 */
    val years: List<Int>,
)

object FreezingParser {
    /** 공공데이터포털 getRestFrequentzoneFreezing JSON. 자료가 없으면(NODATA) 빈 목록 */
    fun parse(json: String, year: Int): List<FreezingSpot> {
        val root = JSONObject(json)
        val code = root.optString("resultCode")
        if (code == "03") return emptyList()
        if (code != "00") throw IllegalStateException("결빙 사고 다발지역 오류 $code ${root.optString("resultMsg")}")
        val items = root.optJSONObject("items")?.optJSONArray("item") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            FreezingSpot(
                // "서울 강남구 역삼동(베이트리어린이집 부근)"에서 시·구는 떼고 동과 부근 이름만 남긴다
                name = o.optString("spot_nm").split(" ").let { if (it.size > 2) it.drop(2).joinToString(" ") else it.joinToString(" ") },
                position = LatLng(o.getString("la_crd").toDouble(), o.getString("lo_crd").toDouble()),
                accidents = o.optInt("occrrnc_cnt"),
                years = listOf(year),
            )
        }
    }

    /** 여러 해 자료에서 100m 안에 있는 같은 곳을 하나로 합친다. 이름은 최근 해 것을 쓴다 */
    fun merge(spots: List<FreezingSpot>): List<FreezingSpot> {
        val merged = mutableListOf<FreezingSpot>()
        spots.sortedByDescending { it.years.max() }.forEach { s ->
            val i = merged.indexOfFirst { RouteGeometry.distanceMeters(it.position, s.position) <= 100.0 }
            if (i < 0) merged += s
            else merged[i] = merged[i].copy(accidents = merged[i].accidents + s.accidents, years = (merged[i].years + s.years).distinct().sorted())
        }
        return merged
    }
}

/** 공공데이터포털 결빙 사고 다발지역 API. 기상청과 같은 공공데이터포털 키를 쓴다. 메인 스레드에서 부르지 않는다 */
class FreezingClient(private val serviceKey: String) {
    /** [sigungu]는 법정동 코드 앞 5자리 (예: 서울 강남구 11680) */
    fun spots(sigungu: String, year: Int): List<FreezingSpot> {
        val url = "https://apis.data.go.kr/B552061/frequentzoneFreezing/getRestFrequentzoneFreezing?serviceKey=$serviceKey" +
            "&searchYearCd=$year&siDo=${sigungu.take(2)}&guGun=${sigungu.drop(2)}&type=json&numOfRows=100&pageNo=1"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return FreezingParser.parse(body, year)
    }
}

/**
 * 경로가 지나는 시·군·구의 결빙 사고 다발지역을 모은다. 지점 → 시·군·구 코드는 [regionOf]로,
 * 시·군·구·해별 자료는 [spotsOf]로 받는다. 한 번 받은 값은 앱이 켜져 있는 동안 다시 받지 않는다 (해마다 바뀌는 통계라서).
 */
class FreezingSpotFinder(
    private val regionOf: (LatLng) -> String?,
    private val spotsOf: (sigungu: String, year: Int) -> List<FreezingSpot>,
    private val years: List<Int>,
) {
    private val regions = ConcurrentHashMap<String, String>()
    private val spots = ConcurrentHashMap<Pair<String, Int>, List<FreezingSpot>>()
    private val pool = Executors.newFixedThreadPool(6) { r -> Thread(r, "freezing").apply { isDaemon = true } }

    /** [lines]가 지나는 시·군·구의 다발지역. 일부 지역을 못 받으면 그 지역만 빼고 돌려준다 */
    fun along(lines: List<List<LatLng>>): List<FreezingSpot> {
        // 약 1.5km마다 찍은 점을 1km 격자로 묶어 같은 곳을 여러 번 묻지 않는다
        val samples = lines.flatMap { line ->
            val length = RouteGeometry.lengthMeters(line)
            (0..(length / SAMPLE_METERS).toInt()).map { RouteGeometry.pointAt(line, it * SAMPLE_METERS) } + line.last()
        }.distinctBy { cell(it) }
        val codes = samples.map { p -> pool.submit<String?> { regions[cell(p)] ?: runCatching { regionOf(p) }.getOrNull()?.also { regions[cell(p)] = it } } }
            .mapNotNull { it.get() }.distinct()
        val found = codes.flatMap { code -> years.map { y -> code to y } }
            .map { key -> pool.submit<List<FreezingSpot>> { spots[key] ?: runCatching { spotsOf(key.first, key.second) }.getOrNull()?.also { spots[key] = it }.orEmpty() } }
            .flatMap { it.get() }
        return FreezingParser.merge(found)
    }

    private fun cell(p: LatLng) = "${(p.lat * 100).toInt()},${(p.lng * 100).toInt()}"

    companion object {
        const val SAMPLE_METERS = 1500.0
        /** 경로가 다발지역 원(반경 약 200m) 안을 지나면 그 경로의 위험 지점으로 본다 */
        const val NEAR_ROUTE_METERS = 200.0
    }
}
