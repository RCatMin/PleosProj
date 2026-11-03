package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.compare.selectedRoute
import com.example.pleosproj.ui.hazardColor
import com.example.pleosproj.ui.hazardName
import com.example.pleosproj.ui.state.RouteCompareState
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal class RouteCompareMapData(state: RouteCompareState) {
    private val selectedId = state.selectedRoute()?.id
    private val drawable = state.candidates.filter { it.routeLine.size >= 2 }
    val routes: List<List<Point>> = drawable.map { candidate ->
        candidate.routeLine.map { Point.fromLngLat(it.lng, it.lat) }
    }
    val routeFeatures: FeatureCollection = FeatureCollection.fromFeatures(drawable.map { candidate ->
        Feature.fromGeometry(LineString.fromLngLats(candidate.routeLine.map { Point.fromLngLat(it.lng, it.lat) })).apply {
            addStringProperty("id", candidate.id)
            addBooleanProperty("selected", candidate.id == selectedId)
            addBooleanProperty("excluded", candidate.excluded)
        }
    })
    val labelFeatures: FeatureCollection = FeatureCollection.fromFeatures(drawable.map { candidate ->
        val point = candidate.routeLine[candidate.routeLine.size / 2]
        Feature.fromGeometry(Point.fromLngLat(point.lng, point.lat)).apply {
            addStringProperty("label", when {
                candidate.excluded -> "${candidate.name} · 제외"
                candidate.id == selectedId -> "${candidate.name} · 선택"
                else -> candidate.name
            })
        }
    })
    private val hazards = state.candidates.flatMap { it.hazards }.distinctBy { it.id }
    val hazardFeatures: FeatureCollection = FeatureCollection.fromFeatures(hazards.map { hazard ->
        Feature.fromGeometry(Point.fromLngLat(hazard.position.lng, hazard.position.lat)).apply {
            addStringProperty("label", hazardName(hazard.kind))
            addStringProperty("color", "#%06X".format(hazardColor(hazard.kind) and 0xFFFFFF))
        }
    })
    val locationFeatures: FeatureCollection = FeatureCollection.fromFeatures(buildList {
        routes.firstOrNull()?.let { route ->
            add(Feature.fromGeometry(route.first()).apply { addStringProperty("label", "출발") })
            add(Feature.fromGeometry(route.last()).apply { addStringProperty("label", "목적지") })
        }
    })
}
