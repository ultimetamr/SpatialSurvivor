package com.example.spatialsurvivor.monster

/** Pure distance LOD policy for normal-monster AI updates. */
object MonsterUpdateBudgetRules {
    fun cadenceTicks(distanceToPlayerMeters: Float): Int =
        when {
            distanceToPlayerMeters <= FULL_RATE_DISTANCE_METERS -> 1
            distanceToPlayerMeters <= HALF_RATE_DISTANCE_METERS -> 2
            else -> 4
        }

    fun shouldUpdate(
        simulationTick: Long,
        phase: Int,
        cadenceTicks: Int,
    ): Boolean {
        val safeCadence = cadenceTicks.coerceAtLeast(1)
        return (simulationTick + phase.toLong()).mod(safeCadence.toLong()) == 0L
    }

    const val FULL_RATE_DISTANCE_METERS = 6f
    const val HALF_RATE_DISTANCE_METERS = 10f
    const val MAX_ACCUMULATED_DELTA_SECONDS = 0.10f
}
