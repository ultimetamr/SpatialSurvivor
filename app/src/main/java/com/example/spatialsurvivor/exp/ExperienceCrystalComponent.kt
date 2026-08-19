package com.example.spatialsurvivor.exp

import com.pico.spatial.core.ecs.Component

enum class ExperienceCrystalState {
    INACTIVE,
    FLOATING,
    ATTRACTING,
}

/** Runtime state for a pooled EXP crystal SpatialEntity. */
class ExperienceCrystalComponent : Component() {
    var state: ExperienceCrystalState = ExperienceCrystalState.INACTIVE
    var experienceValue: Int = 0
    var hoverCenterWorldY: Float = 0f
    var animationSeconds: Float = 0f
    var rotationDegrees: Float = 0f
    var attractionSpeedMetersPerSecond: Float = INITIAL_ATTRACTION_SPEED_METERS_PER_SECOND
    /** Lightweight gameplay sphere; intentionally not registered with Scene Mesh physics. */
    val pickupCollisionRadiusMeters: Float = PICKUP_COLLISION_RADIUS_METERS

    val active: Boolean
        get() = state != ExperienceCrystalState.INACTIVE

    fun activate(value: Int, centerWorldY: Float) {
        state = ExperienceCrystalState.FLOATING
        experienceValue = value.coerceAtLeast(0)
        hoverCenterWorldY = centerWorldY
        animationSeconds = 0f
        rotationDegrees = 0f
        attractionSpeedMetersPerSecond = INITIAL_ATTRACTION_SPEED_METERS_PER_SECOND
    }

    fun deactivate() {
        state = ExperienceCrystalState.INACTIVE
        experienceValue = 0
        animationSeconds = 0f
        attractionSpeedMetersPerSecond = INITIAL_ATTRACTION_SPEED_METERS_PER_SECOND
    }

    companion object {
        const val INITIAL_ATTRACTION_SPEED_METERS_PER_SECOND = 1.6f
        const val PICKUP_COLLISION_RADIUS_METERS = 0.10f
    }
}
