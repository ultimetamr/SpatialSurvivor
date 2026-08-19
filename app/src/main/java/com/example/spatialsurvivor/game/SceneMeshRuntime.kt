package com.example.spatialsurvivor.game

import android.util.Log
import com.example.spatialsurvivor.game.FloorPlaneCandidate
import com.example.spatialsurvivor.game.SpatialManager
import com.example.spatialsurvivor.monster.NavigationBounds
import com.example.spatialsurvivor.monster.NavigationPoint
import com.example.spatialsurvivor.monster.ObstacleFootprint
import com.example.spatialsurvivor.monster.SpatialNavigationMap
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.base.AnchorUpdate
import com.pico.spatial.sense.base.SemanticLabelType
import com.pico.spatial.sense.mesh.MeshAnchor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Converts Scene Mesh anchors into a compact world-space navigation snapshot.
 * Anchor callbacks never mutate Spatial ECS entities and grid work stays off the render thread.
 */
object SceneMeshRuntime {
    private val anchors = ConcurrentHashMap<UUID, MeshAnchor>()
    private val navigationChunks = ConcurrentHashMap<UUID, MeshNavigationChunk>()
    private val revision = AtomicLong(0L)
    private val rebuildLock = Any()
    private val rebuildExecutor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "SceneMeshNavigation").apply { isDaemon = true }
        }
    private var pendingRebuild: ScheduledFuture<*>? = null

    @Volatile
    private var currentNavigation = SpatialNavigationMap.empty()

    val anchorCount: Int
        get() = anchors.size

    fun onAnchorUpdate(update: AnchorUpdate<MeshAnchor>) {
        when (update.event) {
            AnchorUpdate.Event.ADDED,
            AnchorUpdate.Event.UPDATED,
            AnchorUpdate.Event.LOADED,
            -> {
                anchors[update.anchor.anchorUUID] = update.anchor
                navigationChunks[update.anchor.anchorUUID] = update.anchor.toNavigationChunk()
                scheduleNavigationRebuild()
            }

            AnchorUpdate.Event.REMOVED -> {
                anchors.remove(update.anchor.anchorUUID)
                navigationChunks.remove(update.anchor.anchorUUID)
                scheduleNavigationRebuild()
            }

            AnchorUpdate.Event.UNKNOWN -> Unit
        }
    }

    fun snapshot(): List<MeshAnchor> = anchors.values.toList()

    fun navigationSnapshot(): SpatialNavigationMap = currentNavigation

    fun clear() {
        synchronized(rebuildLock) {
            pendingRebuild?.cancel(false)
            pendingRebuild = null
        }
        anchors.clear()
        navigationChunks.clear()
        currentNavigation = SpatialNavigationMap.empty(revision.incrementAndGet())
        SpatialManager.clear()
    }

    private fun scheduleNavigationRebuild() {
        synchronized(rebuildLock) {
            pendingRebuild?.cancel(false)
            pendingRebuild =
                rebuildExecutor.schedule(
                    ::rebuildNavigation,
                    REBUILD_DEBOUNCE_MILLISECONDS,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    private fun rebuildNavigation() {
        val chunks = navigationChunks.values.toList()
        if (chunks.isEmpty()) {
            currentNavigation = SpatialNavigationMap.empty(revision.incrementAndGet())
            return
        }

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var lowestY = Float.POSITIVE_INFINITY
        val floorSamples = ArrayList<Float>()
        val floorPlaneCandidates = ArrayList<FloorPlaneCandidate>()
        val ceilingPoints = ArrayList<NavigationPoint>()
        val obstacles = ArrayList<ObstacleFootprint>()

        chunks.forEach { chunk ->
            minX = minOf(minX, chunk.bounds.minX)
            maxX = maxOf(maxX, chunk.bounds.maxX)
            minZ = minOf(minZ, chunk.bounds.minZ)
            maxZ = maxOf(maxZ, chunk.bounds.maxZ)
            lowestY = minOf(lowestY, chunk.minimumY)
            floorSamples += chunk.floorSamples
            chunk.floorPlaneCandidate?.let(floorPlaneCandidates::add)
            ceilingPoints += chunk.ceilingPoints
            obstacles += chunk.obstacles
        }

        floorSamples.sort()
        val primaryFloor =
            floorPlaneCandidates.maxByOrNull { it.areaSquareMeters }
        val floorHeight =
            when {
                primaryFloor != null &&
                    primaryFloor.areaSquareMeters >= SpatialManager.MIN_GROUND_AREA_SQUARE_METERS ->
                    primaryFloor.heightY
                else ->
                    floorSamples.getOrNull(floorSamples.size / 2)
                        ?: lowestY.takeIf(Float::isFinite)
                        ?: 0f
            }
        val nextRevision = revision.incrementAndGet()
        currentNavigation =
            SpatialNavigationMap.build(
                revision = nextRevision,
                bounds = NavigationBounds(minX, maxX, minZ, maxZ),
                obstacles = obstacles,
                floorHeight = floorHeight,
                ceilingSpawnPoints = ceilingPoints,
            )
        if (primaryFloor != null &&
            primaryFloor.areaSquareMeters >= SpatialManager.MIN_GROUND_AREA_SQUARE_METERS
        ) {
            SpatialManager.updatePrimaryGround(
                heightY = primaryFloor.heightY,
                areaSquareMeters = primaryFloor.areaSquareMeters,
                source = SpatialManager.GroundSource.PRIMARY_FLOOR_PLANE,
                valid = true,
            )
        } else if (floorSamples.isNotEmpty()) {
            SpatialManager.updatePrimaryGround(
                heightY = floorHeight,
                areaSquareMeters = 0f,
                source = SpatialManager.GroundSource.MEDIAN_FLOOR_SAMPLES,
                valid = true,
            )
        }
        Log.i(
            TAG,
            "Scene Mesh navigation rebuilt: revision=$nextRevision, anchors=${chunks.size}, " +
                "obstacles=${obstacles.size}, ceilings=${ceilingPoints.size}, " +
                "walkable=${currentNavigation.walkableCellCount}, " +
                "bounds=[$minX,$maxX]x[$minZ,$maxZ], floorY=$floorHeight",
        )
    }

    private fun MeshAnchor.toNavigationChunk(): MeshNavigationChunk {
        val anchorTransform = transform
        val scale = anchorTransform.scale
        val rotation = anchorTransform.quaternion
        val translation = anchorTransform.position
        val worldVertices =
            vertices.map { vertex ->
                val scaled =
                    Vector3(
                        vertex.x * scale.x,
                        vertex.y * scale.y,
                        vertex.z * scale.z,
                    )
                val rotated = rotation.rotateVector(scaled)
                Vector3(
                    rotated.x + translation.x,
                    rotated.y + translation.y,
                    rotated.z + translation.z,
                )
            }

        if (worldVertices.isEmpty()) {
            val position = transform.position
            return MeshNavigationChunk(
                bounds = NavigationBounds(position.x, position.x, position.z, position.z),
                minimumY = position.y,
                floorSamples = emptyList(),
                floorPlaneCandidate = null,
                ceilingPoints = emptyList(),
                obstacles = emptyList(),
            )
        }

        val allBounds = worldVertices.toBounds()
        val semanticGroups = groupWorldVerticesBySemantic(worldVertices)

        val floorVertices = semanticGroups[SemanticLabelType.FLOOR].orEmpty()
        val ceilingVertices = semanticGroups[SemanticLabelType.CEILING].orEmpty()
        val floorSamples = floorVertices.sampleYValues()
        val ceilingPoints = ceilingVertices.toCeilingPoints()
        val obstacles = toObstacleFootprints(worldVertices, semanticGroups).toMutableList()

        if (obstacles.isEmpty() &&
            semantics.all { it == SemanticLabelType.UNKNOWN } &&
            allBounds.maximumY - allBounds.minimumY >= UNKNOWN_OBSTACLE_MINIMUM_HEIGHT &&
            allBounds.minimumY <= UNKNOWN_OBSTACLE_MAXIMUM_BASE_HEIGHT
        ) {
            obstacles +=
                ObstacleFootprint(
                    minX = allBounds.minX,
                    maxX = allBounds.maxX,
                    minZ = allBounds.minZ,
                    maxZ = allBounds.maxZ,
                )
        }

        return MeshNavigationChunk(
            bounds = NavigationBounds(allBounds.minX, allBounds.maxX, allBounds.minZ, allBounds.maxZ),
            minimumY = allBounds.minimumY,
            floorSamples = floorSamples,
            floorPlaneCandidate = floorVertices.toFloorPlaneCandidate(),
            ceilingPoints = ceilingPoints,
            obstacles = obstacles,
        )
    }

    private fun List<Vector3>.toFloorPlaneCandidate(): FloorPlaneCandidate? {
        if (isEmpty()) return null
        val bounds = toBounds()
        val width = bounds.maxX - bounds.minX
        val depth = bounds.maxZ - bounds.minZ
        val area = width * depth
        val thickness = bounds.maximumY - bounds.minimumY
        if (thickness > SpatialManager.MAX_HORIZONTAL_THICKNESS_METERS) return null
        if (area < SpatialManager.MIN_GROUND_AREA_SQUARE_METERS) return null
        val avgY = sumOf { it.y.toDouble() }.toFloat() / size
        return FloorPlaneCandidate(heightY = avgY, areaSquareMeters = area)
    }

    private fun MeshAnchor.groupWorldVerticesBySemantic(
        worldVertices: List<Vector3>,
    ): Map<SemanticLabelType, MutableList<Vector3>> {
        val groups = HashMap<SemanticLabelType, MutableList<Vector3>>()
        val triangleCount = indices.size / 3
        when {
            semantics.size == worldVertices.size ->
                worldVertices.forEachIndexed { index, vertex ->
                    groups.getOrPut(semantics[index]) { ArrayList() } += vertex
                }

            triangleCount > 0 && semantics.size == triangleCount ->
                repeat(triangleCount) { triangle ->
                    val points = groups.getOrPut(semantics[triangle]) { ArrayList() }
                    repeat(3) { corner ->
                        worldVertices.getOrNull(indices[triangle * 3 + corner])?.let(points::add)
                    }
                }

            semantics.size == 1 -> groups[semantics.first()] = ArrayList(worldVertices)
            else ->
                semantics.distinct().forEach { label ->
                    groups[label] = ArrayList(worldVertices)
                }
        }
        return groups
    }

    private fun MeshAnchor.toObstacleFootprints(
        worldVertices: List<Vector3>,
        semanticGroups: Map<SemanticLabelType, List<Vector3>>,
    ): List<ObstacleFootprint> {
        val triangleCount = indices.size / 3
        if (triangleCount > 0) {
            val footprints = ArrayList<ObstacleFootprint>()
            repeat(triangleCount) { triangle ->
                val vertexIndices =
                    intArrayOf(
                        indices[triangle * 3],
                        indices[triangle * 3 + 1],
                        indices[triangle * 3 + 2],
                    )
                val isBlocking =
                    when {
                        semantics.size == worldVertices.size ->
                            vertexIndices.count { index ->
                                semantics.getOrNull(index) in BLOCKING_SEMANTICS
                            } >= 2
                        semantics.size == triangleCount -> semantics[triangle] in BLOCKING_SEMANTICS
                        semantics.size == 1 -> semantics.first() in BLOCKING_SEMANTICS
                        else -> false
                    }
                if (!isBlocking) return@repeat
                vertexIndices
                    .map { index -> worldVertices.getOrNull(index) }
                    .filterNotNull()
                    .toObstacleFootprint()
                    ?.let(footprints::add)
            }
            if (footprints.isNotEmpty()) return footprints.distinct()
        }

        return semanticGroups
            .filterKeys(BLOCKING_SEMANTICS::contains)
            .values
            .mapNotNull { points -> points.toObstacleFootprint() }
    }

    private fun List<Vector3>.sampleYValues(): List<Float> {
        if (isEmpty()) return emptyList()
        val stride = max(1, size / MAX_HEIGHT_SAMPLES_PER_ANCHOR)
        return indices.step(stride).map { this[it].y }
    }

    private fun List<Vector3>.toCeilingPoints(): List<NavigationPoint> {
        if (isEmpty()) return emptyList()
        val groupSize = max(1, size / MAX_CEILING_POINTS_PER_ANCHOR)
        return chunked(groupSize)
            .take(MAX_CEILING_POINTS_PER_ANCHOR)
            .map { group ->
                NavigationPoint(
                    x = group.sumOf { it.x.toDouble() }.toFloat() / group.size,
                    y = group.sumOf { it.y.toDouble() }.toFloat() / group.size,
                    z = group.sumOf { it.z.toDouble() }.toFloat() / group.size,
                )
            }
    }

    private fun List<Vector3>.toObstacleFootprint(): ObstacleFootprint? {
        if (isEmpty()) return null
        val bounds = toBounds()
        if (bounds.maxX - bounds.minX < MINIMUM_OBSTACLE_EXTENT &&
            bounds.maxZ - bounds.minZ < MINIMUM_OBSTACLE_EXTENT
        ) {
            return null
        }
        return ObstacleFootprint(bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ)
    }

    private fun List<Vector3>.toBounds(): VertexBounds {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        forEach { point ->
            minX = minOf(minX, point.x)
            maxX = maxOf(maxX, point.x)
            minY = minOf(minY, point.y)
            maxY = maxOf(maxY, point.y)
            minZ = minOf(minZ, point.z)
            maxZ = maxOf(maxZ, point.z)
        }
        return VertexBounds(minX, maxX, minY, maxY, minZ, maxZ)
    }

    private data class MeshNavigationChunk(
        val bounds: NavigationBounds,
        val minimumY: Float,
        val floorSamples: List<Float>,
        val floorPlaneCandidate: FloorPlaneCandidate?,
        val ceilingPoints: List<NavigationPoint>,
        val obstacles: List<ObstacleFootprint>,
    )

    private data class VertexBounds(
        val minX: Float,
        val maxX: Float,
        val minimumY: Float,
        val maximumY: Float,
        val minZ: Float,
        val maxZ: Float,
    )

    private val BLOCKING_SEMANTICS =
        setOf(
            SemanticLabelType.WALL,
            SemanticLabelType.TABLE,
            SemanticLabelType.SOFA,
            SemanticLabelType.CHAIR,
            SemanticLabelType.BEAM,
            SemanticLabelType.COLUMN,
            SemanticLabelType.CURTAIN,
            SemanticLabelType.CABINET,
            SemanticLabelType.BED,
            SemanticLabelType.PLANT,
            SemanticLabelType.SCREEN,
            SemanticLabelType.VIRTUAL_WALL,
            SemanticLabelType.REFRIGERATOR,
            SemanticLabelType.WASHING_MACHINE,
            SemanticLabelType.AIR_CONDITIONER,
            SemanticLabelType.STAIRWAY,
        )
    private const val MAX_HEIGHT_SAMPLES_PER_ANCHOR = 128
    private const val MAX_CEILING_POINTS_PER_ANCHOR = 4
    private const val MINIMUM_OBSTACLE_EXTENT = 0.08f
    private const val UNKNOWN_OBSTACLE_MINIMUM_HEIGHT = 0.25f
    private const val UNKNOWN_OBSTACLE_MAXIMUM_BASE_HEIGHT = 1.5f
    private const val REBUILD_DEBOUNCE_MILLISECONDS = 160L
    private const val TAG = "SceneMeshRuntime"
}
