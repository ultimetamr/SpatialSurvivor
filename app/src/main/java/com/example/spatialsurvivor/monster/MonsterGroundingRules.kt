package com.example.spatialsurvivor.monster

import android.util.Log
import com.example.spatialsurvivor.game.SpatialManager
import kotlin.math.abs

/** Pure monster ground alignment: spawn, move, drift correction, and debug logging. */
object MonsterGroundingRules {
    const val DEBUG_GROUND_MARKERS = false
    const val MAX_GROUND_DRIFT_METERS = 0.05f
    const val MAX_ROOT_MOTION_BOBBING_METERS = 0.01f

    fun resolveGroundY(
        navigationFloorY: Float?,
        playerFootY: Float?,
    ): Float = SpatialManager.resolveGroundY(navigationFloorY, playerFootY)

    fun bodyCenterY(groundY: Float, type: MonsterType): Float = groundY + type.anchorOffsetY

    fun bodyCenterY(groundY: Float, monster: MonsterComponent): Float =
        bodyCenterY(groundY, monster.monsterType)

    fun groundedPoint(
        x: Float,
        z: Float,
        monster: MonsterComponent,
        groundY: Float,
    ): NavigationPoint =
        NavigationPoint(
            x = x,
            y = bodyCenterY(groundY, monster),
            z = z,
        )

    fun groundedPoint(
        xz: NavigationPoint,
        monster: MonsterComponent,
        groundY: Float,
    ): NavigationPoint = groundedPoint(xz.x, xz.z, monster, groundY)

    /** Locks Y to ground + anchor; corrects drift beyond [MAX_GROUND_DRIFT_METERS]. */
    fun lockHorizontalMove(
        current: NavigationPoint,
        nextX: Float,
        nextZ: Float,
        monster: MonsterComponent,
        groundY: Float,
        logTag: String = TAG,
    ): NavigationPoint {
        val expectedY = bodyCenterY(groundY, monster)
        val drift = abs(current.y - expectedY)
        if (drift > MAX_GROUND_DRIFT_METERS) {
            Log.w(
                logTag,
                "${monster.monsterType.name} ground drift ${"%.3f".format(drift)}m corrected " +
                    "(y=${"%.3f".format(current.y)} -> ${"%.3f".format(expectedY)}, " +
                    "ground=${"%.3f".format(groundY)}, anchor=${monster.monsterType.anchorOffsetY})",
            )
        }
        return NavigationPoint(nextX, expectedY, nextZ)
    }

    /** Death/dying keeps root Y fixed; only visuals may animate. */
    fun lockDeathY(
        current: NavigationPoint,
        monster: MonsterComponent,
        groundY: Float,
    ): NavigationPoint =
        NavigationPoint(current.x, bodyCenterY(groundY, monster), current.z)

    fun logSpawn(
        type: MonsterType,
        groundY: Float,
        point: NavigationPoint,
        logTag: String = TAG,
    ) {
        Log.i(
            logTag,
            "Grounded spawn ${type.displayName}: y=${"%.3f".format(point.y)}, " +
                "ground=${"%.3f".format(groundY)}, anchor=${type.anchorOffsetY}, " +
                "foot=${"%.3f".format(point.y - type.anchorOffsetY)}",
        )
    }

    fun logMove(
        type: MonsterType,
        groundY: Float,
        point: NavigationPoint,
        logTag: String = TAG,
    ) {
        if (!DEBUG_VERBOSE_MOVE_LOG) return
        Log.d(
            logTag,
            "Grounded move ${type.name}: y=${"%.3f".format(point.y)}, " +
                "ground=${"%.3f".format(groundY)}, anchor=${type.anchorOffsetY}",
        )
    }

    fun groundMarkerLocalY(type: MonsterType): Float = -type.anchorOffsetY

    fun groundMarkerY(groundY: Float): Float = groundY + GROUND_MARKER_LIFT_METERS

    private const val DEBUG_VERBOSE_MOVE_LOG = false
    private const val GROUND_MARKER_LIFT_METERS = 0.004f
    private const val TAG = "MonsterGrounding"
}
