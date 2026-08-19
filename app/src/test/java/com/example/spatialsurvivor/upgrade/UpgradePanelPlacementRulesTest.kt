package com.example.spatialsurvivor.upgrade

import com.example.spatialsurvivor.game.WorldLockedPanelPlacementRules
import com.example.spatialsurvivor.game.WorldLockedPanelPose
import com.example.spatialsurvivor.monster.NavigationBounds
import com.example.spatialsurvivor.monster.ObstacleFootprint
import com.example.spatialsurvivor.monster.SpatialNavigationMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class UpgradePanelPlacementRulesTest {
    @Test
    fun placesAllPanelsOneMeterAheadAtEyeHeightFacingPlayer() {
        val placement =
            UpgradePanelPlacementRules.placeInFrontOfPlayer(
                headX = 0f,
                headY = 1.6f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
            )

        assertEquals(0f, placement.centerX, 0.0001f)
        assertEquals(1.6f, placement.centerY, 0.0001f)
        assertEquals(-0.85f, placement.centerZ, 0.0001f)
        assertEquals(0f, placement.yawDegrees, 0.0001f)
        assertFalse(placement.retreatedForClearance)
        assertEquals(0.85f, UpgradePanelPlacementRules.DISTANCE_METERS, 0.0001f)
        assertEquals(1.0f, WorldLockedPanelPlacementRules.PANEL_DISTANCE_METERS, 0.0001f)
    }

    @Test
    fun rightFacingPlacementUsesNegativeNinetyYawAndStaysCentered() {
        val placement =
            UpgradePanelPlacementRules.placeInFrontOfPlayer(
                headX = 2f,
                headY = 1.7f,
                headZ = 3f,
                rawForwardX = 1f,
                rawForwardZ = 0f,
            )

        assertEquals(2.85f, placement.centerX, 0.0001f)
        assertEquals(1.7f, placement.centerY, 0.0001f)
        assertEquals(3f, placement.centerZ, 0.0001f)
        assertEquals(-90f, placement.yawDegrees, 0.0001f)
    }

    @Test
    fun retreatsTowardPlayerNotRoomCenterWhenIdealPointHitsObstacle() {
        // Player stands near the +Z edge looking into the room (-Z). Ideal 1m ahead hits furniture.
        // Old room-center retreat would push the panel deeper to z≈0 (≈5m away). Pull-to-head stays near.
        val navigation =
            SpatialNavigationMap.build(
                revision = 1L,
                bounds = NavigationBounds(-3f, 3f, -3f, 6f),
                obstacles =
                    listOf(
                        ObstacleFootprint(minX = -0.6f, maxX = 0.6f, minZ = 3.6f, maxZ = 4.4f),
                    ),
                floorHeight = 0f,
                ceilingSpawnPoints = emptyList(),
            )

        val placement =
            UpgradePanelPlacementRules.placeInFrontOfPlayer(
                headX = 0f,
                headY = 1.6f,
                headZ = 5f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                navigation = navigation,
            )

        assertTrue(placement.retreatedForClearance)
        assertEquals(1.6f, placement.centerY, 0.0001f)
        val distance =
            hypot(
                (placement.centerX - 0f).toDouble(),
                (placement.centerZ - 5f).toDouble(),
            ).toFloat()
        assertTrue(distance <= 1.0f + 0.001f)
        assertTrue(placement.centerZ > 4.0f)
    }

    @Test
    fun recenterTriggersWhenPanelIsBehindPlayerOrDistanceDrifts() {
        assertTrue(
            WorldLockedPanelPlacementRules.shouldRecenter(
                headX = 0f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                panelX = 0f,
                panelZ = 1.0f,
                intendedDistanceMeters = 1.0f,
            ),
        )
        assertTrue(
            WorldLockedPanelPlacementRules.shouldRecenter(
                headX = 0f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                panelX = 0f,
                panelZ = -2.0f,
                intendedDistanceMeters = 1.0f,
            ),
        )
        assertFalse(
            WorldLockedPanelPlacementRules.shouldRecenter(
                headX = 0f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                panelX = 0f,
                panelZ = -1.0f,
                intendedDistanceMeters = 1.0f,
            ),
        )
    }

    @Test
    fun idleLockedPoseDoesNotRequestTransformWrites() {
        val pose = WorldLockedPanelPose(1.0f)
        pose.lockTo(
            WorldLockedPanelPlacementRules.placeInFrontOfPlayer(
                headX = 0f,
                headY = 1.6f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                distanceMeters = 1.0f,
            ),
        )
        val whileCooling =
            pose.tick(
                deltaSeconds = 0.016f,
                headX = 0f,
                headY = 1.6f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                navigation = null,
            )
        assertFalse(whileCooling.applyTransform)
        assertFalse(whileCooling.justLocked)

        pose.tick(
            deltaSeconds = WorldLockedPanelPlacementRules.RECENTER_COOLDOWN_SECONDS,
            headX = 0f,
            headY = 1.6f,
            headZ = 0f,
            rawForwardX = 0f,
            rawForwardZ = -1f,
            navigation = null,
        )
        val idle =
            pose.tick(
                deltaSeconds = 0.016f,
                headX = 0f,
                headY = 1.6f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                navigation = null,
            )
        assertFalse(idle.applyTransform)
        assertFalse(pose.isRecentering)
    }

    @Test
    fun worldLockedPoseRecentersSmoothlyThenRelocks() {
        val pose = WorldLockedPanelPose(1.0f)
        pose.lockTo(
            WorldLockedPanelPlacementRules.placeInFrontOfPlayer(
                headX = 0f,
                headY = 1.6f,
                headZ = 0f,
                rawForwardX = 0f,
                rawForwardZ = -1f,
                distanceMeters = 1.0f,
            ),
        )
        assertEquals(-1.0f, pose.z, 0.0001f)

        // Burn the post-lock cooldown so FOV loss can arm a recenter.
        pose.tick(
            deltaSeconds = WorldLockedPanelPlacementRules.RECENTER_COOLDOWN_SECONDS,
            headX = 0f,
            headY = 1.6f,
            headZ = 0f,
            rawForwardX = 0f,
            rawForwardZ = -1f,
            navigation = null,
        )
        assertFalse(pose.isRecentering)

        pose.tick(
            deltaSeconds = 0f,
            headX = 0f,
            headY = 1.6f,
            headZ = 0f,
            rawForwardX = 0f,
            rawForwardZ = 1f,
            navigation = null,
        )
        assertTrue(pose.isRecentering)

        pose.tick(
            deltaSeconds = 0.15f,
            headX = 0f,
            headY = 1.6f,
            headZ = 0f,
            rawForwardX = 0f,
            rawForwardZ = 1f,
            navigation = null,
        )
        assertTrue(pose.isRecentering)
        assertTrue(pose.z > -1.0f)

        pose.tick(
            deltaSeconds = 0.15f,
            headX = 0f,
            headY = 1.6f,
            headZ = 0f,
            rawForwardX = 0f,
            rawForwardZ = 1f,
            navigation = null,
        )
        assertFalse(pose.isRecentering)
        assertEquals(1.0f, pose.z, 0.0001f)
        assertEquals(180f, abs(pose.yawDegrees), 0.01f)
    }
}
