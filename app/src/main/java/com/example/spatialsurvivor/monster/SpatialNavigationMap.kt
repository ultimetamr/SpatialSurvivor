package com.example.spatialsurvivor.monster

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class NavigationPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class NavigationBounds(
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
) {
    val isValid: Boolean
        get() = maxX - minX >= MIN_NAVIGATION_EXTENT && maxZ - minZ >= MIN_NAVIGATION_EXTENT

    companion object {
        private const val MIN_NAVIGATION_EXTENT = 0.5f
    }
}

data class ObstacleFootprint(
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
)

/** Immutable, thread-safe 2D navigation grid derived from world-space Scene Mesh geometry. */
class SpatialNavigationMap private constructor(
    val revision: Long,
    val bounds: NavigationBounds?,
    val floorHeight: Float,
    val ceilingSpawnPoints: List<NavigationPoint>,
    val obstacleCount: Int,
    val walkableCellCount: Int,
    private val cellSize: Float,
    private val columns: Int,
    private val rows: Int,
    private val blocked: BooleanArray,
) {
    val isAvailable: Boolean
        get() = bounds != null && columns > 0 && rows > 0

    fun findBoundarySpawn(
        playerX: Float,
        playerZ: Float,
        minimumDistanceMeters: Float,
        random: Random,
    ): NavigationPoint? {
        bounds ?: return null
        val minimumDistanceSquared = minimumDistanceMeters * minimumDistanceMeters
        var nearestOpenBoundaryDistance = Int.MAX_VALUE
        val eligibleCells = ArrayList<Pair<Int, Int>>()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val boundaryDistance =
                    min(min(column, columns - 1 - column), min(row, rows - 1 - row))
                val index = index(column, row)
                if (blocked[index]) continue
                val point = cellCenter(column, row)
                val dx = point.x - playerX
                val dz = point.z - playerZ
                if (dx * dx + dz * dz >= minimumDistanceSquared) {
                    nearestOpenBoundaryDistance = min(nearestOpenBoundaryDistance, boundaryDistance)
                    eligibleCells += index to boundaryDistance
                }
            }
        }
        if (eligibleCells.isEmpty()) return null
        // Scene walls usually occupy the literal edge cells. The first walkable ring inside
        // those walls is the usable room boundary for spawning.
        val candidates =
            eligibleCells
                .asSequence()
                .filter { (_, distance) ->
                    distance <= nearestOpenBoundaryDistance + BOUNDARY_BAND_CELLS
                }
                .map(Pair<Int, Int>::first)
                .toList()
        if (candidates.isEmpty()) return null
        val selected = candidates[random.nextInt(candidates.size)]
        val column = selected % columns
        val row = selected / columns
        return cellCenter(column, row).takeIf(::isValidSpawnPoint)
    }

    fun findCeilingSpawn(
        playerX: Float,
        playerZ: Float,
        minimumDistanceMeters: Float,
        random: Random,
    ): NavigationPoint? {
        val minimumDistanceSquared = minimumDistanceMeters * minimumDistanceMeters
        val candidates =
            ceilingSpawnPoints.filter { point ->
                val dx = point.x - playerX
                val dz = point.z - playerZ
                dx * dx + dz * dz >= minimumDistanceSquared && isValidSpawnPoint(point)
            }
        return candidates.randomOrNull(random)
    }

    /** True only for a world-space point represented by an open Scene Mesh navigation cell. */
    fun isValidSpawnPoint(point: NavigationPoint): Boolean =
        containsWorldPoint(point.x, point.z) && !isPointBlocked(point)

    /** Geometric center of the mapped open room, or null when no Scene Mesh map is ready. */
    fun openAreaCenter(): NavigationPoint? {
        val worldBounds = bounds ?: return null
        return NavigationPoint(
            x = (worldBounds.minX + worldBounds.maxX) * 0.5f,
            y = floorHeight,
            z = (worldBounds.minZ + worldBounds.maxZ) * 0.5f,
        )
    }

    /**
     * True when [point] sits on an open cell and every sample within [clearanceMeters]
     * also stays outside blocked / out-of-bounds cells.
     */
    fun hasObstacleClearance(point: NavigationPoint, clearanceMeters: Float): Boolean {
        if (!isAvailable) return true
        if (!isValidSpawnPoint(point)) return false
        val safeClearance = clearanceMeters.coerceAtLeast(0f)
        if (safeClearance <= 0f) return true
        for (sample in 0 until CLEARANCE_SAMPLE_COUNT) {
            val angle = sample * TWO_PI / CLEARANCE_SAMPLE_COUNT
            val samplePoint =
                NavigationPoint(
                    x = point.x + kotlin.math.cos(angle.toDouble()).toFloat() * safeClearance,
                    y = point.y,
                    z = point.z + kotlin.math.sin(angle.toDouble()).toFloat() * safeClearance,
                )
            if (isPointBlocked(samplePoint)) return false
        }
        return true
    }

    fun nextWaypoint(start: NavigationPoint, target: NavigationPoint): NavigationPoint {
        if (!isAvailable || isSegmentWalkable(start, target)) return target
        val path = findPath(start, target)
        if (path.isEmpty()) return steerLocally(start, target)
        val maximumLookahead = min(PATH_LOOKAHEAD_CELLS, path.lastIndex)
        for (index in maximumLookahead downTo 0) {
            val candidate = path[index]
            if (isSegmentWalkable(start, candidate)) return candidate
        }
        return steerLocally(start, target)
    }

    internal fun findPath(start: NavigationPoint, target: NavigationPoint): List<NavigationPoint> {
        if (!isAvailable) return listOf(target)
        val startCell = nearestOpenCell(worldToColumn(start.x), worldToRow(start.z)) ?: return emptyList()
        val targetCell = nearestOpenCell(worldToColumn(target.x), worldToRow(target.z)) ?: return emptyList()
        val startIndex = index(startCell.first, startCell.second)
        val targetIndex = index(targetCell.first, targetCell.second)
        if (startIndex == targetIndex) return listOf(target)

        val scores = FloatArray(columns * rows) { Float.POSITIVE_INFINITY }
        val previous = IntArray(columns * rows) { -1 }
        val closed = BooleanArray(columns * rows)
        val queue = PriorityQueue<PathNode>(compareBy(PathNode::estimatedTotalCost))
        scores[startIndex] = 0f
        queue += PathNode(startIndex, heuristic(startIndex, targetIndex))

        var iterations = 0
        while (queue.isNotEmpty() && iterations < MAX_PATH_ITERATIONS) {
            iterations += 1
            val current = queue.remove().index
            if (closed[current]) continue
            if (current == targetIndex) break
            closed[current] = true
            val currentColumn = current % columns
            val currentRow = current / columns

            for ((dx, dz) in NEIGHBOR_OFFSETS) {
                val nextColumn = currentColumn + dx
                val nextRow = currentRow + dz
                if (!isCellOpen(nextColumn, nextRow)) continue
                if (dx != 0 && dz != 0) {
                    if (!isCellOpen(currentColumn + dx, currentRow) ||
                        !isCellOpen(currentColumn, currentRow + dz)
                    ) {
                        continue
                    }
                }
                val next = index(nextColumn, nextRow)
                if (closed[next]) continue
                val stepCost = if (dx != 0 && dz != 0) DIAGONAL_COST else 1f
                val tentative = scores[current] + stepCost
                if (tentative < scores[next]) {
                    scores[next] = tentative
                    previous[next] = current
                    queue += PathNode(next, tentative + heuristic(next, targetIndex))
                }
            }
        }

        if (previous[targetIndex] == -1) return emptyList()
        val reversed = ArrayList<NavigationPoint>()
        var cursor = targetIndex
        while (cursor != startIndex && cursor >= 0) {
            reversed += cellCenter(cursor % columns, cursor / columns)
            cursor = previous[cursor]
        }
        reversed.reverse()
        if (reversed.isNotEmpty()) {
            reversed[reversed.lastIndex] = target.copy(y = floorHeight)
        }
        return reversed
    }

    internal fun isPointBlocked(point: NavigationPoint): Boolean {
        if (!isAvailable) return false
        if (!containsWorldPoint(point.x, point.z)) return true
        val column = worldToColumn(point.x)
        val row = worldToRow(point.z)
        return !isCellOpen(column, row)
    }

    internal fun isSegmentWalkable(start: NavigationPoint, end: NavigationPoint): Boolean {
        if (!isAvailable) return true
        val distance = hypot((end.x - start.x).toDouble(), (end.z - start.z).toDouble()).toFloat()
        val samples = max(1, ceil(distance / (cellSize * 0.5f)).toInt())
        for (sample in 0..samples) {
            val t = sample.toFloat() / samples
            val point =
                NavigationPoint(
                    x = start.x + (end.x - start.x) * t,
                    y = floorHeight,
                    z = start.z + (end.z - start.z) * t,
                )
            if (isPointBlocked(point)) return false
        }
        return true
    }

    private fun steerLocally(start: NavigationPoint, target: NavigationPoint): NavigationPoint {
        val dx = target.x - start.x
        val dz = target.z - start.z
        val length = hypot(dx.toDouble(), dz.toDouble()).toFloat().coerceAtLeast(0.0001f)
        val forwardX = dx / length
        val forwardZ = dz / length
        val candidates =
            listOf(
                NavigationPoint(start.x - forwardZ, floorHeight, start.z + forwardX),
                NavigationPoint(start.x + forwardZ, floorHeight, start.z - forwardX),
            )
        return candidates.firstOrNull { isSegmentWalkable(start, it) } ?: target
    }

    private fun nearestOpenCell(column: Int, row: Int): Pair<Int, Int>? {
        if (isCellOpen(column, row)) return column to row
        for (radius in 1..MAX_OPEN_CELL_SEARCH_RADIUS) {
            for (offset in -radius..radius) {
                val candidates =
                    arrayOf(
                        column + offset to row - radius,
                        column + offset to row + radius,
                        column - radius to row + offset,
                        column + radius to row + offset,
                    )
                candidates.firstOrNull { isCellOpen(it.first, it.second) }?.let { return it }
            }
        }
        return null
    }

    private fun heuristic(from: Int, to: Int): Float {
        val dx = abs(from % columns - to % columns)
        val dz = abs(from / columns - to / columns)
        return max(dx, dz).toFloat()
    }

    private fun isCellOpen(column: Int, row: Int): Boolean =
        column in 0 until columns && row in 0 until rows && !blocked[index(column, row)]

    private fun containsWorldPoint(x: Float, z: Float): Boolean {
        val worldBounds = bounds ?: return false
        return x >= worldBounds.minX && x < worldBounds.maxX &&
            z >= worldBounds.minZ && z < worldBounds.maxZ
    }

    private fun worldToColumn(x: Float): Int {
        val worldBounds = bounds ?: return 0
        return floor((x - worldBounds.minX) / cellSize).toInt()
    }

    private fun worldToRow(z: Float): Int {
        val worldBounds = bounds ?: return 0
        return floor((z - worldBounds.minZ) / cellSize).toInt()
    }

    private fun cellCenter(column: Int, row: Int): NavigationPoint {
        val worldBounds = requireNotNull(bounds)
        return NavigationPoint(
            x = worldBounds.minX + (column + 0.5f) * cellSize,
            y = floorHeight,
            z = worldBounds.minZ + (row + 0.5f) * cellSize,
        )
    }

    private fun index(column: Int, row: Int): Int = row * columns + column

    private data class PathNode(val index: Int, val estimatedTotalCost: Float)

    companion object {
        fun empty(revision: Long = 0L): SpatialNavigationMap =
            SpatialNavigationMap(
                revision = revision,
                bounds = null,
                floorHeight = 0f,
                ceilingSpawnPoints = emptyList(),
                obstacleCount = 0,
                walkableCellCount = 0,
                cellSize = DEFAULT_CELL_SIZE_METERS,
                columns = 0,
                rows = 0,
                blocked = BooleanArray(0),
            )

        fun build(
            revision: Long,
            bounds: NavigationBounds,
            obstacles: List<ObstacleFootprint>,
            floorHeight: Float,
            ceilingSpawnPoints: List<NavigationPoint>,
        ): SpatialNavigationMap {
            if (!bounds.isValid) return empty(revision)
            val width = bounds.maxX - bounds.minX
            val depth = bounds.maxZ - bounds.minZ
            val cellSize = max(DEFAULT_CELL_SIZE_METERS, max(width, depth) / MAX_GRID_DIMENSION)
            val columns = ceil(width / cellSize).toInt().coerceIn(2, MAX_GRID_DIMENSION)
            val rows = ceil(depth / cellSize).toInt().coerceIn(2, MAX_GRID_DIMENSION)
            val blocked = BooleanArray(columns * rows)

            obstacles.forEach { obstacle ->
                val minColumn =
                    floor((obstacle.minX - OBSTACLE_CLEARANCE_METERS - bounds.minX) / cellSize)
                        .toInt()
                        .coerceIn(0, columns - 1)
                val maxColumn =
                    floor((obstacle.maxX + OBSTACLE_CLEARANCE_METERS - bounds.minX) / cellSize)
                        .toInt()
                        .coerceIn(0, columns - 1)
                val minRow =
                    floor((obstacle.minZ - OBSTACLE_CLEARANCE_METERS - bounds.minZ) / cellSize)
                        .toInt()
                        .coerceIn(0, rows - 1)
                val maxRow =
                    floor((obstacle.maxZ + OBSTACLE_CLEARANCE_METERS - bounds.minZ) / cellSize)
                        .toInt()
                        .coerceIn(0, rows - 1)
                for (row in minRow..maxRow) {
                    for (column in minColumn..maxColumn) {
                        blocked[row * columns + column] = true
                    }
                }
            }

            return SpatialNavigationMap(
                revision = revision,
                bounds = bounds,
                floorHeight = floorHeight,
                ceilingSpawnPoints = ceilingSpawnPoints.toList(),
                obstacleCount = obstacles.size,
                walkableCellCount = blocked.count { !it },
                cellSize = cellSize,
                columns = columns,
                rows = rows,
                blocked = blocked,
            )
        }

        private val NEIGHBOR_OFFSETS =
            arrayOf(
                -1 to -1,
                0 to -1,
                1 to -1,
                -1 to 0,
                1 to 0,
                -1 to 1,
                0 to 1,
                1 to 1,
            )
        private const val DEFAULT_CELL_SIZE_METERS = 0.25f
        private const val OBSTACLE_CLEARANCE_METERS = 0.32f
        private const val MAX_GRID_DIMENSION = 80
        private const val BOUNDARY_BAND_CELLS = 3
        private const val MAX_OPEN_CELL_SEARCH_RADIUS = 8
        private const val MAX_PATH_ITERATIONS = 2_048
        private const val PATH_LOOKAHEAD_CELLS = 6
        private const val DIAGONAL_COST = 1.4142135f
        private const val CLEARANCE_SAMPLE_COUNT = 8
        private const val TWO_PI = (Math.PI * 2.0).toFloat()
    }
}
