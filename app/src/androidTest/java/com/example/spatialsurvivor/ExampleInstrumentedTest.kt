package com.example.spatialsurvivor

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spatialsurvivor.platform.LaunchActivity
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.player.PlayerDamageRuntime
import com.example.spatialsurvivor.upgrade.UpgradeRuntime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun launchActivityIsAlive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent =
            Intent(instrumentation.targetContext, LaunchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        val activity = instrumentation.startActivitySync(intent)
        assertNotNull(activity)
        activity.finish()
    }

    @Test
    fun upgradeModalPausesUntilOneCardIsConfirmed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: PlayerComponent
        instrumentation.runOnMainSync {
            GameSessionRuntime.reset()
            UpgradeRuntime.reset()
            player = PlayerComponent()
            UpgradeRuntime.begin(player, Random(7))
        }
        assertTrue(UpgradeRuntime.isVisible)
        assertEquals(3, UpgradeRuntime.state.value.options.size)
        instrumentation.runOnMainSync {
            UpgradeRuntime.requestSelection(0)
            assertNotNull(UpgradeRuntime.processRequests(player))
        }
        assertTrue(!UpgradeRuntime.isVisible)
        assertEquals(1, player.appliedUpgradeCount)
    }

    @Test
    fun newPlayerKeepsStarterEnergyProjectileEnabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: PlayerComponent
        instrumentation.runOnMainSync { player = PlayerComponent() }

        assertEquals(PlayerComponent.DEFAULT_ENERGY_PROJECTILE_STACKS, player.energyProjectileStacks)
        assertTrue(player.energyProjectileStacks > 0)
    }

    @Test
    fun zeroHealthCreatesExplicitDefeatSettlement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: PlayerComponent
        try {
            instrumentation.runOnMainSync {
                GameSessionRuntime.reset()
                player = PlayerComponent()
                PlayerDamageRuntime.applyDamage(player, player.maxHealth, "instrumented monster")
            }

            assertEquals(0, player.currentHealth)
            assertTrue(player.isGameOver)
            assertTrue(GameSessionRuntime.settlementVisible)
            assertNotNull(GameSessionRuntime.state.value.settlement)
        } finally {
            instrumentation.runOnMainSync {
                GameSessionRuntime.reset()
            }
        }
    }

}
