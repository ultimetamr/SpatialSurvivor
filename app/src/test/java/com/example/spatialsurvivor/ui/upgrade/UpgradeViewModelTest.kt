package com.example.spatialsurvivor.ui.upgrade

import com.example.spatialsurvivor.upgrade.UpgradeOption
import com.example.spatialsurvivor.upgrade.UpgradeId
import com.example.spatialsurvivor.upgrade.UpgradeRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeViewModelTest {
    @After fun tearDown() = UpgradeRuntime.reset()

    @Test fun initialStateIsHiddenAndEmpty() {
        val state = UpgradeViewModel().state.value
        assertFalse(state.visible)
        assertTrue(state.options.isEmpty())
    }

    @Test fun synchronizePublishesVisibleOptions() {
        val vm = UpgradeViewModel()
        val option = UpgradeOption(UpgradeId.ATTACK_DAMAGE)
        vm.onEvent(UpgradeEvent.Synchronize(UpgradeUiState(true, listOf(option), false)))
        assertTrue(vm.state.value.visible)
        assertEquals(listOf(option), vm.state.value.options)
    }

    @Test fun selectEventDoesNotOptimisticallyHideTheModal() {
        val vm = UpgradeViewModel()
        vm.onEvent(UpgradeEvent.Synchronize(UpgradeUiState(true, listOf(UpgradeOption(UpgradeId.ATTACK_DAMAGE)), false)))
        vm.onEvent(UpgradeEvent.Select(0))
        assertTrue(vm.state.value.visible)
    }

    @Test fun invalidSelectionDoesNotMutateSynchronizedCards() {
        val vm = UpgradeViewModel()
        val options = listOf(UpgradeOption(UpgradeId.ATTACK_RANGE))
        vm.onEvent(UpgradeEvent.Synchronize(UpgradeUiState(true, options, false)))
        vm.onEvent(UpgradeEvent.Select(99))
        assertEquals(options, vm.state.value.options)
    }

    @Test fun rerollWaitsForAuthoritativeRuntimeSynchronization() {
        val vm = UpgradeViewModel()
        vm.onEvent(UpgradeEvent.Synchronize(UpgradeUiState(true, emptyList(), true)))
        vm.onEvent(UpgradeEvent.Reroll)
        assertTrue(vm.state.value.rerollAvailable)
    }
}
