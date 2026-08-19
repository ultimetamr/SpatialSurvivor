package com.example.spatialsurvivor.player

/** Immutable defaults for the room-scale player. All distances are meters. */
data class PlayerStats(
    val maxHealth: Int = DEFAULT_MAX_HEALTH,
    val attackRangeMeters: Float = DEFAULT_ATTACK_RANGE_METERS,
    val attackIntervalSeconds: Float = DEFAULT_ATTACK_INTERVAL_SECONDS,
    val pickupRangeMeters: Float = DEFAULT_PICKUP_RANGE_METERS,
    val projectileDamage: Int = DEFAULT_PROJECTILE_DAMAGE,
    val projectileSpeedMetersPerSecond: Float = DEFAULT_PROJECTILE_SPEED_METERS_PER_SECOND,
) {
    companion object {
        const val DEFAULT_MAX_HEALTH = 100
        const val DEFAULT_ATTACK_RANGE_METERS = 2f
        const val DEFAULT_ATTACK_INTERVAL_SECONDS = 1f
        const val DEFAULT_PICKUP_RANGE_METERS = 0.5f
        const val DEFAULT_PROJECTILE_DAMAGE = 20
        const val DEFAULT_PROJECTILE_SPEED_METERS_PER_SECOND = 6f
    }
}

data class CombatTarget(
    val id: String,
    val x: Float,
    val z: Float,
    val active: Boolean = true,
)

/** Pure combat rules kept independent from the Spatial runtime for deterministic tests. */
object PlayerCombatRules {
    fun nearestTargetIndex(
        playerX: Float,
        playerZ: Float,
        attackRangeMeters: Float,
        targets: List<CombatTarget>,
    ): Int? {
        val maximumDistanceSquared = attackRangeMeters * attackRangeMeters
        var nearestIndex: Int? = null
        var nearestDistanceSquared = maximumDistanceSquared

        targets.forEachIndexed { index, target ->
            if (!target.active) return@forEachIndexed
            val dx = target.x - playerX
            val dz = target.z - playerZ
            val distanceSquared = dx * dx + dz * dz
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared
                nearestIndex = index
            }
        }
        return nearestIndex
    }

    fun healthAfterDamage(currentHealth: Int, damage: Int): Int =
        (currentHealth - damage.coerceAtLeast(0)).coerceAtLeast(0)
}
