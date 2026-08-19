package com.example.spatialsurvivor.game

import com.example.spatialsurvivor.monster.NavigationPoint
import com.example.spatialsurvivor.monster.SpatialNavigationMap
import com.example.spatialsurvivor.player.PlayerComponent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

data class WorldLockedPanelPlacement(
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val yawDegrees: Float,
    val forwardX: Float,
    val forwardZ: Float,
    val retreatedForClearance: Boolean,
)

/**
 * Pure placement / FOV-loss rules shared by upgrade, settlement, permanent, pause, and main-menu panels.
 * Idle panels stay world-locked; only large turn/walk drift recenters them.
 */
object WorldLockedPanelPlacementRules {
    const val UPGRADE_DISTANCE_METERS = 1.0f
    /** One-shot world lock: 1.0 m ahead of HMD (no continuous head follow). */
    const val OVERLAY_DISTANCE_METERS = 1.0f
    const val BACK_LAYER_DISTANCE_METERS = 1.0f
    const val FRONT_LAYER_DISTANCE_METERS = 1.0f
    const val PANEL_DISTANCE_METERS = 1.0f
    const val OBSTACLE_CLEARANCE_METERS = 0.25f
    const val RECENTER_ANGLE_DEGREES = 90f
    const val RECENTER_DISTANCE_DRIFT_METERS = 0.8f
    const val RECENTER_SECONDS = 0.3f

    fun placeInFrontOfPlayer(
        headX: Float,
        headY: Float,
        headZ: Float,
        rawForwardX: Float,
        rawForwardZ: Float,
        navigation: SpatialNavigationMap? = null,
        distanceMeters: Float = PANEL_DISTANCE_METERS,
        clearanceMeters: Float = OBSTACLE_CLEARANCE_METERS,
    ): WorldLockedPanelPlacement {
        val (forwardX, forwardZ) = normalizeHorizontal(rawForwardX, rawForwardZ)
        val idealX = headX + forwardX * distanceMeters
        val idealZ = headZ + forwardZ * distanceMeters
        val yawDegrees = (atan2(-forwardX, -forwardZ) * 180.0 / PI).toFloat()
        val resolved =
            resolveClearPlacement(
                idealX = idealX,
                idealY = headY,
                idealZ = idealZ,
                headX = headX,
                headZ = headZ,
                intendedDistanceMeters = distanceMeters,
                navigation = navigation,
                clearanceMeters = clearanceMeters,
            )
        return WorldLockedPanelPlacement(
            centerX = resolved.x,
            centerY = headY,
            centerZ = resolved.z,
            yawDegrees = yawDegrees,
            forwardX = forwardX,
            forwardZ = forwardZ,
            retreatedForClearance = resolved.retreated,
        )
    }

    fun horizontalDistanceMeters(
        headX: Float,
        headZ: Float,
        panelX: Float,
        panelZ: Float,
    ): Float = hypot((panelX - headX).toDouble(), (panelZ - headZ).toDouble()).toFloat()

    /** Horizontal angle in degrees between the player's view forward and the vector toward the panel. */
    fun horizontalAngleDegreesToPanel(
        headX: Float,
        headZ: Float,
        rawForwardX: Float,
        rawForwardZ: Float,
        panelX: Float,
        panelZ: Float,
    ): Float {
        val (forwardX, forwardZ) = normalizeHorizontal(rawForwardX, rawForwardZ)
        val toX = panelX - headX
        val toZ = panelZ - headZ
        val length = hypot(toX.toDouble(), toZ.toDouble()).toFloat()
        if (length <= MIN_DIRECTION_LENGTH) return 0f
        val nx = toX / length
        val nz = toZ / length
        val dot = (nx * forwardX + nz * forwardZ).coerceIn(-1f, 1f)
        return (acos(dot.toDouble()) * 180.0 / PI).toFloat()
    }

    fun shouldRecenter(
        headX: Float,
        headZ: Float,
        rawForwardX: Float,
        rawForwardZ: Float,
        panelX: Float,
        panelZ: Float,
        intendedDistanceMeters: Float,
        angleThresholdDegrees: Float = RECENTER_ANGLE_DEGREES,
        distanceDriftMeters: Float = RECENTER_DISTANCE_DRIFT_METERS,
    ): Boolean {
        val angle =
            horizontalAngleDegreesToPanel(
                headX = headX,
                headZ = headZ,
                rawForwardX = rawForwardX,
                rawForwardZ = rawForwardZ,
                panelX = panelX,
                panelZ = panelZ,
            )
        if (angle > angleThresholdDegrees) return true
        val distance = horizontalDistanceMeters(headX, headZ, panelX, panelZ)
        // Only large walk-away / walk-into counts. Clearance may pull the panel slightly closer
        // than intended; that must NOT re-trigger recenter or the panel will pulse forever.
        if (distance > intendedDistanceMeters + distanceDriftMeters) return true
        val tooCloseLimit =
            (intendedDistanceMeters - distanceDriftMeters).coerceAtLeast(MIN_STABLE_DISTANCE_METERS)
        return distance < tooCloseLimit
    }

    fun lerpAngleDegrees(fromDegrees: Float, toDegrees: Float, t: Float): Float {
        var delta = (toDegrees - fromDegrees) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return fromDegrees + delta * t.coerceIn(0f, 1f)
    }

    private fun resolveClearPlacement(
        idealX: Float,
        idealY: Float,
        idealZ: Float,
        headX: Float,
        headZ: Float,
        intendedDistanceMeters: Float,
        navigation: SpatialNavigationMap?,
        clearanceMeters: Float,
    ): ResolvedPoint {
        if (navigation == null || !navigation.isAvailable) {
            return ResolvedPoint(idealX, idealZ, retreated = false)
        }
        val ideal = NavigationPoint(idealX, idealY, idealZ)
        if (navigation.hasObstacleClearance(ideal, clearanceMeters)) {
            return ResolvedPoint(idealX, idealZ, retreated = false)
        }

        // Pull toward the player (never toward room center). Room-center retreat could push a
        // panel several meters deeper into the play space when the wearer stands near a wall.
        var currentX = idealX
        var currentZ = idealZ
        for (step in 0 until MAX_RETREAT_STEPS) {
            val dx = headX - currentX
            val dz = headZ - currentZ
            val length = hypot(dx.toDouble(), dz.toDouble()).toFloat()
            if (length <= MIN_DIRECTION_LENGTH) break
            currentX += dx / length * RETREAT_STEP_METERS
            currentZ += dz / length * RETREAT_STEP_METERS
            val candidate = NavigationPoint(currentX, idealY, currentZ)
            if (navigation.hasObstacleClearance(candidate, clearanceMeters)) {
                return clampDistanceFromHead(
                    pointX = currentX,
                    pointZ = currentZ,
                    headX = headX,
                    headZ = headZ,
                    minDistanceMeters = MIN_STABLE_DISTANCE_METERS,
                    maxDistanceMeters = intendedDistanceMeters,
                    retreated = true,
                )
            }
        }

        // Last resort: keep the intended forward point even if slightly tight on clearance,
        // rather than jumping to the room center (which feels "far away").
        return ResolvedPoint(idealX, idealZ, retreated = true)
    }

    /**
     * Keep retreated panels inside a stable band so distance-based recenter cannot oscillate.
     */
    private fun clampDistanceFromHead(
        pointX: Float,
        pointZ: Float,
        headX: Float,
        headZ: Float,
        minDistanceMeters: Float,
        maxDistanceMeters: Float,
        retreated: Boolean,
    ): ResolvedPoint {
        val dx = pointX - headX
        val dz = pointZ - headZ
        val distance = hypot(dx.toDouble(), dz.toDouble()).toFloat()
        if (distance <= MIN_DIRECTION_LENGTH) {
            return ResolvedPoint(pointX, pointZ, retreated = retreated)
        }
        val clamped =
            distance.coerceIn(minDistanceMeters, maxDistanceMeters.coerceAtLeast(minDistanceMeters))
        if (abs(clamped - distance) <= MIN_DIRECTION_LENGTH) {
            return ResolvedPoint(pointX, pointZ, retreated = retreated)
        }
        val scale = clamped / distance
        return ResolvedPoint(headX + dx * scale, headZ + dz * scale, retreated = retreated)
    }

    private fun normalizeHorizontal(rawX: Float, rawZ: Float): Pair<Float, Float> {
        val length = sqrt(rawX * rawX + rawZ * rawZ)
        return if (length > MIN_DIRECTION_LENGTH) {
            (rawX / length) to (rawZ / length)
        } else {
            0f to -1f
        }
    }

    private data class ResolvedPoint(val x: Float, val z: Float, val retreated: Boolean)

    private const val MIN_DIRECTION_LENGTH = 0.0001f
    private const val RETREAT_STEP_METERS = 0.15f
    private const val MAX_RETREAT_STEPS = 24
    /** Floor for clearance pull-back; below this, distance recenter and near-plane flicker thrash. */
    const val MIN_STABLE_DISTANCE_METERS = 0.40f
    const val RECENTER_COOLDOWN_SECONDS = 0.6f
}

/** Per-panel world-lock + optional 0.3s FOV-loss recenter. */
data class WorldLockedPoseTick(
    /** True only when position/rotation must be written to the Transform this frame. */
    val applyTransform: Boolean,
    val justLocked: Boolean,
)

class WorldLockedPanelPose(
    private val distanceMeters: Float,
) {
    var isLocked: Boolean = false
        private set
    var isRecentering: Boolean = false
        private set

    var x: Float = 0f
        private set
    var y: Float = PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS
        private set
    var z: Float = -distanceMeters
        private set
    var yawDegrees: Float = 0f
        private set

    private var recenterElapsedSeconds = 0f
    private var recenterCooldownRemainingSeconds = 0f
    private var fromX = 0f
    private var fromY = PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS
    private var fromZ = -distanceMeters
    private var fromYaw = 0f
    private var toX = 0f
    private var toY = PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS
    private var toZ = -distanceMeters
    private var toYaw = 0f

    fun clear() {
        isLocked = false
        isRecentering = false
        recenterElapsedSeconds = 0f
        recenterCooldownRemainingSeconds = 0f
    }

    fun lockTo(placement: WorldLockedPanelPlacement) {
        x = placement.centerX
        y = placement.centerY
        z = placement.centerZ
        yawDegrees = placement.yawDegrees
        isLocked = true
        isRecentering = false
        recenterElapsedSeconds = 0f
        recenterCooldownRemainingSeconds = WorldLockedPanelPlacementRules.RECENTER_COOLDOWN_SECONDS
    }

    /**
     * Idle locked panels do not mutate pose. Writes happen only on first lock and during recenter.
     */
    fun tick(
        deltaSeconds: Float,
        headX: Float,
        headY: Float,
        headZ: Float,
        rawForwardX: Float,
        rawForwardZ: Float,
        navigation: SpatialNavigationMap?,
    ): WorldLockedPoseTick {
        val dt = deltaSeconds.coerceAtLeast(0f)
        if (recenterCooldownRemainingSeconds > 0f) {
            recenterCooldownRemainingSeconds = (recenterCooldownRemainingSeconds - dt).coerceAtLeast(0f)
        }

        if (!isLocked) {
            lockTo(
                WorldLockedPanelPlacementRules.placeInFrontOfPlayer(
                    headX = headX,
                    headY = headY,
                    headZ = headZ,
                    rawForwardX = rawForwardX,
                    rawForwardZ = rawForwardZ,
                    navigation = navigation,
                    distanceMeters = distanceMeters,
                ),
            )
            return WorldLockedPoseTick(applyTransform = true, justLocked = true)
        }

        if (isRecentering) {
            recenterElapsedSeconds += dt
            val t =
                (recenterElapsedSeconds / WorldLockedPanelPlacementRules.RECENTER_SECONDS)
                    .coerceIn(0f, 1f)
            x = fromX + (toX - fromX) * t
            y = fromY + (toY - fromY) * t
            z = fromZ + (toZ - fromZ) * t
            yawDegrees = WorldLockedPanelPlacementRules.lerpAngleDegrees(fromYaw, toYaw, t)
            if (t >= 1f) {
                x = toX
                y = toY
                z = toZ
                yawDegrees = toYaw
                isRecentering = false
                recenterCooldownRemainingSeconds =
                    WorldLockedPanelPlacementRules.RECENTER_COOLDOWN_SECONDS
            }
            return WorldLockedPoseTick(applyTransform = true, justLocked = false)
        }

        if (recenterCooldownRemainingSeconds > 0f) {
            return WorldLockedPoseTick(applyTransform = false, justLocked = false)
        }

        val needsRecenter =
            WorldLockedPanelPlacementRules.shouldRecenter(
                headX = headX,
                headZ = headZ,
                rawForwardX = rawForwardX,
                rawForwardZ = rawForwardZ,
                panelX = x,
                panelZ = z,
                intendedDistanceMeters = distanceMeters,
            )
        if (!needsRecenter) {
            // Hard idle: zero position/rotation mutation while thresholds are not met.
            return WorldLockedPoseTick(applyTransform = false, justLocked = false)
        }

        val target =
            WorldLockedPanelPlacementRules.placeInFrontOfPlayer(
                headX = headX,
                headY = headY,
                headZ = headZ,
                rawForwardX = rawForwardX,
                rawForwardZ = rawForwardZ,
                navigation = navigation,
                distanceMeters = distanceMeters,
            )
        fromX = x
        fromY = y
        fromZ = z
        fromYaw = yawDegrees
        toX = target.centerX
        toY = target.centerY
        toZ = target.centerZ
        toYaw = target.yawDegrees
        isRecentering = true
        recenterElapsedSeconds = 0f
        return WorldLockedPoseTick(applyTransform = false, justLocked = false)
    }
}
