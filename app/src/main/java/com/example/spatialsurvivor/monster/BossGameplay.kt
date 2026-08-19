package com.example.spatialsurvivor.monster

import android.util.Log
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.game.SceneMeshRuntime
import com.example.spatialsurvivor.game.WaveRules
import com.example.spatialsurvivor.game.SpatialManager
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.player.PlayerDamageRuntime
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** Final-boss spawning, mesh-aware chase, and telegraphed radial attack. */
object BossGameplay {
    private val playerQuery = EntityQueryCondition.hasComponent(PlayerComponent::class.java)
    private val bossQuery = EntityQueryCondition.hasComponent(FinalBossComponent::class.java)
    private val areaVisualQuery = EntityQueryCondition.hasComponent(BossAreaVisualComponent::class.java)

    fun fixedUpdate(scene: Scene, deltaSeconds: Float, elapsedSeconds: Float) {
        val playerEntity = scene.queryEntity(playerQuery).firstOrNull() ?: return
        val player = playerEntity.components[PlayerComponent::class.java] ?: return
        val root = playerEntity.getParent() ?: return
        val bossEntity = scene.queryEntity(bossQuery).firstOrNull() ?: return
        val boss = bossEntity.components[FinalBossComponent::class.java] ?: return
        val monster = bossEntity.components[MonsterComponent::class.java] ?: return
        val navigation = SceneMeshRuntime.navigationSnapshot()
        val playerWorldForGround = worldPosition(root, playerEntity)
        val playerFootY =
            playerWorldForGround?.let { SpatialManager.estimatePlayerFootY(it.y) }
        val groundY =
            MonsterGroundingRules.resolveGroundY(
                navigation.floorHeight.takeIf { navigation.isAvailable },
                playerFootY,
            )

        if (!boss.spawnedThisRun && WaveRules.shouldSpawnFinalBoss(elapsedSeconds)) {
            spawnBoss(root, scene, playerEntity, bossEntity, boss, monster, navigation, groundY)
        }
        if (!monster.active) {
            hideAreaVisual(scene)
            return
        }

        val bossPosition = worldPosition(root, bossEntity) ?: return
        val playerPosition = worldPosition(root, playerEntity) ?: return
        updateAreaAttack(
            root = root,
            scene = scene,
            player = player,
            playerPosition = playerPosition,
            bossPosition = bossPosition,
            boss = boss,
            deltaSeconds = deltaSeconds,
        )
        if (GameSessionRuntime.settlementVisible || boss.telegraphRemainingSeconds > 0f) return
        updateMeshChase(
            root,
            bossEntity,
            monster,
            bossPosition,
            playerPosition,
            navigation,
            groundY,
            deltaSeconds,
        )
    }

    private fun spawnBoss(
        root: Entity,
        scene: Scene,
        playerEntity: Entity,
        bossEntity: Entity,
        boss: FinalBossComponent,
        monster: MonsterComponent,
        navigation: SpatialNavigationMap,
        groundY: Float,
    ) {
        if (!navigation.isAvailable) return
        val playerWorld = worldPosition(root, playerEntity) ?: return
        val spawnPoint =
            navigation.findBoundarySpawn(
                playerX = playerWorld.x,
                playerZ = playerWorld.z,
                minimumDistanceMeters = MINIMUM_BOSS_SPAWN_DISTANCE_METERS,
                random = kotlin.random.Random(BOSS_SPAWN_SEED),
            ) ?: return

        MonsterGameplay.clearNormalMonsters(scene)
        monster.configure(MonsterType.FINAL_BOSS)
        val grounded =
            MonsterGroundingRules.groundedPoint(spawnPoint, monster, groundY)
        monster.activateWithGround(MonsterMovementState.CHASING, groundY)
        boss.spawnedThisRun = true
        boss.areaAttackCooldownSeconds = FinalBossComponent.INITIAL_AREA_ATTACK_DELAY_SECONDS
        boss.telegraphRemainingSeconds = 0f
        setWorldPosition(root, bossEntity, grounded)
        bossEntity.components[TransformComponent::class.java]?.let(MonsterVisualRules::applyGameplayRootPose)
        MonsterVisualRules.resetVisualScale(monster.visualChild, monster)
        MonsterGroundDebugVisual.ensureMarker(bossEntity, monster)
        MonsterGroundingRules.logSpawn(MonsterType.FINAL_BOSS, groundY, grounded, TAG)
        GameSessionRuntime.markBossActive()
        Log.i(
            TAG,
            "Final boss spawned at 20 minutes: hp=${monster.currentHealth}, " +
                "damage=${monster.contactDamage}, areaRadius=${BossCombatRules.AREA_ATTACK_RADIUS_METERS}m",
        )
    }

    private fun updateAreaAttack(
        root: Entity,
        scene: Scene,
        player: PlayerComponent,
        playerPosition: NavigationPoint,
        bossPosition: NavigationPoint,
        boss: FinalBossComponent,
        deltaSeconds: Float,
    ) {
        val visualEntity = scene.queryEntity(areaVisualQuery).firstOrNull()
        val visualTransform = visualEntity?.components?.get(TransformComponent::class.java)
        if (visualEntity != null) {
            setWorldPosition(
                root,
                visualEntity,
                bossPosition.copy(
                    y =
                        bossPosition.y - MonsterType.FINAL_BOSS.bodyCenterHeightMeters +
                            AREA_VISUAL_HEIGHT_METERS,
                ),
            )
        }

        if (boss.telegraphRemainingSeconds > 0f) {
            boss.telegraphRemainingSeconds =
                (boss.telegraphRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
            val progress =
                1f - boss.telegraphRemainingSeconds / BossCombatRules.AREA_ATTACK_TELEGRAPH_SECONDS
            visualTransform?.setScaleVector(Vector3(progress, progress, progress))
            if (boss.telegraphRemainingSeconds <= 0f) {
                if (BossCombatRules.isInsideAreaAttack(
                        playerX = playerPosition.x,
                        playerZ = playerPosition.z,
                        bossX = bossPosition.x,
                        bossZ = bossPosition.z,
                    )
                ) {
                    PlayerDamageRuntime.applyDamage(
                        player,
                        BossCombatRules.AREA_ATTACK_DAMAGE,
                        "Final boss area attack",
                    )
                }
                boss.areaAttackCooldownSeconds = BossCombatRules.AREA_ATTACK_COOLDOWN_SECONDS
                visualTransform?.setScaleVector(Vector3.ZERO)
                Log.i(TAG, "Final boss area attack resolved")
            }
            return
        }

        boss.areaAttackCooldownSeconds =
            (boss.areaAttackCooldownSeconds - deltaSeconds).coerceAtLeast(0f)
        if (boss.areaAttackCooldownSeconds <= 0f) {
            boss.telegraphRemainingSeconds = BossCombatRules.AREA_ATTACK_TELEGRAPH_SECONDS
            visualTransform?.setScaleVector(Vector3(0.05f, 0.05f, 0.05f))
            Log.i(TAG, "Final boss area attack telegraph started")
        } else {
            visualTransform?.setScaleVector(Vector3.ZERO)
        }
    }

    private fun updateMeshChase(
        root: Entity,
        entity: Entity,
        monster: MonsterComponent,
        position: NavigationPoint,
        playerPosition: NavigationPoint,
        navigation: SpatialNavigationMap,
        groundY: Float,
        deltaSeconds: Float,
    ) {
        monster.navigationCooldownSeconds -= deltaSeconds
        if (monster.navigationCooldownSeconds <= 0f ||
            monster.navigationRevision != navigation.revision
        ) {
            val waypoint = navigation.nextWaypoint(position, playerPosition)
            monster.waypointX = waypoint.x
            monster.waypointZ = waypoint.z
            monster.navigationRevision = navigation.revision
            monster.navigationCooldownSeconds = BOSS_REPATH_INTERVAL_SECONDS
        }
        val dx = monster.waypointX - position.x
        val dz = monster.waypointZ - position.z
        val distance = hypot(dx.toDouble(), dz.toDouble()).toFloat()
        if (distance <= WAYPOINT_REACHED_DISTANCE_METERS) {
            monster.navigationCooldownSeconds = 0f
            return
        }
        val travel = min(monster.moveSpeedMetersPerSecond * deltaSeconds, distance)
        val locked =
            MonsterGroundingRules.lockHorizontalMove(
                current = position,
                nextX = position.x + dx / distance * travel,
                nextZ = position.z + dz / distance * travel,
                monster = monster,
                groundY = groundY,
                logTag = TAG,
            )
        setWorldPosition(root, entity, locked)
        val yaw = (atan2(dx.toDouble(), dz.toDouble()) * 180.0 / PI).toFloat()
        entity.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, yaw, 0f))
    }

    fun resetScene(scene: Scene) {
        scene.queryEntity(bossQuery).forEach { entity ->
            entity.components[FinalBossComponent::class.java]?.reset()
            entity.components[MonsterComponent::class.java]?.configure(MonsterType.FINAL_BOSS)
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
        }
        hideAreaVisual(scene)
    }

    private fun hideAreaVisual(scene: Scene) {
        scene.queryEntity(areaVisualQuery).forEach { entity ->
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
        }
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

    private val HIDDEN_POSITION = Vector3(0f, -100f, 0f)
    private const val MINIMUM_BOSS_SPAWN_DISTANCE_METERS = 3f
    private const val BOSS_SPAWN_SEED = 0xB055
    private const val BOSS_REPATH_INTERVAL_SECONDS = 0.32f
    private const val WAYPOINT_REACHED_DISTANCE_METERS = 0.1f
    private const val AREA_VISUAL_HEIGHT_METERS = 0.035f
    private const val TAG = "BossGameplay"
}
