package com.example.spatialsurvivor.monster

import android.util.Log
import com.example.spatialsurvivor.game.GameSessionRuntime
import com.example.spatialsurvivor.game.GameRuntime
import com.example.spatialsurvivor.game.SceneMeshRuntime
import com.example.spatialsurvivor.game.SpatialManager
import com.example.spatialsurvivor.game.WaveRules
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.upgrade.SkillFxGameplay
import com.example.spatialsurvivor.upgrade.SkillFxSpawn
import com.example.spatialsurvivor.upgrade.SkillVisualId
import com.example.spatialsurvivor.upgrade.UpgradeGameplay
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/** Fixed-step spawning, Scene Mesh pathfinding, movement, and death lifecycle. */
object MonsterGameplay {
    private val playerQuery =
        EntityQueryCondition.hasComponent(PlayerComponent::class.java)
    private val monsterQuery =
        EntityQueryCondition.hasComponent(MonsterComponent::class.java)

    private var random = Random(RANDOM_SEED)
    private var initialWaveSpawned = false
    private var spawnCooldownSeconds = 0f
    private var lastAppliedWave = 1

    fun reset() {
        random = Random(RANDOM_SEED)
        initialWaveSpawned = false
        spawnCooldownSeconds = 0f
        lastAppliedWave = 1
        // ExperienceGameplay owns MonsterDropRuntime.bind; do not unbind here.
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float) {
        val playerEntity = scene.queryEntity(playerQuery).firstOrNull() ?: return
        val player = playerEntity.components[PlayerComponent::class.java] ?: return
        val root = playerEntity.getParent() ?: return
        val playerLocalPosition =
            playerEntity.components[TransformComponent::class.java]?.position ?: return
        val playerWorldVector = root.convertPositionTo(playerLocalPosition, null)
        val playerFootY =
            SpatialManager.estimatePlayerFootY(playerWorldVector.y)
        val playerWorld =
            NavigationPoint(
                playerWorldVector.x,
                playerWorldVector.y,
                playerWorldVector.z,
            )
        val monsters =
            scene.queryEntity(monsterQuery).filter { entity ->
                entity.components[MonsterComponent::class.java]?.monsterType != MonsterType.FINAL_BOSS
            }
        val navigation = SceneMeshRuntime.navigationSnapshot()
        val groundY =
            MonsterGroundingRules.resolveGroundY(
                navigationFloorY = navigation.floorHeight.takeIf { navigation.isAvailable },
                playerFootY = playerFootY,
            )

        applyWaveTransition(monsters, GameSessionRuntime.currentWave)

        // Death/EXP must keep ticking during freeze pulse; only chase/spawn freeze.
        updateDeathEffects(root, monsters, player, groundY, deltaSeconds)
        if (player.isGameOver || GameSessionRuntime.bossActive) return
        if (UpgradeGameplay.monstersFrozenByPulse) return

        if (!initialWaveSpawned && navigation.isAvailable) {
            spawnInitialWave(root, monsters, playerWorld, navigation, groundY, GameSessionRuntime.currentWave)
            initialWaveSpawned = true
            spawnCooldownSeconds = SPAWN_INTERVAL_SECONDS
        }

        if (initialWaveSpawned) {
            spawnCooldownSeconds -= deltaSeconds
            val wave = GameSessionRuntime.currentWave
            if (spawnCooldownSeconds <= 0f &&
                activeMonsterCount(monsters) < WaveRules.activeMonsterLimit(wave)
            ) {
                spawnNext(root, monsters, playerWorld, navigation, groundY, wave)
                spawnCooldownSeconds = SPAWN_INTERVAL_SECONDS
            }
        }

        monsters.forEach { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@forEach
            val activeGroundY = groundY
            when (monster.movementState) {
                MonsterMovementState.DROPPING ->
                    updateDroppingMonster(root, entity, monster, activeGroundY, deltaSeconds)
                MonsterMovementState.CHASING ->
                    updateChasingMonster(
                        root = root,
                        entity = entity,
                        monster = monster,
                        playerWorld = playerWorld,
                        navigation = navigation,
                        groundY = activeGroundY,
                        deltaSeconds = deltaSeconds,
                    )
                MonsterMovementState.INACTIVE,
                MonsterMovementState.DYING,
                -> Unit
            }
        }
    }

    private fun spawnInitialWave(
        root: Entity,
        monsters: List<Entity>,
        playerWorld: NavigationPoint,
        navigation: SpatialNavigationMap,
        groundY: Float,
        wave: Int,
    ) {
        spawnBoundaryMonster(root, monsters, MonsterType.NORMAL_BUG, playerWorld, navigation, groundY, wave)
    }

    private fun spawnNext(
        root: Entity,
        monsters: List<Entity>,
        playerWorld: NavigationPoint,
        navigation: SpatialNavigationMap,
        groundY: Float,
        wave: Int,
    ) {
        val unlocked = WaveRules.unlockedTypes(wave)
        val candidates =
            unlocked.filter { type ->
                type.spawnMode != MonsterSpawnMode.CEILING_DROP ||
                    navigation.ceilingSpawnPoints.isNotEmpty()
            }
        if (candidates.isEmpty()) return
        val type = candidates.random(random)
        if (type.spawnMode == MonsterSpawnMode.CEILING_DROP) {
            spawnCeilingDropper(root, monsters, playerWorld, navigation, groundY, wave)
        } else {
            spawnBoundaryMonster(root, monsters, type, playerWorld, navigation, groundY, wave)
        }
    }

    private fun spawnBoundaryMonster(
        root: Entity,
        monsters: List<Entity>,
        type: MonsterType,
        playerWorld: NavigationPoint,
        navigation: SpatialNavigationMap,
        groundY: Float,
        wave: Int,
    ): Boolean {
        val poolEntity = findInactivePoolEntity(monsters, type) ?: return false
        val spawnPoint =
            navigation.findBoundarySpawn(
                playerX = playerWorld.x,
                playerZ = playerWorld.z,
                minimumDistanceMeters = MINIMUM_SPAWN_DISTANCE_METERS,
                random = random,
            ) ?: run {
                Log.w(
                    TAG,
                    "Mapped boundary has no valid spawn cell at least 3m from player: " +
                        "player=(${playerWorld.x},${playerWorld.z}), " +
                        "bounds=${navigation.bounds}, walkable=${navigation.walkableCellCount}",
                )
                return false
            }
        val monster = poolEntity.components[MonsterComponent::class.java] ?: return false
        monster.configure(
            type = type,
            healthMultiplier = WaveRules.healthMultiplier(wave),
            speedMultiplier = WaveRules.speedMultiplier(wave),
        )
        val grounded =
            MonsterGroundingRules.groundedPoint(
                xz = spawnPoint,
                monster = monster,
                groundY = groundY,
            )
        monster.activateWithGround(MonsterMovementState.CHASING, groundY)
        applyGroundedTransform(root, poolEntity, monster, grounded)
        MonsterGroundDebugVisual.ensureMarker(poolEntity, monster)
        MonsterGroundingRules.logSpawn(type, groundY, grounded, TAG)
        Log.i(
            TAG,
            "Spawned ${type.displayName} from Scene Mesh boundary: " +
                "speed=${monster.moveSpeedMetersPerSecond}, hp=${monster.currentHealth}, " +
                "damage=${monster.contactDamage}",
        )
        return true
    }

    private fun spawnCeilingDropper(
        root: Entity,
        monsters: List<Entity>,
        playerWorld: NavigationPoint,
        navigation: SpatialNavigationMap,
        groundY: Float,
        wave: Int,
    ): Boolean {
        val poolEntity =
            findInactivePoolEntity(monsters, MonsterType.CEILING_DROPPER) ?: return false
        val ceilingPoint =
            navigation.findCeilingSpawn(
                playerX = playerWorld.x,
                playerZ = playerWorld.z,
                minimumDistanceMeters = MINIMUM_SPAWN_DISTANCE_METERS,
                random = random,
            ) ?: return false
        val monster = poolEntity.components[MonsterComponent::class.java] ?: return false
        monster.configure(
            type = MonsterType.CEILING_DROPPER,
            healthMultiplier = WaveRules.healthMultiplier(wave),
            speedMultiplier = WaveRules.speedMultiplier(wave),
        )
        val landingY = MonsterGroundingRules.bodyCenterY(groundY, monster)
        monster.activateWithGround(MonsterMovementState.DROPPING, groundY)
        monster.landingWorldY = landingY
        setWorldPosition(
            root,
            poolEntity,
            ceilingPoint.copy(y = ceilingPoint.y - CEILING_SPAWN_INSET_METERS),
        )
        applyGameplayRootScale(poolEntity)
        MonsterGroundDebugVisual.ensureMarker(poolEntity, monster)
        Log.i(TAG, "Spawned 垂降怪 from CEILING semantic at y=${ceilingPoint.y}, landingY=$landingY")
        return true
    }

    private fun updateDroppingMonster(
        root: Entity,
        entity: Entity,
        monster: MonsterComponent,
        groundY: Float,
        deltaSeconds: Float,
    ) {
        val position = worldPosition(root, entity) ?: return
        val landingY = MonsterGroundingRules.bodyCenterY(groundY, monster)
        monster.landingWorldY = landingY
        val nextY = maxOf(landingY, position.y - CEILING_DROP_SPEED_METERS_PER_SECOND * deltaSeconds)
        setWorldPosition(root, entity, position.copy(y = nextY))
        if (nextY <= landingY + LANDING_EPSILON_METERS) {
            monster.movementState = MonsterMovementState.CHASING
            monster.navigationCooldownSeconds = 0f
            applyGroundedTransform(
                root,
                entity,
                monster,
                MonsterGroundingRules.groundedPoint(position, monster, groundY),
            )
            Log.i(TAG, "垂降怪 landed and switched to Scene Mesh chase at y=$landingY")
        }
    }

    private fun updateChasingMonster(
        root: Entity,
        entity: Entity,
        monster: MonsterComponent,
        playerWorld: NavigationPoint,
        navigation: SpatialNavigationMap,
        groundY: Float,
        deltaSeconds: Float,
    ) {
        val position = worldPosition(root, entity) ?: return
        val distanceToPlayer =
            hypot(
                (playerWorld.x - position.x).toDouble(),
                (playerWorld.z - position.z).toDouble(),
            ).toFloat()
        monster.aiAccumulatedSeconds += deltaSeconds
        val cadence = MonsterUpdateBudgetRules.cadenceTicks(distanceToPlayer)
        if (!MonsterUpdateBudgetRules.shouldUpdate(
                simulationTick = GameRuntime.simulationTick,
                phase = monster.lodUpdatePhase,
                cadenceTicks = cadence,
            )
        ) {
            return
        }
        val effectiveDeltaSeconds =
            monster.aiAccumulatedSeconds
                .coerceAtMost(MonsterUpdateBudgetRules.MAX_ACCUMULATED_DELTA_SECONDS)
        monster.aiAccumulatedSeconds = 0f
        monster.navigationCooldownSeconds -= effectiveDeltaSeconds
        if (monster.navigationCooldownSeconds <= 0f ||
            monster.navigationRevision != navigation.revision
        ) {
            val waypoint = navigation.nextWaypoint(position, playerWorld)
            monster.waypointX = waypoint.x
            monster.waypointZ = waypoint.z
            monster.navigationRevision = navigation.revision
            monster.navigationCooldownSeconds =
                BASE_REPATH_INTERVAL_SECONDS + monster.monsterType.ordinal * REPATH_JITTER_SECONDS
        }

        val dx = monster.waypointX - position.x
        val dz = monster.waypointZ - position.z
        val distance = hypot(dx.toDouble(), dz.toDouble()).toFloat()
        if (distance <= WAYPOINT_REACHED_DISTANCE_METERS) {
            monster.navigationCooldownSeconds = 0f
            return
        }
        val pursuitSpeed =
            monster.moveSpeedMetersPerSecond *
                MonsterAttackRules.pursuitSpeedMultiplier(distanceToPlayer)
        val travel = min(pursuitSpeed * effectiveDeltaSeconds, distance)
        val nextX = position.x + dx / distance * travel
        val nextZ = position.z + dz / distance * travel
        val locked =
            MonsterGroundingRules.lockHorizontalMove(
                current = position,
                nextX = nextX,
                nextZ = nextZ,
                monster = monster,
                groundY = groundY,
                logTag = TAG,
            )
        setWorldPosition(root, entity, locked)
        MonsterGroundingRules.logMove(monster.monsterType, groundY, locked, TAG)
        entity.components[TransformComponent::class.java]?.setEulerAngles(
            com.pico.spatial.core.math.EulerAngles(
                pitch = 0f,
                yaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat(),
                roll = 0f,
            ),
        )
    }

    private fun updateDeathEffects(
        root: Entity,
        monsters: List<Entity>,
        player: PlayerComponent,
        groundY: Float,
        deltaSeconds: Float,
    ) {
        monsters.forEach { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@forEach
            if (monster.movementState != MonsterMovementState.DYING) return@forEach
            val transform = entity.components[TransformComponent::class.java] ?: return@forEach
            val position = worldPosition(root, entity) ?: return@forEach
            val activeGroundY = groundY
            monster.deathEffectRemainingSeconds =
                (monster.deathEffectRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
            val progress =
                monster.deathEffectRemainingSeconds / MonsterComponent.DEATH_EFFECT_DURATION_SECONDS
            MonsterVisualRules.applyVisualPulse(monster.visualChild, monster.visualScale, progress)
            val locked =
                MonsterGroundingRules.lockDeathY(position, monster, activeGroundY)
            setWorldPosition(root, entity, locked)
            MonsterVisualRules.applyGameplayRootPose(transform)
            if (monster.deathEffectRemainingSeconds <= 0f) {
                if (!monster.deathDropRequested) {
                    monster.deathDropRequested = true
                    MonsterDropRuntime.request(
                        ExperienceDropRequest(
                            monsterType = monster.monsterType,
                            experienceValue = monster.experienceValue,
                            worldPosition = locked,
                        ),
                    )
                    if (player.explosiveRemainsOwned) {
                        SkillFxGameplay.request(
                            SkillFxSpawn(
                                kind = SkillVisualId.EXPLOSION,
                                worldPosition = Vector3(locked.x, locked.y + 0.35f, locked.z),
                                durationSeconds = 0.45f,
                            ),
                        )
                    }
                }
                monster.movementState = MonsterMovementState.INACTIVE
                transform.setPosition(HIDDEN_POSITION)
                transform.setScaleVector(Vector3.ZERO)
                MonsterVisualRules.resetVisualScale(monster.visualChild, monster)
                MonsterGroundDebugVisual.hideMarker(monster)
                Log.i(TAG, "${monster.monsterType.displayName} death disappearance effect completed")
            }
        }
    }

    private fun applyGroundedTransform(
        root: Entity,
        entity: Entity,
        monster: MonsterComponent,
        point: NavigationPoint,
    ) {
        setWorldPosition(root, entity, point)
        entity.components[TransformComponent::class.java]?.let(MonsterVisualRules::applyGameplayRootPose)
        MonsterVisualRules.resetVisualScale(monster.visualChild, monster)
    }

    private fun applyGameplayRootScale(entity: Entity) {
        entity.components[TransformComponent::class.java]?.let(MonsterVisualRules::applyGameplayRootPose)
    }

    private fun findInactivePoolEntity(monsters: List<Entity>, type: MonsterType): Entity? =
        monsters.firstOrNull { entity ->
            entity.components[MonsterComponent::class.java]?.let { monster ->
                monster.monsterType == type &&
                    monster.movementState == MonsterMovementState.INACTIVE
            } == true
        }

    private fun activeMonsterCount(monsters: List<Entity>): Int =
        monsters.count { entity ->
            entity.components[MonsterComponent::class.java]?.active == true
        }

    private fun applyWaveTransition(monsters: List<Entity>, wave: Int) {
        if (wave <= lastAppliedWave) return
        val healthRatio =
            WaveRules.healthMultiplier(wave) / WaveRules.healthMultiplier(lastAppliedWave)
        val speedRatio =
            WaveRules.speedMultiplier(wave) / WaveRules.speedMultiplier(lastAppliedWave)
        monsters.forEach { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@forEach
            if (!monster.active) return@forEach
            monster.maxHealth = ceil(monster.maxHealth * healthRatio).toInt()
            monster.currentHealth = ceil(monster.currentHealth * healthRatio).toInt()
            monster.moveSpeedMetersPerSecond *= speedRatio
        }
        lastAppliedWave = wave
        Log.i(TAG, "Applied cumulative wave $wave scaling to active normal monsters")
    }

    fun clearNormalMonsters(scene: Scene) {
        scene.queryEntity(monsterQuery).forEach { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@forEach
            if (monster.monsterType == MonsterType.FINAL_BOSS) return@forEach
            monster.configure(monster.monsterType)
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
            MonsterGroundDebugVisual.hideMarker(monster)
        }
        Log.i(TAG, "Normal monsters cleared for final boss")
    }

    fun clearAllNonBossMonsters(scene: Scene) {
        clearNormalMonsters(scene)
    }

    fun resetScene(scene: Scene) {
        scene.queryEntity(monsterQuery).forEach { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@forEach
            monster.configure(monster.monsterType)
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
            MonsterGroundDebugVisual.hideMarker(monster)
        }
        reset()
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
    private const val RANDOM_SEED = 0x5EED
    private const val MINIMUM_SPAWN_DISTANCE_METERS = 3f
    private const val SPAWN_INTERVAL_SECONDS = 3f
    private const val CEILING_SPAWN_INSET_METERS = 0.12f
    private const val CEILING_DROP_SPEED_METERS_PER_SECOND = 2.2f
    private const val LANDING_EPSILON_METERS = 0.01f
    private const val BASE_REPATH_INTERVAL_SECONDS = 0.42f
    private const val REPATH_JITTER_SECONDS = 0.035f
    private const val WAYPOINT_REACHED_DISTANCE_METERS = 0.08f
    private const val TAG = "MonsterGameplay"
}
