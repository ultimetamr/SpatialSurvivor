package com.example.spatialsurvivor.exp

import android.util.Log
import com.example.spatialsurvivor.game.GameRuntime
import com.example.spatialsurvivor.monster.ExperienceDropRequest
import com.example.spatialsurvivor.monster.MonsterDeathDropSink
import com.example.spatialsurvivor.monster.MonsterDropRuntime
import com.example.spatialsurvivor.monster.NavigationPoint
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.upgrade.UpgradeMath
import com.example.spatialsurvivor.upgrade.UpgradeRuntime
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Fixed-step crystal spawning, hovering, attraction, absorption, and level progression. */
object ExperienceGameplay {
    private val playerQuery =
        EntityQueryCondition.hasComponent(PlayerComponent::class.java)
    private val crystalQuery =
        EntityQueryCondition.hasComponent(ExperienceCrystalComponent::class.java)
    private val pendingDrops = ConcurrentLinkedQueue<ExperienceDropRequest>()
    private val dropSink = MonsterDeathDropSink(pendingDrops::offer)
    private var didLogReady = false
    private var didLogPoolSaturated = false

    fun reset() {
        pendingDrops.clear()
        MonsterDropRuntime.bind(dropSink)
        didLogReady = false
        didLogPoolSaturated = false
    }

    fun resetScene(scene: Scene) {
        reset()
        clearCrystals(scene)
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float) {
        val playerEntity = scene.queryEntity(playerQuery).firstOrNull() ?: return
        val player = playerEntity.components[PlayerComponent::class.java] ?: return
        val root = playerEntity.getParent() ?: return
        val crystals = scene.queryEntity(crystalQuery)

        if (!didLogReady) {
            didLogReady = true
            Log.i(
                TAG,
                "EXP system ready: crystalPool=${crystals.size}, level=${player.level}, " +
                    "experience=${player.currentExperience}/${player.experienceRequired}, " +
                    "pickupRange=${player.pickupRangeMeters}m",
            )
        }

        spawnPendingDrops(scene, root, crystals, player)
        if (player.isGameOver) return

        val playerWorld = playerWorldPosition(root, player)
        crystals.forEach { entity ->
            val crystal =
                entity.components[ExperienceCrystalComponent::class.java] ?: return@forEach
            when (crystal.state) {
                ExperienceCrystalState.INACTIVE -> Unit
                ExperienceCrystalState.FLOATING ->
                    updateFloatingCrystal(
                        root,
                        entity,
                        crystal,
                        playerWorld,
                        player,
                        deltaSeconds,
                    )
                ExperienceCrystalState.ATTRACTING ->
                    updateAttractingCrystal(
                        scene,
                        root,
                        entity,
                        crystal,
                        player,
                        playerWorld,
                        deltaSeconds,
                    )
            }
        }
    }

    private fun spawnPendingDrops(
        scene: Scene,
        root: Entity,
        crystals: List<Entity>,
        player: PlayerComponent,
    ) {
        while (true) {
            val request = pendingDrops.peek() ?: return
            if (request.experienceValue <= 0) {
                pendingDrops.poll()
                continue
            }
            val entity =
                crystals.firstOrNull { candidate ->
                    candidate.components[ExperienceCrystalComponent::class.java]?.active == false
                }
            if (entity == null) {
                // Pool exhausted: award immediately so later kills never silently lose EXP.
                pendingDrops.poll()
                val dropValue = resolveDropValue(player, request.experienceValue)
                if (!didLogPoolSaturated) {
                    didLogPoolSaturated = true
                    Log.w(
                        TAG,
                        "EXP crystal pool saturated (${crystals.size}); granting direct EXP until slots free",
                    )
                }
                awardExperience(player, dropValue, dropValue)
                continue
            }
            pendingDrops.poll()
            val crystal = entity.components[ExperienceCrystalComponent::class.java] ?: continue
            val spawn = request.worldPosition.copy(y = request.worldPosition.y + DROP_HEIGHT_METERS)
            val dropValue = resolveDropValue(player, request.experienceValue)
            crystal.activate(dropValue, spawn.y)
            setWorldPosition(root, entity, spawn)
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3(1f, 1f, 1f))
            didLogPoolSaturated = false
            Log.i(
                TAG,
                "EXP crystal spawned: type=${request.monsterType}, value=$dropValue, " +
                    "world=(${spawn.x},${spawn.y},${spawn.z})",
            )
        }
    }

    private fun resolveDropValue(player: PlayerComponent, base: Int): Int =
        if (player.doubleCrystalOwned && Random.Default.nextFloat() < DOUBLE_CRYSTAL_CHANCE) {
            base * 2
        } else {
            base
        }

    private fun updateFloatingCrystal(
        root: Entity,
        entity: Entity,
        crystal: ExperienceCrystalComponent,
        playerWorld: NavigationPoint,
        player: PlayerComponent,
        deltaSeconds: Float,
    ) {
        crystal.animationSeconds += deltaSeconds
        crystal.rotationDegrees =
            (crystal.rotationDegrees + ROTATION_DEGREES_PER_SECOND * deltaSeconds) % 360f
        val position = worldPosition(root, entity) ?: return
        val hoverY =
            crystal.hoverCenterWorldY +
                ExperienceCrystalMotionRules.hoverOffsetMeters(
                    elapsedSeconds = crystal.animationSeconds,
                    amplitudeMeters = HOVER_AMPLITUDE_METERS,
                    cyclesPerSecond = HOVER_CYCLES_PER_SECOND,
                )
        setWorldPosition(root, entity, position.copy(y = hoverY))
        entity.components[TransformComponent::class.java]?.setEulerAngles(
            EulerAngles(CRYSTAL_TILT_DEGREES, crystal.rotationDegrees, CRYSTAL_TILT_DEGREES),
        )

        val passiveMultiplier = when {
            player.magneticFieldOwned -> 2f
            player.experienceMagnetOwned -> 1.35f
            else -> 1f
        }
        if (ExperienceCrystalMotionRules.isWithinPickupRangeXZ(
                crystalX = position.x,
                crystalZ = position.z,
                playerX = playerWorld.x,
                playerZ = playerWorld.z,
                pickupRangeMeters = player.pickupRangeMeters * passiveMultiplier,
            )
        ) {
            crystal.state = ExperienceCrystalState.ATTRACTING
            Log.i(TAG, "EXP crystal attraction started: value=${crystal.experienceValue}")
        }
    }

    private fun updateAttractingCrystal(
        scene: Scene,
        root: Entity,
        entity: Entity,
        crystal: ExperienceCrystalComponent,
        player: PlayerComponent,
        playerWorld: NavigationPoint,
        deltaSeconds: Float,
    ) {
        val position = worldPosition(root, entity) ?: return
        val dx = playerWorld.x - position.x
        val dy = playerWorld.y - position.y
        val dz = playerWorld.z - position.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        crystal.attractionSpeedMetersPerSecond =
            min(
                MAX_ATTRACTION_SPEED_METERS_PER_SECOND,
                crystal.attractionSpeedMetersPerSecond +
                    ATTRACTION_ACCELERATION_METERS_PER_SECOND_SQUARED * deltaSeconds,
            )
        val travel = crystal.attractionSpeedMetersPerSecond * deltaSeconds
        if (ExperienceCrystalMotionRules.spheresOverlap(
                distanceMeters = distance,
                firstRadiusMeters = crystal.pickupCollisionRadiusMeters,
                secondRadiusMeters = PlayerComponent.BODY_COLLISION_RADIUS_METERS,
            ) || travel >= distance
        ) {
            absorbCrystal(entity = entity, crystal = crystal, player = player)
            return
        }

        setWorldPosition(
            root,
            entity,
            NavigationPoint(
                x = position.x + dx / distance * travel,
                y = position.y + dy / distance * travel,
                z = position.z + dz / distance * travel,
            ),
        )
        crystal.rotationDegrees =
            (crystal.rotationDegrees + ATTRACTING_ROTATION_DEGREES_PER_SECOND * deltaSeconds) % 360f
        entity.components[TransformComponent::class.java]?.setEulerAngles(
            EulerAngles(CRYSTAL_TILT_DEGREES, crystal.rotationDegrees, CRYSTAL_TILT_DEGREES),
        )
    }

    private fun absorbCrystal(
        entity: Entity,
        crystal: ExperienceCrystalComponent,
        player: PlayerComponent,
    ) {
        val baseValue = crystal.experienceValue
        crystal.deactivate()
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(HIDDEN_POSITION)
            setScaleVector(Vector3(0f, 0f, 0f))
        }
        awardExperience(player, baseValue, baseValue)
    }

    private fun awardExperience(
        player: PlayerComponent,
        baseValue: Int,
        loggedBase: Int,
    ) {
        val value =
            UpgradeMath.experienceWithMultiplier(
                baseValue = baseValue,
                multiplier = player.experienceGainMultiplier,
            )
        val result =
            ExperienceProgressionRules.applyGain(
                ExperienceState(
                    level = player.level,
                    currentExperience = player.currentExperience,
                    experienceRequired = player.experienceRequired,
                ),
                value,
            )
        player.level = result.state.level
        player.currentExperience = result.state.currentExperience
        player.experienceRequired = result.state.experienceRequired
        Log.i(
            TAG,
            "EXP awarded: baseValue=$loggedBase, awarded=$value, level=${player.level}, " +
                "experience=${player.currentExperience}/${player.experienceRequired}",
        )
        if (result.leveledUp) {
            UpgradeRuntime.begin(player)
            Log.i(TAG, "Experience full: level=${player.level}; combat pause requested for upgrade modal")
        }
        GameRuntime.publishPlayerState(player)
    }

    private fun playerWorldPosition(root: Entity, player: PlayerComponent): NavigationPoint {
        val local =
            Vector3(
                player.trackedHeadX,
                player.trackedHeadY,
                player.trackedHeadZ,
            )
        val world = root.convertPositionTo(local, null)
        return NavigationPoint(world.x, world.y, world.z)
    }

    private fun worldPosition(root: Entity, entity: Entity): NavigationPoint? {
        val local = entity.components[TransformComponent::class.java]?.position ?: return null
        val world = root.convertPositionTo(local, null)
        return NavigationPoint(world.x, world.y, world.z)
    }

    private fun setWorldPosition(root: Entity, entity: Entity, point: NavigationPoint) {
        val local = root.convertPositionFrom(Vector3(point.x, point.y, point.z), null)
        entity.components[TransformComponent::class.java]?.setPosition(local)
    }

    fun clearCrystals(scene: Scene) {
        scene.queryEntity(crystalQuery).forEach { entity ->
            entity.components[ExperienceCrystalComponent::class.java]?.deactivate()
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
        }
    }

    private val HIDDEN_POSITION = Vector3(0f, -100f, 0f)
    private const val DROP_HEIGHT_METERS = 0.12f
    private const val HOVER_AMPLITUDE_METERS = 0.045f
    private const val DOUBLE_CRYSTAL_CHANCE = 0.15f
    private const val HOVER_CYCLES_PER_SECOND = 0.7f
    private const val ROTATION_DEGREES_PER_SECOND = 75f
    private const val ATTRACTING_ROTATION_DEGREES_PER_SECOND = 220f
    private const val CRYSTAL_TILT_DEGREES = 45f
    private const val ATTRACTION_ACCELERATION_METERS_PER_SECOND_SQUARED = 7f
    private const val MAX_ATTRACTION_SPEED_METERS_PER_SECOND = 6f
    private const val TAG = "ExperienceGameplay"
}
