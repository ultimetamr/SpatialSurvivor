package com.example.spatialsurvivor.game

import com.example.spatialsurvivor.monster.MonsterType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** Pure deterministic wave progression and cumulative difficulty scaling. */
object WaveRules {
    fun waveAt(elapsedSeconds: Float): Int =
        (floor(elapsedSeconds.coerceAtLeast(0f) / WAVE_DURATION_SECONDS).toInt() + 1)
            .coerceAtMost(FINAL_WAVE)

    fun healthMultiplier(wave: Int): Float =
        HEALTH_MULTIPLIER_PER_WAVE.pow((wave.coerceAtLeast(1) - 1).toFloat())

    fun speedMultiplier(wave: Int): Float =
        SPEED_MULTIPLIER_PER_WAVE.pow((wave.coerceAtLeast(1) - 1).toFloat())

    fun quantityMultiplier(wave: Int): Float =
        QUANTITY_MULTIPLIER_PER_WAVE.pow((wave.coerceAtLeast(1) - 1).toFloat())

    fun activeMonsterLimit(wave: Int): Int =
        ceil(BASE_ACTIVE_MONSTERS * quantityMultiplier(wave)).toInt()
            .coerceAtMost(MAX_POOLED_NORMAL_MONSTERS)

    fun unlockedTypes(wave: Int): List<MonsterType> =
        buildList {
            add(MonsterType.NORMAL_BUG)
            if (wave >= RUNNER_UNLOCK_WAVE) add(MonsterType.RUNNER)
            if (wave >= ARMORED_UNLOCK_WAVE) add(MonsterType.ARMORED)
            if (wave >= CEILING_DROPPER_UNLOCK_WAVE) add(MonsterType.CEILING_DROPPER)
        }

    fun shouldSpawnFinalBoss(elapsedSeconds: Float): Boolean =
        elapsedSeconds >= FINAL_BOSS_TIME_SECONDS

    const val WAVE_DURATION_SECONDS = 120f
    const val FINAL_BOSS_TIME_SECONDS = 10f * 60f
    const val FINAL_WAVE = 10
    const val BASE_ACTIVE_MONSTERS = 12
    const val MAX_POOLED_NORMAL_MONSTERS = 18
    const val HEALTH_MULTIPLIER_PER_WAVE = 1.2f
    const val QUANTITY_MULTIPLIER_PER_WAVE = 1.15f
    const val SPEED_MULTIPLIER_PER_WAVE = 1.1f
    const val RUNNER_UNLOCK_WAVE = 2
    const val ARMORED_UNLOCK_WAVE = 3
    const val CEILING_DROPPER_UNLOCK_WAVE = 4
}
