package com.example.spatialsurvivor.monster

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossCombatRulesTest {
    @Test
    fun playerInsideRadiusIsHit() {
        assertTrue(BossCombatRules.isInsideAreaAttack(2.5f, 0f, 0f, 0f))
    }

    @Test
    fun playerOutsideRadiusIsSafe() {
        assertFalse(BossCombatRules.isInsideAreaAttack(2.51f, 0f, 0f, 0f))
    }
}
