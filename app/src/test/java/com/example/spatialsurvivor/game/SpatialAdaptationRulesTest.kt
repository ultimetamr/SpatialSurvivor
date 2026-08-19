package com.example.spatialsurvivor.game

import com.example.spatialsurvivor.exp.ExperienceCrystalMotionRules
import com.example.spatialsurvivor.monster.MonsterUpdateBudgetRules
import com.example.spatialsurvivor.monster.NavigationBounds
import com.example.spatialsurvivor.monster.NavigationPoint
import com.example.spatialsurvivor.monster.SpatialNavigationMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpatialAdaptationRulesTest {
    @Test
    fun pointsOutsideSceneMeshBoundsAreNeverValidSpawnPoints() {
        val navigation = openNavigation()

        assertFalse(navigation.isValidSpawnPoint(NavigationPoint(-2.01f, 0f, 0f)))
        assertFalse(navigation.isValidSpawnPoint(NavigationPoint(2f, 0f, 0f)))
        assertTrue(navigation.isValidSpawnPoint(NavigationPoint(1.75f, 0f, 1.75f)))
    }

    @Test
    fun ceilingSpawnRejectsSemanticPointsOutsideMappedRoom() {
        val navigation =
            SpatialNavigationMap.build(
                revision = 4L,
                bounds = NavigationBounds(-2f, 2f, -2f, 2f),
                obstacles = emptyList(),
                floorHeight = 0f,
                ceilingSpawnPoints = listOf(NavigationPoint(4f, 2.6f, 0f)),
            )

        assertNull(navigation.findCeilingSpawn(0f, 0f, 1f, Random(8)))
    }

    @Test
    fun crystalPickupCollisionUsesSphereContactWithoutFurniturePhysics() {
        assertTrue(
            ExperienceCrystalMotionRules.spheresOverlap(
                distanceMeters = 0.4f,
                firstRadiusMeters = 0.1f,
                secondRadiusMeters = 0.3f,
            ),
        )
        assertFalse(
            ExperienceCrystalMotionRules.spheresOverlap(
                distanceMeters = 0.401f,
                firstRadiusMeters = 0.1f,
                secondRadiusMeters = 0.3f,
            ),
        )
    }

    @Test
    fun farMonsterAiUsesLowerUpdateCadence() {
        assertEquals(1, MonsterUpdateBudgetRules.cadenceTicks(6f))
        assertEquals(2, MonsterUpdateBudgetRules.cadenceTicks(6.01f))
        assertEquals(2, MonsterUpdateBudgetRules.cadenceTicks(10f))
        assertEquals(4, MonsterUpdateBudgetRules.cadenceTicks(10.01f))
    }

    @Test
    fun lodPhasesDistributeWorkAcrossTicks() {
        assertTrue(MonsterUpdateBudgetRules.shouldUpdate(8L, phase = 0, cadenceTicks = 4))
        assertFalse(MonsterUpdateBudgetRules.shouldUpdate(8L, phase = 1, cadenceTicks = 4))
        assertTrue(MonsterUpdateBudgetRules.shouldUpdate(11L, phase = 1, cadenceTicks = 4))
    }

    @Test
    fun staleHmdSamplePausesTrackingContinuity() {
        assertTrue(TrackingContinuityRules.isFreshHmdSample(true, 0.35f))
        assertFalse(TrackingContinuityRules.isFreshHmdSample(true, 0.351f))
        assertFalse(TrackingContinuityRules.isFreshHmdSample(false, 0f))

        val paused =
            TrackingContinuityRules.next(
                current = TrackingContinuityState(TrackingContinuityStatus.RUNNING),
                freshHmdSample = false,
                deltaSeconds = 1f / 90f,
            )
        assertEquals(TrackingContinuityStatus.PAUSED, paused.status)
    }

    @Test
    fun trackingResumesOnlyAfterStableFreshSamples() {
        val recovering =
            TrackingContinuityRules.next(
                current = TrackingContinuityState(),
                freshHmdSample = true,
                deltaSeconds = 0.1f,
            )
        assertEquals(TrackingContinuityStatus.RECOVERING, recovering.status)
        assertTrue(recovering.gameplayPaused)

        val running =
            TrackingContinuityRules.next(
                current = recovering,
                freshHmdSample = true,
                deltaSeconds = 0.05f,
            )
        assertEquals(TrackingContinuityStatus.RUNNING, running.status)
        assertFalse(running.gameplayPaused)
    }

    private fun openNavigation(): SpatialNavigationMap =
        SpatialNavigationMap.build(
            revision = 1L,
            bounds = NavigationBounds(-2f, 2f, -2f, 2f),
            obstacles = emptyList(),
            floorHeight = 0f,
            ceilingSpawnPoints = emptyList(),
        )
}
