package com.example.spatialsurvivor.upgrade

import com.example.spatialsurvivor.game.WorldLockedPanelPlacement
import com.example.spatialsurvivor.game.WorldLockedPanelPlacementRules
import com.example.spatialsurvivor.monster.SpatialNavigationMap

/** Thin aliases so upgrade call sites / tests keep a stable import path. */
object UpgradePanelPlacementRules {
    const val DISTANCE_METERS = WorldLockedPanelPlacementRules.UPGRADE_DISTANCE_METERS
    const val OBSTACLE_CLEARANCE_METERS = WorldLockedPanelPlacementRules.OBSTACLE_CLEARANCE_METERS

    fun placeInFrontOfPlayer(
        headX: Float,
        headY: Float,
        headZ: Float,
        rawForwardX: Float,
        rawForwardZ: Float,
        navigation: SpatialNavigationMap? = null,
        distanceMeters: Float = DISTANCE_METERS,
        clearanceMeters: Float = OBSTACLE_CLEARANCE_METERS,
    ): WorldLockedPanelPlacement =
        WorldLockedPanelPlacementRules.placeInFrontOfPlayer(
            headX = headX,
            headY = headY,
            headZ = headZ,
            rawForwardX = rawForwardX,
            rawForwardZ = rawForwardZ,
            navigation = navigation,
            distanceMeters = distanceMeters,
            clearanceMeters = clearanceMeters,
        )
}
