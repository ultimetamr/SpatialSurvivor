package com.example.spatialsurvivor.game

import com.example.spatialsurvivor.upgrade.UpgradeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialHudRulesTest {
    @Test
    fun `remaining time counts down to final boss and clamps at zero`() {
        assertEquals(600, SpatialHudRules.remainingSeconds(0f))
        assertEquals(1, SpatialHudRules.remainingSeconds(599.2f))
        assertEquals(0, SpatialHudRules.remainingSeconds(601f))
    }

    @Test
    fun `experience and health progress clamp to display range`() {
        assertEquals(0.5f, SpatialHudRules.experienceProgress(5, 10), 0.0001f)
        assertEquals(1f, SpatialHudRules.experienceProgress(20, 10), 0.0001f)
        assertEquals(0f, SpatialHudRules.healthProgress(-10, 100), 0.0001f)
    }

    @Test
    fun `view hud resolves one point two meters ahead and fifteen degrees below eye level`() {
        val placement = SpatialHudRules.viewHudPlacement(2f, 1.7f, 3f, 0f, -1f)

        assertEquals(2f, placement.centerX, 0.0001f)
        assertEquals(1.7f - 1.2f * kotlin.math.sin(Math.toRadians(15.0)).toFloat(), placement.centerY, 0.0001f)
        assertEquals(3f - 1.2f * kotlin.math.cos(Math.toRadians(15.0)).toFloat(), placement.centerZ, 0.0001f)
        assertEquals(0f, placement.yawDegrees, 0.0001f)
    }

    @Test
    fun `view hud billboard faces the player after a right turn`() {
        val placement = SpatialHudRules.viewHudPlacement(0f, 1.6f, 0f, 1f, 0f)

        assertTrue(placement.centerX > 0f)
        assertEquals(-90f, placement.yawDegrees, 0.0001f)
    }

    @Test
    fun `view hud dims only while a modal overlay is active`() {
        assertEquals(1f, SpatialHudRules.hudTargetAlpha(dimmedForOverlay = false), 0.0001f)
        assertEquals(0.5f, SpatialHudRules.hudTargetAlpha(dimmedForOverlay = true), 0.0001f)
        assertTrue(SpatialHudRules.shouldDimForOverlay(settlementVisible = false, upgradeVisible = true))
        assertTrue(SpatialHudRules.shouldDimForOverlay(settlementVisible = true))
        assertFalse(SpatialHudRules.shouldDimForOverlay(settlementVisible = false))
    }

    @Test
    fun `large view change triggers bounded recentering`() {
        assertTrue(
            SpatialHudRules.shouldRecenterViewHud(
                headX = 0f,
                headZ = 0f,
                currentX = 0f,
                currentZ = -1f,
                desiredForwardX = 1f,
                desiredForwardZ = 0f,
            ),
        )
        assertFalse(
            SpatialHudRules.shouldRecenterViewHud(
                headX = 0f,
                headZ = 0f,
                currentX = 0f,
                currentZ = -1f,
                desiredForwardX = 0f,
                desiredForwardZ = -1f,
            ),
        )
    }

    @Test
    fun `active skills include only equipped weapon skills with stacks`() {
        val skills = SpatialHudRules.activeSkills(orbitingSwordStacks = 2, chainLightningStacks = 0, poisonAuraStacks = 1)

        assertEquals(listOf(UpgradeId.ORBITING_SWORD, UpgradeId.POISON_AURA), skills.map { it.id })
        assertEquals(listOf(2, 1), skills.map { it.stacks })
    }
}
