package com.example.spatialsurvivor.monster

import android.util.Log
import com.example.spatialsurvivor.game.GameRuntime
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.player.PlayerCombatRules
import com.pico.spatial.core.ecs.Entity

object MonsterDamageRuntime {
    fun applyDamage(entity: Entity, damage: Int, source: String): Boolean {
        val monster = entity.components[MonsterComponent::class.java] ?: return false
        if (!monster.active || damage <= 0) return false
        monster.currentHealth = PlayerCombatRules.healthAfterDamage(monster.currentHealth, damage)
        CombatFeedbackRuntime.requestHit(monster)
        Log.i(TAG, "$source hit monster: damage=$damage, remainingHealth=${monster.currentHealth}")
        if (monster.currentHealth == 0) {
            monster.beginDeath()
            GameSessionRuntime.recordMonsterDefeated(monster.monsterType, GameRuntime.elapsedGameSeconds)
            Log.i(TAG, "Monster defeated by $source; disappearance effect started")
            return true
        }
        return false
    }

    private const val TAG = "MonsterDamageRuntime"
}
