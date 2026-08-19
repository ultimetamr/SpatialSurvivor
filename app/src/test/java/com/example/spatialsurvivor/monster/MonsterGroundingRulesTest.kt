package com.example.spatialsurvivor.monster

import com.example.spatialsurvivor.game.SpatialManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterGroundingRulesTest {
    @Test
    fun bodyCenterY_usesAnchorOffsetAboveGround() {
        val groundY = 0.42f
        val type = MonsterType.NORMAL_BUG
        assertEquals(groundY + type.anchorOffsetY, MonsterGroundingRules.bodyCenterY(groundY, type), 0.0001f)
    }

    @Test
    fun lockHorizontalMove_correctsYDrift() {
        val monster = MonsterComponent().apply { monsterType = MonsterType.RUNNER }
        val groundY = 0f
        val drifted = NavigationPoint(1f, 0.25f, 2f)
        val locked =
            MonsterGroundingRules.lockHorizontalMove(
                current = drifted,
                nextX = 1.1f,
                nextZ = 2.1f,
                monster = monster,
                groundY = groundY,
            )
        assertEquals(1.1f, locked.x, 0.0001f)
        assertEquals(2.1f, locked.z, 0.0001f)
        assertEquals(MonsterType.RUNNER.anchorOffsetY, locked.y, 0.0001f)
    }

    @Test
    fun resolveGroundY_prefersNavigationFloor() {
        SpatialManager.clear()
        val resolved = MonsterGroundingRules.resolveGroundY(navigationFloorY = 0.35f, playerFootY = 0.1f)
        assertEquals(0.35f, resolved, 0.0001f)
    }

    @Test
    fun visualLocalOffset_alignsFeetWhenScaleOnChild() {
        val offset = MonsterVisualRules.visualLocalOffset(MonsterType.ARMORED)
        val scale = MonsterVisualRules.visualScale(MonsterType.ARMORED)
        val feetWorldOffset = offset.y * scale
        assertEquals(-MonsterType.ARMORED.anchorOffsetY, feetWorldOffset, 0.001f)
    }

    @Test
    fun deathLock_keepsGroundedY() {
        val monster = MonsterComponent().apply { monsterType = MonsterType.NORMAL_BUG }
        val locked =
            MonsterGroundingRules.lockDeathY(
                NavigationPoint(0f, 0.9f, 0f),
                monster,
                groundY = 0.1f,
            )
        assertEquals(0.1f + MonsterType.NORMAL_BUG.anchorOffsetY, locked.y, 0.0001f)
    }
}
