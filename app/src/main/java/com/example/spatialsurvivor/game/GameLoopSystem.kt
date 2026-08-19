package com.example.spatialsurvivor.game

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.spatialsurvivor.exp.ExperienceGameplay
import com.example.spatialsurvivor.exp.ExperienceProgressionRules
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.player.PlayerGameplay
import com.example.spatialsurvivor.player.PlayerStats
import com.example.spatialsurvivor.monster.MonsterGameplay
import com.example.spatialsurvivor.monster.BossGameplay
import com.example.spatialsurvivor.monster.CombatFeedbackRuntime
import com.example.spatialsurvivor.upgrade.UpgradeGameplay
import com.example.spatialsurvivor.upgrade.UpgradeRuntime
import com.example.spatialsurvivor.upgrade.UpgradeWeaponGameplay
import com.example.spatialsurvivor.upgrade.LevelUpEffectGameplay
import com.example.spatialsurvivor.upgrade.UpgradeId
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System

/** ECS system registered in the active Stage scene. */
class GameLoopSystem : System() {
    private val clock = FixedStepClock()

    override fun update(context: SceneUpdateContext) {
        clock.advance(context.deltaTime) { fixedDeltaSeconds ->
            GameRuntime.fixedUpdate(
                scene = context.scene,
                deltaSeconds = fixedDeltaSeconds,
                tracking = SpatialTrackingRuntime.snapshot(),
            )
        }
    }
}

object GameTimeRules {
    fun shouldAdvanceGameplay(
        settlementActive: Boolean = false,
        upgradeActive: Boolean = false,
        mainMenuActive: Boolean = false,
        permanentPanelActive: Boolean = false,
        pausePanelActive: Boolean = false,
    ): Boolean =
        !settlementActive &&
            !upgradeActive &&
            !mainMenuActive &&
            !permanentPanelActive &&
            !pausePanelActive
}

enum class GameLoopRoute {
    MAIN_MENU,
    PERMANENT_PANEL,
    PAUSE_PANEL,
    SETTLEMENT,
    TRACKING_PAUSED,
    UPGRADE_PAUSED,
    GAMEPLAY,
}

/** Terminal results and upgrade choices pause combat; their UI systems run before this route. */
object GameLoopRoutingRules {
    fun select(
        settlementActive: Boolean,
        trackingPaused: Boolean,
        upgradeActive: Boolean = false,
        mainMenuActive: Boolean = false,
        permanentPanelActive: Boolean = false,
        pausePanelActive: Boolean = false,
    ): GameLoopRoute =
        when {
            mainMenuActive -> GameLoopRoute.MAIN_MENU
            permanentPanelActive -> GameLoopRoute.PERMANENT_PANEL
            pausePanelActive -> GameLoopRoute.PAUSE_PANEL
            settlementActive -> GameLoopRoute.SETTLEMENT
            trackingPaused -> GameLoopRoute.TRACKING_PAUSED
            upgradeActive -> GameLoopRoute.UPGRADE_PAUSED
            else -> GameLoopRoute.GAMEPLAY
        }
}

object GameRuntime {
    var elapsedGameSeconds: Float = 0f
        private set

    var simulationTick: Long = 0L
        private set

    var latestTracking: SpatialTrackingSnapshot = SpatialTrackingSnapshot()
        private set

    private val mutableHudState = mutableStateOf(PlayerHudState())
    val hudState: State<PlayerHudState> = mutableHudState

    fun reset() {
        elapsedGameSeconds = 0f
        simulationTick = 0L
        latestTracking = SpatialTrackingSnapshot()
        mutableHudState.value = PlayerHudState()
        levelUpFeedbackRemainingSeconds = 0f
        UpgradeGameplay.reset()
        GameSessionRuntime.reset()
        PlayerGameplay.resetDiagnostics()
        MonsterGameplay.reset()
        ExperienceGameplay.reset()
        UpgradeWeaponGameplay.reset()
        SpatialHudGameplay.resetDiagnostics()
        CombatFeedbackRuntime.reset()
        TrackingPauseRuntime.reset()
    }

    fun fixedUpdate(
        scene: Scene,
        deltaSeconds: Float,
        tracking: SpatialTrackingSnapshot,
    ) {
        latestTracking = tracking
        val trackingPaused = TrackingPauseRuntime.update(tracking, deltaSeconds)
        publishTrackingState(active = !trackingPaused)
        AppUiGameplay.fixedUpdate(scene, deltaSeconds, tracking)
        SettlementGameplay.fixedUpdate(scene, deltaSeconds, tracking)
        SpatialHudGameplay.fixedUpdate(scene, deltaSeconds, tracking)
        GameSessionRuntime.advanceSettlement(deltaSeconds)
        advanceLevelUpFeedback(deltaSeconds)
        LevelUpEffectGameplay.fixedUpdate(scene, deltaSeconds)
        VictoryLightEffectGameplay.fixedUpdate(scene, deltaSeconds)
        UpgradeGameplay.fixedUpdate(scene, deltaSeconds, tracking)
        if (GameSessionRuntime.consumeVictoryEffectRequest()) {
            VictoryLightEffectGameplay.trigger(scene)
        }
        if (GameSessionRuntime.consumeBattlefieldClearRequest()) {
            PlayerGameplay.clearProjectiles(scene)
            MonsterGameplay.clearAllNonBossMonsters(scene)
            ExperienceGameplay.clearCrystals(scene)
        }
        if (AppUiRuntime.consumeReturnToMainMenuRequest()) {
            returnToMainMenu(scene)
            return
        }
        if (AppUiRuntime.consumeStartRunRequest()) {
            AppUiRuntime.showGameplay()
            restart(scene)
            return
        }
        when (
            GameLoopRoutingRules.select(
                settlementActive = GameSessionRuntime.settlementBlockingActive,
                trackingPaused = trackingPaused,
                upgradeActive = UpgradeRuntime.isVisible,
                mainMenuActive = AppUiRuntime.mainMenuVisible,
                permanentPanelActive = AppUiRuntime.permanentPanelVisible,
                pausePanelActive = AppUiRuntime.pausePanelVisible,
            )
        ) {
            GameLoopRoute.MAIN_MENU -> return
            GameLoopRoute.PERMANENT_PANEL -> return
            GameLoopRoute.PAUSE_PANEL -> return
            GameLoopRoute.SETTLEMENT -> return
            GameLoopRoute.TRACKING_PAUSED -> return
            GameLoopRoute.UPGRADE_PAUSED -> return
            GameLoopRoute.GAMEPLAY -> Unit
        }
        if (CombatFeedbackRuntime.consumeHitStop(deltaSeconds)) {
            CombatFeedbackRuntime.updateVisuals(scene, deltaSeconds)
            return
        }
        elapsedGameSeconds += deltaSeconds
        simulationTick += 1
        GameSessionRuntime.advance(elapsedGameSeconds)
        BossGameplay.fixedUpdate(scene, deltaSeconds, elapsedGameSeconds)
        if (GameSessionRuntime.settlementBlockingActive) {
            return
        }
        PlayerGameplay.fixedUpdate(scene, deltaSeconds, tracking)
        if (GameSessionRuntime.settlementBlockingActive) {
            return
        }
        UpgradeWeaponGameplay.fixedUpdate(scene, deltaSeconds)
        if (GameSessionRuntime.settlementBlockingActive) {
            return
        }
        MonsterGameplay.fixedUpdate(scene, deltaSeconds)
        ExperienceGameplay.fixedUpdate(scene, deltaSeconds)
        CombatFeedbackRuntime.updateVisuals(scene, deltaSeconds)
    }

    fun restart(scene: Scene) {
        elapsedGameSeconds = 0f
        simulationTick = 0L
        levelUpFeedbackRemainingSeconds = 0f
        UpgradeGameplay.reset()
        GameSessionRuntime.reset()
        PlayerGameplay.resetScene(scene)
        MonsterGameplay.resetScene(scene)
        BossGameplay.resetScene(scene)
        ExperienceGameplay.resetScene(scene)
        UpgradeWeaponGameplay.resetScene(scene)
        AppUiGameplay.resetScene(scene)
        SettlementGameplay.resetScene(scene)
        UpgradeGameplay.resetScene(scene)
        VictoryLightEffectGameplay.resetScene(scene)
        SpatialHudGameplay.resetScene(scene)
        CombatFeedbackRuntime.reset()
        TrackingPauseRuntime.reset()
        scene.queryEntity(EntityQueryCondition.hasComponent(PlayerComponent::class.java))
            .firstOrNull()
            ?.components
            ?.get(PlayerComponent::class.java)
            ?.let(::publishPlayerState)
    }

    fun returnToMainMenu(scene: Scene) {
        restart(scene)
        AppUiRuntime.showMainMenu()
    }

    fun publishPlayerState(player: PlayerComponent) {
        val current = mutableHudState.value
        val next =
            PlayerHudState(
                health = player.currentHealth,
                maxHealth = player.maxHealth,
                attackRangeMeters = player.attackRangeMeters,
                attackDamage = player.projectileDamage,
                attackIntervalSeconds = player.attackIntervalSeconds,
                projectileCount = player.projectileCount,
                healthRegenerationPerSecond = player.healthRegenerationPerSecond,
                pickupRangeMeters = player.pickupRangeMeters,
                experienceGainMultiplier = player.experienceGainMultiplier,
                level = player.level,
                experience = player.currentExperience,
                experienceRequired = player.experienceRequired,
                gameOver = player.isGameOver,
                trackingActive = player.hasTrackingPose,
                remainingSeconds = SpatialHudRules.remainingSeconds(elapsedGameSeconds),
                activeSkills =
                    SpatialHudRules.activeSkills(
                        orbitingSwordStacks = player.orbitingSwordStacks,
                        energyProjectileStacks = player.energyProjectileStacks,
                        chainLightningStacks = player.chainLightningStacks,
                        poisonAuraStacks = player.poisonAuraStacks,
                        piercingIceConeStacks = player.piercingIceConeStacks,
                        lavaBombStacks = player.lavaBombStacks,
                        gravityBlackHoleStacks = player.gravityBlackHoleStacks,
                        swordRainStacks = player.swordRainStacks,
                        lightningDomainStacks = player.lightningDomainStacks,
                    ),
                damageEventSequence = player.damageEventSequence,
                hudDimmed = current.hudDimmed,
                levelUpFeedback = current.levelUpFeedback,
            )
        if (mutableHudState.value != next) {
            mutableHudState.value = next
        }
    }

    fun publishHudPresentation(dimmedForOverlay: Boolean) {
        val current = mutableHudState.value
        val next = current.copy(hudDimmed = dimmedForOverlay)
        if (current != next) mutableHudState.value = next
    }

    fun showLevelUpFeedback(message: String) {
        levelUpFeedbackRemainingSeconds = LEVEL_UP_FEEDBACK_SECONDS
        mutableHudState.value = mutableHudState.value.copy(levelUpFeedback = message)
    }

    private fun advanceLevelUpFeedback(deltaSeconds: Float) {
        if (levelUpFeedbackRemainingSeconds <= 0f) return
        levelUpFeedbackRemainingSeconds -= deltaSeconds
        if (levelUpFeedbackRemainingSeconds <= 0f) {
            mutableHudState.value = mutableHudState.value.copy(levelUpFeedback = null)
        }
    }

    private fun publishTrackingState(active: Boolean) {
        val current = mutableHudState.value
        if (current.trackingActive != active) {
            mutableHudState.value = current.copy(trackingActive = active)
        }
    }

    private var levelUpFeedbackRemainingSeconds = 0f
    private const val LEVEL_UP_FEEDBACK_SECONDS = 1.5f
}

data class PlayerHudState(
    val health: Int = PlayerStats.DEFAULT_MAX_HEALTH,
    val maxHealth: Int = PlayerStats.DEFAULT_MAX_HEALTH,
    val attackRangeMeters: Float = PlayerStats.DEFAULT_ATTACK_RANGE_METERS,
    val attackDamage: Int = PlayerStats.DEFAULT_PROJECTILE_DAMAGE,
    val attackIntervalSeconds: Float = PlayerStats.DEFAULT_ATTACK_INTERVAL_SECONDS,
    val projectileCount: Int = 1,
    val healthRegenerationPerSecond: Int = 0,
    val pickupRangeMeters: Float = PlayerStats.DEFAULT_PICKUP_RANGE_METERS,
    val experienceGainMultiplier: Float = 1f,
    val level: Int = ExperienceProgressionRules.DEFAULT_LEVEL,
    val experience: Int = 0,
    val experienceRequired: Int = ExperienceProgressionRules.DEFAULT_EXPERIENCE_REQUIRED,
    val gameOver: Boolean = false,
    val trackingActive: Boolean = false,
    val remainingSeconds: Int = WaveRules.FINAL_BOSS_TIME_SECONDS.toInt(),
    val activeSkills: List<ActiveSkillHud> = emptyList(),
    val damageEventSequence: Long = 0L,
    val hudDimmed: Boolean = false,
    val levelUpFeedback: String? = null,
)
