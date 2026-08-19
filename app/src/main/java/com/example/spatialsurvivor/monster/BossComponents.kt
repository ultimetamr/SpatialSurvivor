package com.example.spatialsurvivor.monster

import com.pico.spatial.core.ecs.Component

class FinalBossComponent : Component() {
    var spawnedThisRun: Boolean = false
    var areaAttackCooldownSeconds: Float = INITIAL_AREA_ATTACK_DELAY_SECONDS
    var telegraphRemainingSeconds: Float = 0f

    fun reset() {
        spawnedThisRun = false
        areaAttackCooldownSeconds = INITIAL_AREA_ATTACK_DELAY_SECONDS
        telegraphRemainingSeconds = 0f
    }

    companion object {
        const val INITIAL_AREA_ATTACK_DELAY_SECONDS = 2.5f
    }
}

class BossAreaVisualComponent : Component()

object BossCombatRules {
    fun isInsideAreaAttack(
        playerX: Float,
        playerZ: Float,
        bossX: Float,
        bossZ: Float,
        radiusMeters: Float = AREA_ATTACK_RADIUS_METERS,
    ): Boolean {
        val dx = playerX - bossX
        val dz = playerZ - bossZ
        return dx * dx + dz * dz <= radiusMeters * radiusMeters
    }

    const val AREA_ATTACK_RADIUS_METERS = 2.5f
    const val AREA_ATTACK_DAMAGE = 28
    const val AREA_ATTACK_COOLDOWN_SECONDS = 5f
    const val AREA_ATTACK_TELEGRAPH_SECONDS = 1.25f
}
