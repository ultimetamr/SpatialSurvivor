package com.example.spatialsurvivor.game

import android.util.Log
import com.example.spatialsurvivor.player.PlayerComponent
import com.pico.spatial.core.ecs.AnchorComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

data class HeadPoseSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val forwardX: Float,
    val forwardZ: Float,
)

/** Shared head sampling / transform writes for world-locked overlay panels. */
object WorldLockedPanelSupport {
    fun headFrom(tracking: SpatialTrackingSnapshot): HeadPoseSample {
        // Prefer any HMD sample over the Stage-origin fallback so panels do not lock meters away
        // when freshness gating briefly fails during menu/permanent opens.
        val pose = tracking.hmd?.hmdPose
        val head = pose?.position ?: DEFAULT_HEAD
        val forward = pose?.rotation?.rotateVector(Vector3.BACK) ?: Vector3.BACK
        return HeadPoseSample(
            x = head.x,
            y = head.y,
            z = head.z,
            forwardX = forward.x,
            forwardZ = forward.z,
        )
    }

    fun tickPose(
        pose: WorldLockedPanelPose,
        deltaSeconds: Float,
        tracking: SpatialTrackingSnapshot,
    ): WorldLockedPoseTick {
        // Delay the first lock until an HMD sample exists; otherwise the panel freezes at world origin.
        if (!pose.isLocked && tracking.hmd?.hmdPose == null) {
            return WorldLockedPoseTick(applyTransform = false, justLocked = false)
        }
        val head = headFrom(tracking)
        return pose.tick(
            deltaSeconds = deltaSeconds,
            headX = head.x,
            headY = head.y,
            headZ = head.z,
            rawForwardX = head.forwardX,
            rawForwardZ = head.forwardZ,
            navigation = SceneMeshRuntime.navigationSnapshot(),
        )
    }

    fun applyPose(transform: TransformComponent, pose: WorldLockedPanelPose) {
        transform.setPosition(Vector3(pose.x, pose.y, pose.z))
        transform.setEulerAngles(EulerAngles(0f, pose.yawDegrees, 0f))
    }

    fun detachCameraFollow(entity: Entity, label: String, tag: String) {
        val components = entity.components
        if (components[AnchorComponent::class.java] != null) {
            components.remove(AnchorComponent::class.java)
            Log.i(tag, "Removed AnchorComponent from $label panel to prevent camera follow")
        }
    }

    private val DEFAULT_HEAD = Vector3(0f, PlayerComponent.DEFAULT_HEAD_HEIGHT_METERS, 0f)
}
