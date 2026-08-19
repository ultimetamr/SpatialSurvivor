package com.example.spatialsurvivor.upgrade

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.spatialsurvivor.domain.model.UpgradeLoadout
import com.example.spatialsurvivor.player.PlayerComponent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class UpgradePresentationState(
    val visible: Boolean = false,
    val options: List<UpgradeOption> = emptyList(),
    val rerollAvailable: Boolean = false,
    val completedUpgradeCount: Int = 0,
)

/** Authoritative modal state shared by the fixed-step ECS loop and Compose panel. */
object UpgradeRuntime {
    private val mutableState = mutableStateOf(UpgradePresentationState())
    val state: State<UpgradePresentationState> = mutableState
    private val requestedSelection = AtomicInteger(NO_SELECTION)
    private val rerollRequested = AtomicBoolean(false)
    private var random: Random = Random.Default

    val isVisible: Boolean get() = mutableState.value.visible

    fun reset() {
        mutableState.value = UpgradePresentationState()
        requestedSelection.set(NO_SELECTION)
        rerollRequested.set(false)
        random = Random.Default
    }

    fun begin(player: PlayerComponent, drawRandom: Random = random) {
        if (isVisible) return
        random = drawRandom
        val profile = player.toUpgradeProfile()
        val options = UpgradeCatalog.draw(drawRandom, profile)
        check(options.isNotEmpty()) { "No eligible upgrade options remain" }
        mutableState.value = UpgradePresentationState(
            visible = true,
            options = options,
            rerollAvailable = player.rerollMasterOwned,
            completedUpgradeCount = player.appliedUpgradeCount,
        )
        Log.i(TAG, "Upgrade modal opened: paused=true, cards=${options.joinToString { "${it.rarity}:${it.id}" }}")
    }

    fun requestSelection(index: Int) {
        if (isVisible && index in mutableState.value.options.indices) requestedSelection.set(index)
    }

    fun requestReroll() {
        if (isVisible && mutableState.value.rerollAvailable) rerollRequested.set(true)
    }

    fun processRequests(player: PlayerComponent): AppliedUpgrade? {
        if (!isVisible) return null
        if (rerollRequested.compareAndSet(true, false) && mutableState.value.rerollAvailable) {
            val options = UpgradeCatalog.draw(random, player.toUpgradeProfile())
            mutableState.value = mutableState.value.copy(options = options, rerollAvailable = false)
            Log.i(TAG, "Upgrade cards rerolled: ${options.joinToString { it.id.name }}")
        }
        val index = requestedSelection.getAndSet(NO_SELECTION)
        if (index !in mutableState.value.options.indices) return null
        val option = mutableState.value.options[index]
        val applied = UpgradeEffects.apply(player, option.id)
        mutableState.value = UpgradePresentationState(completedUpgradeCount = player.appliedUpgradeCount)
        Log.i(TAG, "Upgrade selected: id=${option.id}, rarity=${option.rarity}, stack=${applied.resultingStack}, paused=false")
        return applied
    }

    fun currentProfile(player: PlayerComponent): UpgradeProfile = player.toUpgradeProfile()

    private fun PlayerComponent.toUpgradeProfile(): UpgradeProfile {
        val stacks = buildMap {
            put(UpgradeId.ORBITING_SWORD, orbitingSwordStacks)
            put(UpgradeId.ENERGY_PROJECTILE, energyProjectileStacks)
            put(UpgradeId.POISON_AURA, poisonAuraStacks)
            put(UpgradeId.CHAIN_LIGHTNING, chainLightningStacks)
            put(UpgradeId.PIERCING_ICE_CONE, piercingIceConeStacks)
            put(UpgradeId.LAVA_BOMB, lavaBombStacks)
            put(UpgradeId.GRAVITY_BLACK_HOLE, gravityBlackHoleStacks)
            put(UpgradeId.SWORD_RAIN, swordRainStacks)
            put(UpgradeId.LIGHTNING_DOMAIN, lightningDomainStacks)
            put(UpgradeId.ATTACK_DAMAGE, attackDamageUpgradeStacks)
            put(UpgradeId.ATTACK_SPEED, attackSpeedUpgradeStacks)
            put(UpgradeId.ATTACK_RANGE, attackRangeUpgradeStacks)
            put(UpgradeId.PICKUP_RANGE, pickupRangeUpgradeStacks)
            put(UpgradeId.EXPERIENCE_GAIN, experienceGainUpgradeStacks)
            put(UpgradeId.MAX_HEALTH, maxHealthUpgradeStacks)
            put(UpgradeId.HEALTH_REGENERATION, healthRegenerationUpgradeStacks)
            put(UpgradeId.PROJECTILE_COUNT, projectileCountUpgradeStacks)
            put(UpgradeId.PIERCE_COUNT, pierceCountUpgradeStacks)
            put(UpgradeId.CRITICAL_CHANCE, criticalChanceUpgradeStacks)
            put(UpgradeId.DAMAGE_REDUCTION, damageReductionUpgradeStacks)
            put(UpgradeId.CRITICAL_DAMAGE, criticalDamageUpgradeStacks)
            put(UpgradeId.DODGE_CHANCE, dodgeChanceUpgradeStacks)
            put(UpgradeId.REGENERATING_SHIELD, regeneratingShieldStacks)
        }
        val owned = buildSet {
            if (experienceMagnetOwned) add(UpgradeId.EXPERIENCE_MAGNET)
            if (killHealOwned) add(UpgradeId.KILL_HEAL)
            if (magneticFieldOwned) add(UpgradeId.MAGNETIC_FIELD)
            if (explosiveRemainsOwned) add(UpgradeId.EXPLOSIVE_REMAINS)
            if (freezePulseOwned) add(UpgradeId.FREEZE_PULSE)
            if (rerollMasterOwned) add(UpgradeId.REROLL_MASTER)
            if (nearDeathProtectionOwned) add(UpgradeId.NEAR_DEATH_PROTECTION)
            if (extraOptionOwned) add(UpgradeId.EXTRA_OPTION)
            if (doubleCrystalOwned) add(UpgradeId.DOUBLE_CRYSTAL)
            if (gatheringAuraOwned) add(UpgradeId.GATHERING_AURA)
            addAll(ownedEvolutions)
        }
        return UpgradeLoadout(stacks, owned, appliedUpgradeCount)
    }

    private const val NO_SELECTION = -1
    private const val TAG = "UpgradeRuntime"
}
