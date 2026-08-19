package com.example.spatialsurvivor.ui.settlement

import com.example.spatialsurvivor.game.GameOutcome
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.game.SettlementSnapshot
import com.example.spatialsurvivor.progression.SettlementCrystalReward
import com.example.spatialsurvivor.upgrade.UpgradeId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettlementViewModelTest {
    private lateinit var viewModel: SettlementViewModel

    @Before
    fun setUp() {
        GameSessionRuntime.reset()
        viewModel = SettlementViewModel()
    }

    @After
    fun tearDown() {
        GameSessionRuntime.reset()
    }

    @Test
    fun initialStateIsHiddenAndEmpty() {
        assertNull(viewModel.state.value.snapshot)
        assertEquals("00:00", viewModel.state.value.summary.survivalTimeText)
        assertEquals("第1波", viewModel.state.value.summary.waveText)
        assertTrue(viewModel.state.value.summary.upgradeHistory.isEmpty())
    }

    @Test
    fun synchronizeVictoryBuildsDurationAndGroupedUpgradeHistory() {
        viewModel.onEvent(
            SettlementEvent.Synchronize(
                snapshot(
                    outcome = GameOutcome.VICTORY,
                    upgrades = listOf(UpgradeId.ATTACK_DAMAGE, UpgradeId.ATTACK_DAMAGE),
                ),
            ),
        )
        assertEquals("20:05", viewModel.state.value.summary.survivalTimeText)
        assertEquals(2, viewModel.state.value.summary.upgradeHistory.single().count)
    }

    @Test
    fun synchronizeNullClearsPreviousResult() {
        viewModel.onEvent(SettlementEvent.Synchronize(snapshot(GameOutcome.DEFEAT)))
        viewModel.onEvent(SettlementEvent.Synchronize(null))
        assertNull(viewModel.state.value.snapshot)
        assertTrue(viewModel.state.value.summary.upgradeHistory.isEmpty())
    }

    @Test
    fun defeatWithNoUpgradesKeepsHistoryEmpty() {
        viewModel.onEvent(SettlementEvent.Synchronize(snapshot(GameOutcome.DEFEAT)))
        assertEquals(GameOutcome.DEFEAT, viewModel.state.value.snapshot?.outcome)
        assertTrue(viewModel.state.value.summary.upgradeHistory.isEmpty())
    }

    private fun snapshot(
        outcome: GameOutcome,
        upgrades: List<UpgradeId> = emptyList(),
    ) = SettlementSnapshot(
        outcome = outcome,
        survivalSeconds = 1205.9f,
        kills = 42,
        wave = 10,
        acquiredUpgrades = upgrades,
        waveText = if (outcome == GameOutcome.VICTORY) "Boss战" else "第10波",
        clearedWaves = if (outcome == GameOutcome.VICTORY) 10 else 9,
        crystalReward = SettlementCrystalReward(100, 8, 90, if (outcome == GameOutcome.VICTORY) 100 else 0, 298),
        totalCrystalsAfterReward = 512,
    )
}
