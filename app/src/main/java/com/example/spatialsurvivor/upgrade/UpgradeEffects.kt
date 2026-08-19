package com.example.spatialsurvivor.upgrade

import com.example.spatialsurvivor.player.PlayerComponent

data class AppliedUpgrade(val id: UpgradeId, val resultingStack: Int)

object UpgradeEffects {
    fun apply(player: PlayerComponent, id: UpgradeId): AppliedUpgrade {
        val stack = when (id) {
            UpgradeId.ORBITING_SWORD -> ++player.orbitingSwordStacks
            UpgradeId.ENERGY_PROJECTILE -> ++player.energyProjectileStacks
            UpgradeId.POISON_AURA -> ++player.poisonAuraStacks
            UpgradeId.CHAIN_LIGHTNING -> ++player.chainLightningStacks
            UpgradeId.PIERCING_ICE_CONE -> ++player.piercingIceConeStacks
            UpgradeId.LAVA_BOMB -> ++player.lavaBombStacks
            UpgradeId.GRAVITY_BLACK_HOLE -> ++player.gravityBlackHoleStacks
            UpgradeId.SWORD_RAIN -> ++player.swordRainStacks
            UpgradeId.LIGHTNING_DOMAIN -> ++player.lightningDomainStacks
            UpgradeId.ATTACK_DAMAGE -> {
                player.projectileDamage = UpgradeMath.increasedDamage(player.projectileDamage)
                ++player.attackDamageUpgradeStacks
            }
            UpgradeId.ATTACK_SPEED -> {
                player.attackIntervalSeconds = UpgradeMath.fasterAttackInterval(player.attackIntervalSeconds)
                ++player.attackSpeedUpgradeStacks
            }
            UpgradeId.ATTACK_RANGE -> {
                player.attackRangeMeters = UpgradeMath.increasedAttackRange(player.attackRangeMeters)
                ++player.attackRangeUpgradeStacks
            }
            UpgradeId.PICKUP_RANGE -> {
                player.expandPickupRange(UpgradeMath.PICKUP_RANGE_INCREASE_METERS)
                ++player.pickupRangeUpgradeStacks
            }
            UpgradeId.EXPERIENCE_GAIN -> {
                player.experienceGainMultiplier = UpgradeMath.increasedExperienceMultiplier(player.experienceGainMultiplier)
                ++player.experienceGainUpgradeStacks
            }
            UpgradeId.MAX_HEALTH -> {
                player.maxHealth += 25
                player.currentHealth = (player.currentHealth + 25).coerceAtMost(player.maxHealth)
                ++player.maxHealthUpgradeStacks
            }
            UpgradeId.HEALTH_REGENERATION -> {
                player.healthRegenerationPerSecond += 1
                ++player.healthRegenerationUpgradeStacks
            }
            UpgradeId.PROJECTILE_COUNT -> {
                player.projectileCount += 1
                ++player.projectileCountUpgradeStacks
            }
            UpgradeId.PIERCE_COUNT -> {
                player.pierceCount += 1
                ++player.pierceCountUpgradeStacks
            }
            UpgradeId.CRITICAL_CHANCE -> {
                player.criticalChance = (player.criticalChance + 0.1f).coerceAtMost(0.5f)
                ++player.criticalChanceUpgradeStacks
            }
            UpgradeId.DAMAGE_REDUCTION -> {
                player.damageReduction = (player.damageReduction + 0.1f).coerceAtMost(0.5f)
                ++player.damageReductionUpgradeStacks
            }
            UpgradeId.CRITICAL_DAMAGE -> {
                player.criticalDamageMultiplier += 0.5f
                ++player.criticalDamageUpgradeStacks
            }
            UpgradeId.DODGE_CHANCE -> {
                player.dodgeChance = (player.dodgeChance + 0.1f).coerceAtMost(0.3f)
                ++player.dodgeChanceUpgradeStacks
            }
            UpgradeId.REGENERATING_SHIELD -> ++player.regeneratingShieldStacks
            UpgradeId.EXPERIENCE_MAGNET -> { player.experienceMagnetOwned = true; 1 }
            UpgradeId.KILL_HEAL -> { player.killHealOwned = true; 1 }
            UpgradeId.MAGNETIC_FIELD -> { player.magneticFieldOwned = true; 1 }
            UpgradeId.EXPLOSIVE_REMAINS -> { player.explosiveRemainsOwned = true; 1 }
            UpgradeId.FREEZE_PULSE -> { player.freezePulseOwned = true; 1 }
            UpgradeId.REROLL_MASTER -> { player.rerollMasterOwned = true; 1 }
            UpgradeId.NEAR_DEATH_PROTECTION -> { player.nearDeathProtectionOwned = true; 1 }
            UpgradeId.EXTRA_OPTION -> { player.extraOptionOwned = true; 1 }
            UpgradeId.DOUBLE_CRYSTAL -> { player.doubleCrystalOwned = true; 1 }
            UpgradeId.GATHERING_AURA -> { player.gatheringAuraOwned = true; 1 }
            UpgradeId.MYRIAD_SWORDS, UpgradeId.NINE_HEAVENS_THUNDER,
            UpgradeId.NETHER_POISON_DOMAIN, UpgradeId.METEOR_LAVA,
            UpgradeId.ABSOLUTE_ZERO, UpgradeId.VOID_BLACK_HOLE -> {
                player.ownedEvolutions += id
                1
            }
            UpgradeId.MOVEMENT_SPEED -> {
                player.movementSpeedMultiplier = UpgradeMath.increasedMovementMultiplier(player.movementSpeedMultiplier)
                ++player.movementSpeedUpgradeStacks
            }
        }
        player.appliedUpgradeCount += 1
        return AppliedUpgrade(id, stack)
    }
}
