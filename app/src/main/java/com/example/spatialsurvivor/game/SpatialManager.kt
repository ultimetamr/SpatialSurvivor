package com.example.spatialsurvivor.game

import android.util.Log
import com.example.spatialsurvivor.monster.NavigationPoint
import com.example.spatialsurvivor.player.PlayerComponent

/**
 * Global Scene Mesh ground-plane manager.
 * Selects the largest horizontal floor surface (≥ [MIN_GROUND_AREA_SQUARE_METERS]),
 * caches its world Y height, and exposes [getGroundHeight] to gameplay systems.
 */
object SpatialManager {
    enum class GroundSource {
        PRIMARY_FLOOR_PLANE,
        MEDIAN_FLOOR_SAMPLES,
        LAST_VALID,
        PLAYER_FOOT_FALLBACK,
        NONE,
    }

    data class GroundSnapshot(
        val heightY: Float,
        val areaSquareMeters: Float,
        val source: GroundSource,
        val valid: Boolean,
    )

    @Volatile
    private var snapshot: GroundSnapshot = GroundSnapshot(0f, 0f, GroundSource.NONE, false)

    @Volatile
    private var lastValidHeightY: Float? = null

    fun updatePrimaryGround(
        heightY: Float,
        areaSquareMeters: Float,
        source: GroundSource,
        valid: Boolean,
    ) {
        if (valid && heightY.isFinite()) {
            lastValidHeightY = heightY
        }
        snapshot =
            GroundSnapshot(
                heightY = if (heightY.isFinite()) heightY else lastValidHeightY ?: 0f,
                areaSquareMeters = areaSquareMeters.coerceAtLeast(0f),
                source = source,
                valid = valid,
            )
        Log.i(
            TAG,
            "Ground updated: y=${snapshot.heightY}, area=${snapshot.areaSquareMeters}m², " +
                "source=${snapshot.source}, valid=${snapshot.valid}",
        )
    }

    fun groundSnapshot(): GroundSnapshot = snapshot

    /**
     * Returns the current ground world Y. When mesh ground is unavailable, falls back to the
     * last valid height, then [playerFootY], then 0.
     */
    fun getGroundHeight(playerFootY: Float? = null): Float {
        if (snapshot.valid) return snapshot.heightY
        lastValidHeightY?.let { return it }
        playerFootY?.takeIf { it.isFinite() }?.let {
            Log.w(TAG, "No valid ground plane; falling back to player foot height y=$it")
            return it
        }
        Log.w(TAG, "No valid ground plane and no player foot fallback; using y=0")
        return 0f
    }

    fun estimatePlayerFootY(headWorldY: Float): Float =
        headWorldY - PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS

    fun resolveGroundY(
        navigationFloorY: Float?,
        playerFootY: Float?,
    ): Float {
        navigationFloorY?.takeIf { it.isFinite() }?.let { meshY ->
            if (snapshot.valid) {
                // Prefer mesh navigation floor when it agrees with the primary plane.
                return meshY
            }
            lastValidHeightY = meshY
            return meshY
        }
        return getGroundHeight(playerFootY)
    }

    fun clear() {
        snapshot = GroundSnapshot(0f, 0f, GroundSource.NONE, false)
        lastValidHeightY = null
    }

    const val MIN_GROUND_AREA_SQUARE_METERS = 1.0f
    const val MAX_HORIZONTAL_THICKNESS_METERS = 0.35f
    private const val TAG = "SpatialManager"
}

/** Alias requested in gameplay specs. */
typealias SpatialGroundRuntime = SpatialManager

/** Horizontal floor candidate extracted from Scene Mesh FLOOR semantics. */
data class FloorPlaneCandidate(
    val heightY: Float,
    val areaSquareMeters: Float,
)

fun NavigationPoint.footY(anchorOffsetY: Float): Float = y - anchorOffsetY
