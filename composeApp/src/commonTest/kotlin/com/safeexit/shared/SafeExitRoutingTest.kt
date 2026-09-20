package com.safeexit.shared

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.math.abs

class SafeExitRoutingTest {
    @Test
    fun routeUsesAnAvailableExit() {
        val result = SafeExitRouter().calculateRoute(MapPoint(10.0, 8.0))
        assertTrue(result is RoutingResult.Success)
        result as RoutingResult.Success
        assertEquals(result.destinationExit, result.path.last())
        assertEquals("U", result.path.first())
    }

    @Test
    fun blockedExitsAreNotSelected() {
        val router = SafeExitRouter(SafeExitMapState(availableExits = setOf(SafeExitMapDefaults.EAST_EXIT)))
        val result = router.calculateRoute(MapPoint(10.0, 8.0))
        assertTrue(result is RoutingResult.Success)
        assertEquals(SafeExitMapDefaults.EAST_EXIT, (result as RoutingResult.Success).destinationExit)
    }

    @Test
    fun noRouteIsReturnedWhenEveryExitIsBlocked() {
        val router = SafeExitRouter(SafeExitMapState(availableExits = emptySet()))

        assertTrue(router.calculateRoute(MapPoint(10.0, 8.0)) is RoutingResult.NoRoute)
    }

    @Test fun geometryAndGraphMatchSpecification() {
        assertEquals(MapArea.ROOM_1, SafeExitMapGeometry.classify(MapPoint(10.0, 5.0)))
        assertEquals(MapArea.ROOM_2, SafeExitMapGeometry.classify(MapPoint(25.0, 5.0)))
        assertEquals(MapArea.ROOM_3, SafeExitMapGeometry.classify(MapPoint(10.0, 25.0)))
        assertEquals(MapArea.ROOM_4, SafeExitMapGeometry.classify(MapPoint(25.0, 25.0)))
        assertEquals(MapArea.LIBRARY, SafeExitMapGeometry.classify(MapPoint(55.0, 5.0)))
        assertEquals(MapArea.ROOM_5, SafeExitMapGeometry.classify(MapPoint(48.0, 25.0)))
        assertEquals(MapArea.TOILET, SafeExitMapGeometry.classify(MapPoint(57.0, 25.0)))
        assertEquals(MapArea.LEFT_HALLWAY, SafeExitMapGeometry.classify(MapPoint(10.0, 15.0)))
        assertEquals(MapArea.MAIN_HALLWAY, SafeExitMapGeometry.classify(MapPoint(41.5, 8.0)))
        assertEquals(MapArea.RIGHT_HALLWAY, SafeExitMapGeometry.classify(MapPoint(50.0, 21.0)))
        assertEquals(14, SafeExitMapGeometry.vertices.count { it.type == MapVertexType.DOOR })
        assertEquals(2, SafeExitMapGeometry.vertices.count { it.type == MapVertexType.JUNCTION })
        assertEquals(4, SafeExitMapGeometry.vertices.count { it.type == MapVertexType.EXIT })
        assertTrue(SafeExitMapGeometry.EXIT_IDS.all { id -> SafeExitPermanentGraph.edges.none { it.from == id } })
        SafeExitPermanentGraph.edges.forEach { edge ->
            val a = SafeExitMapGeometry.vertexById.getValue(edge.from).position
            val b = SafeExitMapGeometry.vertexById.getValue(edge.to).position
            assertTrue(abs(edge.weightMetres - kotlin.math.hypot(b.x - a.x, b.y - a.y)) < 0.000001)
        }
    }

    @Test fun dynamicVerticesAndReroutingWork() {
        assertEquals(listOf("R2A", "R2B"), SafeExitMapGeometry.firstVertices(MapPoint(25.0, 5.0)))
        assertEquals(listOf("L1", "L2", "L3"), SafeExitMapGeometry.firstVertices(MapPoint(55.0, 5.0)))
        assertEquals(listOf("W", "J1"), SafeExitMapGeometry.firstVertices(MapPoint(10.0, 15.0)))
        assertEquals(listOf("J2", "E"), SafeExitMapGeometry.firstVertices(MapPoint(50.0, 21.0)))
        assertEquals(listOf("J1", "J2"), SafeExitMapGeometry.firstVertices(MapPoint(41.5, 18.0)))
        val router = SafeExitRouter()
        val first = router.calculateRoute(MapPoint(10.0, 15.0)) as RoutingResult.Success
        router.setExitAvailable(first.destinationExit, false)
        val second = router.calculateRoute(MapPoint(10.0, 15.0))
        assertTrue(second is RoutingResult.Success)
        assertTrue((second as RoutingResult.Success).destinationExit != first.destinationExit)
        router.updateMapState(SafeExitMapState(emptySet()))
        assertTrue(router.calculateRoute(MapPoint(10.0, 15.0)) is RoutingResult.NoRoute)
        router.setExitAvailable("E", true)
        assertTrue(router.calculateRoute(MapPoint(10.0, 15.0)) is RoutingResult.Success)
    }
}
