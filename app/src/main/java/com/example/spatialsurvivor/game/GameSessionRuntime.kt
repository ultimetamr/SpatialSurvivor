package com.example.spatialsurvivor.game

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.spatialsurvivor.monster.MonsterType
import com.example.spatialsurvivor.progression.PermanentProgressionRuntime
import com.example.spatialsurvivor.progression.SettlementCrystalReward
import com.example.spatialsurvivor.upgrade.UpgradeId
import java.util.concurrent.atomic.AtomicBoolean

enum class GameOutcome {
    VICTORY,
    DEFEAT,
}

enum class SettlementPhase {
    HIDDEN,
    DEFEAT_DELAY,
    VICTORY_DELAY,
    PANEL,
}

data class SettlementSnapshot(
    val outcome: GameOutcome,
    val survivalSeconds: Float,
    val kills: Int,
    val wave: Int,
    val acquiredUpgrades: List<UpgradeId>,
    val waveText: String,
    val clearedWaves: Int,
    val crystalReward: SettlementCrystalReward,
    val totalCrystalsAfterReward: Int,
)

data class GameSessionPresentationState(
    val currentWave: Int = 1,
    val kills: Int = 0,
    val bossActive: Boolean = false,
    val settlement: SettlementSnapshot? = null,
    val settlementPhase: SettlementPhase = SettlementPhase.HIDDEN,
)

/** Authoritative run state shared by fixed-step gameplay and Compose result presentation. */
object GameSessionRuntime {
    private val mutableState = mutableStateOf(GameSessionPresentationState())
    val state: State<GameSessionPresentationState> = mutableState
    private val acquiredUpgrades = mutableListOf<UpgradeId>()
    private val restartRequested = AtomicBoolean(false)
    private val victoryEffectRequested = AtomicBoolean(false)
    private val battlefieldClearRequested = AtomicBoolean(false)
    private var settlementRevealRemainingSeconds = 0f

    val currentWave: Int
        get() = mutableState.value.currentWave

    val bossActive: Boolean
        get() = mutableState.value.bossActive

    val settlementVisible: Boolean
        get() = mutableState.value.settlementPhase == SettlementPhase.PANEL

    val settlementBlockingActive: Boolean
        get() = mutableState.value.settlement != null

    fun reset() {
        mutableState.value = GameSessionPresentationState()
        acquiredUpgrades.clear()
        restartRequested.set(false)
        victoryEffectRequested.set(false)
        battlefieldClearRequested.set(false)
        settlementRevealRemainingSeconds = 0f
    }

    fun advance(elapsedSeconds: Float) {
        if (settlementBlockingActive) return
        val nextWave = WaveRules.waveAt(elapsedSeconds)
        if (nextWave != mutableState.value.currentWave) {
            mutableState.value = mutableState.value.copy(currentWave = nextWave)
            Log.i(TAG, "Wave $nextWave started: hp x${WaveRules.healthMultiplier(nextWave)}, " +
                "quantity x${WaveRules.quantityMultiplier(nextWave)}, " +
                "speed x${WaveRules.speedMultiplier(nextWave)}")
        }
    }

    fun advanceSettlement(deltaSeconds: Float) {
        val current = mutableState.value
        if (current.settlement == null || current.settlementPhase == SettlementPhase.PANEL) return
        settlementRevealRemainingSeconds =
            (settlementRevealRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
        if (settlementRevealRemainingSeconds <= 0f) {
            mutableState.value = current.copy(settlementPhase = SettlementPhase.PANEL)
        }
    }

    fun recordUpgrade(id: UpgradeId) {
        if (!settlementBlockingActive) acquiredUpgrades += id
    }

    fun markBossActive() {
        if (!settlementBlockingActive) {
            mutableState.value = mutableState.value.copy(bossActive = true)
        }
    }

    fun recordMonsterDefeated(type: MonsterType, elapsedSeconds: Float) {
        if (settlementBlockingActive) return
        val nextKills = mutableState.value.kills + 1
        mutableState.value = mutableState.value.copy(kills = nextKills)
        if (type == MonsterType.FINAL_BOSS) {
            finish(GameOutcome.VICTORY, elapsedSeconds)
        }
    }

    fun finishDefeat(elapsedSeconds: Float) {
        finish(GameOutcome.DEFEAT, elapsedSeconds)
    }

    private fun finish(outcome: GameOutcome, elapsedSeconds: Float) {
        if (settlementBlockingActive) return
        val current = mutableState.value
        val clearedWaves = clearedWaves(outcome, current.currentWave)
        val reward =
            PermanentProgressionRuntime.awardSettlementCrystals(
                survivalSeconds = elapsedSeconds,
                kills = current.kills,
                clearedWaves = clearedWaves,
                victory = outcome == GameOutcome.VICTORY,
            )
        mutableState.value =
            current.copy(
                settlement =
                    SettlementSnapshot(
                        outcome = outcome,
                        survivalSeconds = elapsedSeconds,
                        kills = current.kills,
                        wave = current.currentWave,
                        acquiredUpgrades = acquiredUpgrades.toList(),
                        waveText = if (outcome == GameOutcome.VICTORY) "Boss战" else "第${current.currentWave}波",
                        clearedWaves = clearedWaves,
                        crystalReward = reward,
                        totalCrystalsAfterReward = PermanentProgressionRuntime.state.value.totalCrystals,
                    ),
                settlementPhase =
                    if (outcome == GameOutcome.VICTORY) {
                        SettlementPhase.VICTORY_DELAY
                    } else {
                        SettlementPhase.DEFEAT_DELAY
                    },
            )
        settlementRevealRemainingSeconds =
            if (outcome == GameOutcome.VICTORY) {
                victoryEffectRequested.set(true)
                battlefieldClearRequested.set(true)
                VICTORY_REVEAL_DELAY_SECONDS
            } else {
                DEFEAT_REVEAL_DELAY_SECONDS
            }
        Log.i(TAG, "Settlement opened: outcome=$outcome, time=$elapsedSeconds, " +
            "kills=${current.kills}, wave=${current.currentWave}")
    }

    fun requestRestart() {
        if (settlementVisible) restartRequested.set(true)
    }

    fun consumeRestartRequest(): Boolean = restartRequested.compareAndSet(true, false)

    fun consumeVictoryEffectRequest(): Boolean = victoryEffectRequested.compareAndSet(true, false)

    fun consumeBattlefieldClearRequest(): Boolean = battlefieldClearRequested.compareAndSet(true, false)

    fun clearSettlement() {
        val current = mutableState.value
        mutableState.value =
            current.copy(
                settlement = null,
                settlementPhase = SettlementPhase.HIDDEN,
                bossActive = false,
            )
        settlementRevealRemainingSeconds = 0f
        restartRequested.set(false)
        victoryEffectRequested.set(false)
        battlefieldClearRequested.set(false)
    }

    private fun clearedWaves(outcome: GameOutcome, currentWave: Int): Int =
        if (outcome == GameOutcome.VICTORY) {
            currentWave.coerceAtLeast(1)
        } else {
            (currentWave - 1).coerceAtLeast(0)
        }

    private const val TAG = "GameSessionRuntime"
    private const val DEFEAT_REVEAL_DELAY_SECONDS = 0.5f
    private const val VICTORY_REVEAL_DELAY_SECONDS = 1f
}
