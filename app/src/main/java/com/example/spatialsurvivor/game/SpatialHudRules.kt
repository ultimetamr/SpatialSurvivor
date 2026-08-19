package com.example.spatialsurvivor.game

import com.example.spatialsurvivor.upgrade.UpgradeId
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

data class ActiveSkillHud(
    val id: UpgradeId,
    val stacks: Int,
)

data class HudTimeParts(
    val minutes: Int,
    val seconds: Int,
)

data class ViewHudPlacement(
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val forwardX: Float,
    val forwardZ: Float,
    val yawDegrees: Float,
)

/** Pure presentation rules shared by the fixed-step HUD pose system and Compose panels. */
object SpatialHudRules {
    fun remainingSeconds(elapsedSeconds: Float): Int =
        ceil((WaveRules.FINAL_BOSS_TIME_SECONDS - elapsedSeconds).coerceAtLeast(0f)).toInt()

    fun timeParts(totalSeconds: Int): HudTimeParts =
        HudTimeParts(
            minutes = totalSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE,
            seconds = totalSeconds.coerceAtLeast(0) % SECONDS_PER_MINUTE,
        )

    fun experienceProgress(current: Int, required: Int): Float =
        if (required <= 0) 0f else (current.toFloat() / required).coerceIn(0f, 1f)

    fun healthProgress(current: Int, maximum: Int): Float =
        if (maximum <= 0) 0f else (current.toFloat() / maximum).coerceIn(0f, 1f)

    /** Resolves an upright HUD 1.2m ahead and 15 degrees below the HMD gaze. */
    fun viewHudPlacement(
        headX: Float,
        headY: Float,
        headZ: Float,
        rawForwardX: Float,
        rawForwardZ: Float,
    ): ViewHudPlacement {
        val horizontalLength =
            kotlin.math.sqrt(rawForwardX * rawForwardX + rawForwardZ * rawForwardZ)
        val forwardX = if (horizontalLength > MIN_DIRECTION_LENGTH) rawForwardX / horizontalLength else 0f
        val forwardZ = if (horizontalLength > MIN_DIRECTION_LENGTH) rawForwardZ / horizontalLength else -1f
        val horizontalDistance =
            VIEW_HUD_DISTANCE_METERS * cos(VIEW_HUD_DOWN_ANGLE_RADIANS).toFloat()
        return ViewHudPlacement(
            centerX = headX + forwardX * horizontalDistance,
            centerY =
                headY - VIEW_HUD_DISTANCE_METERS * sin(VIEW_HUD_DOWN_ANGLE_RADIANS).toFloat(),
            centerZ = headZ + forwardZ * horizontalDistance,
            forwardX = forwardX,
            forwardZ = forwardZ,
            // Panel local +Z faces the viewer, opposite the HMD's horizontal view direction.
            yawDegrees = (kotlin.math.atan2(-forwardX, -forwardZ) * 180.0 / Math.PI).toFloat(),
        )
    }

    /** Exponential smoothing makes 0.1 seconds independent of the fixed-step rate. */
    fun viewHudFollowLerp(deltaSeconds: Float): Float =
        (
            1f -
                exp(
                    (-deltaSeconds.coerceAtLeast(0f) / VIEW_HUD_FOLLOW_SECONDS).toDouble(),
                ).toFloat()
        ).coerceIn(0f, 1f)

    /** Large head turns receive a bounded recenter boost so the HUD remains in peripheral view. */
    fun shouldRecenterViewHud(
        headX: Float,
        headZ: Float,
        currentX: Float,
        currentZ: Float,
        desiredForwardX: Float,
        desiredForwardZ: Float,
    ): Boolean {
        val offsetX = currentX - headX
        val offsetZ = currentZ - headZ
        val length = kotlin.math.sqrt(offsetX * offsetX + offsetZ * offsetZ)
        if (length <= MIN_DIRECTION_LENGTH) return false
        val dot = (offsetX / length) * desiredForwardX + (offsetZ / length) * desiredForwardZ
        return dot < RECENTER_DOT_THRESHOLD
    }

    fun hudTargetAlpha(dimmedForOverlay: Boolean): Float =
        if (dimmedForOverlay) OVERLAY_DIMMED_ALPHA else 1f

    fun shouldDimForOverlay(settlementVisible: Boolean, upgradeVisible: Boolean = false): Boolean =
        settlementVisible || upgradeVisible

    fun activeSkills(
        orbitingSwordStacks: Int = 0,
        energyProjectileStacks: Int = 0,
        chainLightningStacks: Int = 0,
        poisonAuraStacks: Int = 0,
        piercingIceConeStacks: Int = 0,
        lavaBombStacks: Int = 0,
        gravityBlackHoleStacks: Int = 0,
        swordRainStacks: Int = 0,
        lightningDomainStacks: Int = 0,
    ): List<ActiveSkillHud> =
        buildList {
            if (orbitingSwordStacks > 0) add(ActiveSkillHud(UpgradeId.ORBITING_SWORD, orbitingSwordStacks))
            if (energyProjectileStacks > 0) add(ActiveSkillHud(UpgradeId.ENERGY_PROJECTILE, energyProjectileStacks))
            if (chainLightningStacks > 0) add(ActiveSkillHud(UpgradeId.CHAIN_LIGHTNING, chainLightningStacks))
            if (poisonAuraStacks > 0) add(ActiveSkillHud(UpgradeId.POISON_AURA, poisonAuraStacks))
            if (piercingIceConeStacks > 0) add(ActiveSkillHud(UpgradeId.PIERCING_ICE_CONE, piercingIceConeStacks))
            if (lavaBombStacks > 0) add(ActiveSkillHud(UpgradeId.LAVA_BOMB, lavaBombStacks))
            if (gravityBlackHoleStacks > 0) add(ActiveSkillHud(UpgradeId.GRAVITY_BLACK_HOLE, gravityBlackHoleStacks))
            if (swordRainStacks > 0) add(ActiveSkillHud(UpgradeId.SWORD_RAIN, swordRainStacks))
            if (lightningDomainStacks > 0) add(ActiveSkillHud(UpgradeId.LIGHTNING_DOMAIN, lightningDomainStacks))
        }

    const val VIEW_HUD_DISTANCE_METERS = 1.2f
    const val VIEW_HUD_DOWN_ANGLE_DEGREES = 15f
    const val OVERLAY_DIMMED_ALPHA = 0.5f
    const val VIEW_HUD_FOLLOW_SECONDS = 0.1f
    const val VIEW_HUD_RECENTER_LERP = 0.38f
    private const val VIEW_HUD_DOWN_ANGLE_RADIANS = VIEW_HUD_DOWN_ANGLE_DEGREES * (Math.PI / 180.0)
    private const val RECENTER_DOT_THRESHOLD = 0.70710677f
    private const val MIN_DIRECTION_LENGTH = 0.0001f
    private const val SECONDS_PER_MINUTE = 60
}
