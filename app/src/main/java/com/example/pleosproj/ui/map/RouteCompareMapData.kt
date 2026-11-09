package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.compare.selectedRoute
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
    private val hazardLines = drawable.sortedBy { it.id == selectedId }.flatMap { candidate ->
        hazardRouteLines(candidate.routeLine, candidate.hazards, hazardsOnRoute = true)
    }
    val hazardKinds = hazardLines.map { it.kind }.distinct()
    val hazardFeatures = hazardLines.toFeatures()
    val locationFeatures: FeatureCollection = FeatureCollection.fromFeatures(buildList {
        routes.firstOrNull()?.let { route ->
            add(Feature.fromGeometry(route.first()).apply { addStringProperty("label", "출발") })
            add(Feature.fromGeometry(route.last()).apply { addStringProperty("label", "목적지") })
        }
    })
}
