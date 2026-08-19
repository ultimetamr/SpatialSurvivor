package com.example.spatialsurvivor.player

import android.util.Log
import com.example.spatialsurvivor.game.GameRuntime
import com.example.spatialsurvivor.game.GameSessionRuntime
import kotlin.math.ceil
import kotlin.random.Random

object PlayerDamageRuntime {
    fun applyDamage(player: PlayerComponent, damage: Int, source: String): Boolean {
        if (player.isGameOver || damage <= 0 || GameSessionRuntime.settlementVisible) return false
        if (player.dodgeChance > 0f && Random.Default.nextFloat() < player.dodgeChance) {
            Log.i(TAG, "$source attack dodged: chance=${player.dodgeChance}")
            return false
        }
        val nearDeathReduction =
            if (player.nearDeathProtectionOwned && player.currentHealth * 5 <= player.maxHealth) 0.5f else 0f
        val effectiveDamage = ceil(damage * (1f - player.damageReduction) * (1f - nearDeathReduction))
            .toInt().coerceAtLeast(1)
        player.currentHealth = PlayerCombatRules.healthAfterDamage(player.currentHealth, effectiveDamage)
        player.damageEventSequence += 1L
        Log.i(TAG, "$source damaged player: raw=$damage, applied=$effectiveDamage, health=${player.currentHealth}")
        if (player.currentHealth == 0) {
            player.isGameOver = true
            GameSessionRuntime.finishDefeat(GameRuntime.elapsedGameSeconds)
            Log.i(TAG, "Player health reached zero; defeat settlement triggered")
            return true
        }
        return false
    }

    private const val TAG = "PlayerDamageRuntime"
}
