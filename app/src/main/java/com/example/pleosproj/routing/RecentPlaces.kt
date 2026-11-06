package com.example.pleosproj.routing

import android.content.Context
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.Place
import org.json.JSONArray
import org.json.JSONObject

/**
 * 최근 목적지. 우리 앱에서 고른 목적지를 앱 안에 저장한다 (기본 내비 기록에 기대지 않는다).
 * 같은 곳은 맨 앞으로 옮기고, [MAX]곳까지만 둔다. 거리는 저장하지 않는다(그때그때 위치가 다르다).
 */
class RecentPlaces(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("recent_places", Context.MODE_PRIVATE)

    fun list(): List<Place> = runCatching { decode(prefs.getString(KEY, null)) }.getOrDefault(emptyList())

    fun add(place: Place) {
        val next = listOf(place.copy(distanceMeters = null)) + list().filter { it.id != place.id }
        prefs.edit().putString(KEY, encode(next.take(MAX))).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        const val MAX = 10
        private const val KEY = "places"

        internal fun encode(places: List<Place>): String = JSONArray(places.map {
            JSONObject().put("id", it.id).put("name", it.name).put("address", it.address)
                .put("lat", it.position.lat).put("lng", it.position.lng).put("category", it.category)
        }).toString()

        internal fun decode(json: String?): List<Place> {
            if (json.isNullOrBlank()) return emptyList()
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Place(o.getString("id"), o.getString("name"), o.optString("address"),
                    GeoPoint(o.getDouble("lat"), o.getDouble("lng")), null, o.optString("category"))
            }
        }
    }
}
