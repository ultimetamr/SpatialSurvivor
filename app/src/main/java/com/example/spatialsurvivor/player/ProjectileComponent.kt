package com.example.spatialsurvivor.player

import com.pico.spatial.core.ecs.Component

/** Pooled straight-line energy projectile state. */
class ProjectileComponent : Component() {
    var active: Boolean = false
    var directionX: Float = 0f
    var directionY: Float = 0f
    var directionZ: Float = -1f
    var speedMetersPerSecond: Float = PlayerStats.DEFAULT_PROJECTILE_SPEED_METERS_PER_SECOND
    var remainingDistanceMeters: Float = 0f
    var damage: Int = PlayerStats.DEFAULT_PROJECTILE_DAMAGE
    var remainingPierces: Int = 0

    companion object {
        const val COLLISION_RADIUS_METERS = 0.08f
    }
}
