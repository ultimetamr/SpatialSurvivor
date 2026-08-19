package com.example.spatialsurvivor.exp

import kotlin.math.ceil
import kotlin.math.sin

data class ExperienceState(
    val level: Int = ExperienceProgressionRules.DEFAULT_LEVEL,
    val currentExperience: Int = 0,
    val experienceRequired: Int = ExperienceProgressionRules.DEFAULT_EXPERIENCE_REQUIRED,
)

data class ExperienceGainResult(
    val state: ExperienceState,
    val leveledUp: Boolean,
)

/** Pure deterministic progression rules used by the Spatial ECS runtime and tests. */
object ExperienceProgressionRules {
    fun applyGain(state: ExperienceState, amount: Int): ExperienceGainResult {
        val requirement = state.experienceRequired.coerceAtLeast(1)
        val total = state.currentExperience.coerceAtLeast(0) + amount.coerceAtLeast(0)
        if (total < requirement) {
            return ExperienceGainResult(
                state = state.copy(currentExperience = total, experienceRequired = requirement),
                leveledUp = false,
            )
        }

        val nextRequirement =
            ceil(requirement * EXPERIENCE_REQUIREMENT_MULTIPLIER)
                .toInt()
                .coerceAtLeast(requirement + 1)
        return ExperienceGainResult(
            state =
                ExperienceState(
                    level = state.level.coerceAtLeast(DEFAULT_LEVEL) + 1,
                    currentExperience = 0,
                    experienceRequired = nextRequirement,
                ),
            leveledUp = true,
        )
    }

    fun expandedPickupRange(currentMeters: Float, increaseMeters: Float): Float =
        (currentMeters + increaseMeters.coerceAtLeast(0f))
            .coerceIn(MINIMUM_PICKUP_RANGE_METERS, MAXIMUM_PICKUP_RANGE_METERS)

    const val DEFAULT_LEVEL = 1
    const val DEFAULT_EXPERIENCE_REQUIRED = 5
    const val EXPERIENCE_REQUIREMENT_MULTIPLIER = 1.5
    const val MINIMUM_PICKUP_RANGE_METERS = 0.1f
    const val MAXIMUM_PICKUP_RANGE_METERS = 8f
}

object ExperienceCrystalMotionRules {
    fun isWithinPickupRangeXZ(
        crystalX: Float,
        crystalZ: Float,
        playerX: Float,
        playerZ: Float,
        pickupRangeMeters: Float,
    ): Boolean {
        val dx = crystalX - playerX
        val dz = crystalZ - playerZ
        val safeRange = pickupRangeMeters.coerceAtLeast(0f)
        return dx * dx + dz * dz <= safeRange * safeRange
    }

    fun hoverOffsetMeters(
        elapsedSeconds: Float,
        amplitudeMeters: Float,
        cyclesPerSecond: Float,
    ): Float =
        sin(elapsedSeconds * TWO_PI * cyclesPerSecond).toFloat() * amplitudeMeters

    fun spheresOverlap(
        distanceMeters: Float,
        firstRadiusMeters: Float,
        secondRadiusMeters: Float,
    ): Boolean {
        val combinedRadius =
            firstRadiusMeters.coerceAtLeast(0f) + secondRadiusMeters.coerceAtLeast(0f)
        return distanceMeters.coerceAtLeast(0f) <= combinedRadius
    }

    private const val TWO_PI = (Math.PI * 2.0).toFloat()
}
