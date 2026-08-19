package com.example.spatialsurvivor.monster

/** Pure rules shared by every monster archetype's close-range attack. */
object MonsterAttackRules {
    fun pursuitSpeedMultiplier(distanceToPlayerMeters: Float): Float =
        if (distanceToPlayerMeters <= AGGRO_SPRINT_RADIUS_METERS) {
            AGGRO_SPRINT_SPEED_MULTIPLIER
        } else {
            1f
        }

    fun canMeleeAttack(
        movementState: MonsterMovementState,
        horizontalDistanceSquared: Float,
        monsterRadiusMeters: Float,
        playerRadiusMeters: Float,
        cooldownSeconds: Float,
    ): Boolean {
        if (movementState != MonsterMovementState.CHASING || cooldownSeconds > 0f) return false
        val attackDistance =
            monsterRadiusMeters.coerceAtLeast(0f) +
                playerRadiusMeters.coerceAtLeast(0f) +
                MELEE_REACH_METERS
        return horizontalDistanceSquared <= attackDistance * attackDistance
    }

    /** Small reach beyond collider contact makes the monster's attack intent perceptible. */
    const val MELEE_REACH_METERS = 0.18f
    const val AGGRO_SPRINT_RADIUS_METERS = 2.5f
    const val AGGRO_SPRINT_SPEED_MULTIPLIER = 1.35f
}
