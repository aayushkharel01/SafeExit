package com.safeexit.shared

import kotlin.math.hypot

data class MapPoint(val x: Double, val y: Double)

data class MetricRect(val left: Double, val top: Double, val right: Double, val bottom: Double)

enum class MapArea {
    ROOM_1, ROOM_2, ROOM_3, ROOM_4, LIBRARY, ROOM_5, TOILET,
    LEFT_HALLWAY, MAIN_HALLWAY, RIGHT_HALLWAY, BOUNDARY
}

enum class MapVertexType { DOOR, JUNCTION, EXIT }

data class MapVertex(val id: String, val position: MapPoint, val type: MapVertexType)

data class MapEdge(val from: String, val to: String, val weightMetres: Double)

data class SafeExitMapState(
    val availableExits: Set<String> = setOf("N", "S", "W", "E"),
    val unsafeVertices: Set<String> = emptySet()
)

data class MetricScreenTransform(val widthPx: Double, val heightPx: Double) {
    val scale: Double get() = minOf(widthPx / SafeExitMapGeometry.WIDTH_METRES, heightPx / SafeExitMapGeometry.HEIGHT_METRES)
    val renderedWidthPx: Double get() = SafeExitMapGeometry.WIDTH_METRES * scale
    val renderedHeightPx: Double get() = SafeExitMapGeometry.HEIGHT_METRES * scale
    val offsetX: Double get() = (widthPx - renderedWidthPx) / 2.0
    val offsetY: Double get() = (heightPx - renderedHeightPx) / 2.0
    fun toScreen(point: MapPoint): MapPoint = MapPoint(offsetX + point.x * scale, offsetY + point.y * scale)
    fun toMap(point: MapPoint): MapPoint = MapPoint((point.x - offsetX) / scale, (point.y - offsetY) / scale)
}

object SafeExitMapGeometry {
    const val WIDTH_METRES = 63.0
    const val HEIGHT_METRES = 30.0
    val EXIT_IDS = setOf("N", "S", "W", "E")

    val roomBounds = mapOf(
        MapArea.ROOM_1 to MetricRect(0.0, 0.0, 20.0, 14.0),
        MapArea.ROOM_2 to MetricRect(20.0, 0.0, 40.0, 14.0),
        MapArea.ROOM_3 to MetricRect(0.0, 16.0, 20.0, 30.0),
        MapArea.ROOM_4 to MetricRect(20.0, 16.0, 40.0, 30.0),
        MapArea.LIBRARY to MetricRect(43.0, 0.0, 63.0, 20.0),
        MapArea.ROOM_5 to MetricRect(43.0, 22.0, 53.0, 30.0),
        MapArea.TOILET to MetricRect(53.0, 22.0, 63.0, 30.0)
    )

    val vertices: List<MapVertex> = listOf(
        MapVertex("R1A", MapPoint(6.5, 14.0), MapVertexType.DOOR), MapVertex("R1B", MapPoint(13.5, 14.0), MapVertexType.DOOR),
        MapVertex("R2A", MapPoint(26.5, 14.0), MapVertexType.DOOR), MapVertex("R2B", MapPoint(33.5, 14.0), MapVertexType.DOOR),
        MapVertex("R3A", MapPoint(6.5, 16.0), MapVertexType.DOOR), MapVertex("R3B", MapPoint(13.5, 16.0), MapVertexType.DOOR),
        MapVertex("R4A", MapPoint(26.5, 16.0), MapVertexType.DOOR), MapVertex("R4B", MapPoint(33.5, 16.0), MapVertexType.DOOR),
        MapVertex("L1", MapPoint(43.0, 6.0), MapVertexType.DOOR), MapVertex("L2", MapPoint(43.0, 14.0), MapVertexType.DOOR),
        MapVertex("L3", MapPoint(53.0, 20.0), MapVertexType.DOOR), MapVertex("R5", MapPoint(48.0, 22.0), MapVertexType.DOOR),
        MapVertex("T1", MapPoint(56.0, 22.0), MapVertexType.DOOR), MapVertex("T2", MapPoint(60.0, 22.0), MapVertexType.DOOR),
        MapVertex("J1", MapPoint(41.5, 15.0), MapVertexType.JUNCTION), MapVertex("J2", MapPoint(41.5, 21.0), MapVertexType.JUNCTION),
        MapVertex("N", MapPoint(41.5, 0.0), MapVertexType.EXIT), MapVertex("S", MapPoint(41.5, 30.0), MapVertexType.EXIT),
        MapVertex("W", MapPoint(0.0, 15.0), MapVertexType.EXIT), MapVertex("E", MapPoint(63.0, 21.0), MapVertexType.EXIT)
    )
    val vertexById = vertices.associateBy { it.id }

    fun classify(point: MapPoint): MapArea {
        if (point.x in 0.0..40.0 && point.y in 14.0..16.0) return if (point.x <= 40.0) MapArea.LEFT_HALLWAY else MapArea.BOUNDARY
        if (point.x in 43.0..63.0 && point.y in 20.0..22.0) return MapArea.RIGHT_HALLWAY
        if (point.x in 40.0..43.0 && point.y in 0.0..30.0) return MapArea.MAIN_HALLWAY
        if (point.x >= 0.0 && point.x < 20.0 && point.y >= 0.0 && point.y < 14.0) return MapArea.ROOM_1
        if (point.x >= 20.0 && point.x < 40.0 && point.y >= 0.0 && point.y < 14.0) return MapArea.ROOM_2
        if (point.x >= 0.0 && point.x < 20.0 && point.y > 16.0 && point.y <= 30.0) return MapArea.ROOM_3
        if (point.x >= 20.0 && point.x < 40.0 && point.y > 16.0 && point.y <= 30.0) return MapArea.ROOM_4
        if (point.x > 43.0 && point.x <= 63.0 && point.y >= 0.0 && point.y < 20.0) return MapArea.LIBRARY
        if (point.x > 43.0 && point.x < 53.0 && point.y > 22.0 && point.y <= 30.0) return MapArea.ROOM_5
        if (point.x >= 53.0 && point.x <= 63.0 && point.y > 22.0 && point.y <= 30.0) return MapArea.TOILET
        return MapArea.BOUNDARY
    }

    fun firstVertices(area: MapArea): List<String> = when (area) {
        MapArea.ROOM_1 -> listOf("R1A", "R1B"); MapArea.ROOM_2 -> listOf("R2A", "R2B")
        MapArea.ROOM_3 -> listOf("R3A", "R3B"); MapArea.ROOM_4 -> listOf("R4A", "R4B")
        MapArea.LIBRARY -> listOf("L1", "L2", "L3"); MapArea.ROOM_5 -> listOf("R5"); MapArea.TOILET -> listOf("T1", "T2")
        MapArea.LEFT_HALLWAY -> listOf("W", "J1"); MapArea.RIGHT_HALLWAY -> listOf("J2", "E")
        MapArea.MAIN_HALLWAY -> emptyList(); MapArea.BOUNDARY -> emptyList()
    }

    fun firstVertices(point: MapPoint, area: MapArea = classify(point)): List<String> = when (area) {
        MapArea.MAIN_HALLWAY -> when { point.y <= 15.0 -> listOf("N", "J1"); point.y <= 21.0 -> listOf("J1", "J2"); else -> listOf("J2", "S") }
        else -> firstVertices(area)
    }
}

object SafeExitPermanentGraph {
    private fun edge(from: String, to: String): MapEdge {
        val a = SafeExitMapGeometry.vertexById.getValue(from).position
        val b = SafeExitMapGeometry.vertexById.getValue(to).position
        return MapEdge(from, to, hypot(b.x - a.x, b.y - a.y))
    }
    val edges: List<MapEdge> = buildList {
        listOf("R1A", "R1B", "R2A", "R2B", "R3A", "R3B", "R4A", "R4B").forEach { add(edge(it, "W")); add(edge(it, "J1")) }
        listOf("L1", "L2").forEach { add(edge(it, "N")); add(edge(it, "J1")) }
        listOf("L3", "R5", "T1", "T2").forEach { add(edge(it, "J2")); add(edge(it, "E")) }
        listOf("N", "W", "J2").forEach { add(edge("J1", it)) }
        listOf("S", "E", "J1").forEach { add(edge("J2", it)) }
    }
}

object SafeExitMapDefaults {
    const val WIDTH_METRES = 63.0; const val HEIGHT_METRES = 30.0
    const val EAST_EXIT = "E"
    val EXIT_IDS = SafeExitMapGeometry.EXIT_IDS
    val vertices = SafeExitMapGeometry.vertices
    val vertexById = SafeExitMapGeometry.vertexById
    val weightedEdges = SafeExitPermanentGraph.edges
}

sealed interface RoutingResult {
    val userLocation: MapPoint
    val area: MapArea
    val possibleFirstVertices: List<String>
    data class Success(override val userLocation: MapPoint, override val area: MapArea, override val possibleFirstVertices: List<String>, val path: List<String>, val destinationExit: String, val totalDistanceMetres: Double) : RoutingResult
    data class NoRoute(override val userLocation: MapPoint, override val area: MapArea, override val possibleFirstVertices: List<String>, val reason: String) : RoutingResult
}

typealias SafeExitRoute = RoutingResult.Success

class SafeExitRouter(private var mapState: SafeExitMapState = SafeExitMapState()) {
    fun updateMapState(state: SafeExitMapState) { mapState = state }
    fun setExitAvailable(exitId: String, available: Boolean) { require(exitId in SafeExitMapGeometry.EXIT_IDS); mapState = mapState.copy(availableExits = if (available) mapState.availableExits + exitId else mapState.availableExits - exitId) }
    fun isExitAvailable(exitId: String) = exitId in mapState.availableExits

    fun calculateRoute(user: MapPoint): RoutingResult {
        require(user.x in 0.0..SafeExitMapGeometry.WIDTH_METRES && user.y in 0.0..SafeExitMapGeometry.HEIGHT_METRES) { "Location is outside the building map" }
        val area = SafeExitMapGeometry.classify(user)
        val first = SafeExitMapGeometry.firstVertices(user, area).filterNot { it in mapState.unsafeVertices || (it in SafeExitMapGeometry.EXIT_IDS && it !in mapState.availableExits) }
        if (first.isEmpty()) return RoutingResult.NoRoute(user, area, first, "This location is on an unassigned boundary or has no available first vertex.")
        val edges = SafeExitPermanentGraph.edges.filterNot { it.from in mapState.unsafeVertices || it.to in mapState.unsafeVertices || (it.to in SafeExitMapGeometry.EXIT_IDS && it.to !in mapState.availableExits) } + first.map { id -> val p = SafeExitMapGeometry.vertexById.getValue(id).position; MapEdge("U", id, hypot(p.x - user.x, p.y - user.y)) }
        val distances = mutableMapOf("U" to 0.0); val previous = mutableMapOf<String, String>(); val remaining = edges.flatMap { listOf(it.from, it.to) }.toMutableSet().apply { add("U") }
        while (remaining.isNotEmpty()) {
            val current = remaining.minByOrNull { distances[it] ?: Double.POSITIVE_INFINITY } ?: break
            remaining.remove(current); val base = distances[current] ?: continue
            edges.filter { it.from == current }.forEach { e -> val candidate = base + e.weightMetres; if (candidate < (distances[e.to] ?: Double.POSITIVE_INFINITY)) { distances[e.to] = candidate; previous[e.to] = current } }
        }
        val destination = mapState.availableExits.filterNot { it in mapState.unsafeVertices }.mapNotNull { id -> distances[id]?.let { id to it } }.minByOrNull { it.second }
            ?: return RoutingResult.NoRoute(user, area, first, "No available emergency exit is reachable from this location.")
        val path = buildList { var cursor: String? = destination.first; while (cursor != null) { add(cursor); cursor = previous[cursor] }; reverse() }
        return RoutingResult.Success(user, area, first, path, destination.first, destination.second)
    }
}
