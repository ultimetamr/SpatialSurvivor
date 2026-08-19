package com.example.spatialsurvivor.player

import android.util.Log
import com.example.spatialsurvivor.game.GameRuntime
import com.example.spatialsurvivor.game.SpatialTrackingSnapshot
import com.example.spatialsurvivor.monster.MonsterAttackRules
import com.example.spatialsurvivor.monster.MonsterComponent
import com.example.spatialsurvivor.monster.MonsterDamageRuntime
import com.example.spatialsurvivor.progression.PermanentProgressionRuntime
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlin.random.Random
import com.example.spatialsurvivor.upgrade.SkillFxGameplay
import com.example.spatialsurvivor.upgrade.UpgradeId

/** Fixed-step Spatial ECS gameplay for tracking, attacks, projectiles, and player damage. */
object PlayerGameplay {
    private var didLogReady = false
    private var didLogTracking = false
    private val playerQuery =
        EntityQueryCondition.hasComponent(PlayerComponent::class.java)
    private val monsterQuery =
        EntityQueryCondition.hasComponent(MonsterComponent::class.java)
    private val projectileQuery =
        EntityQueryCondition.hasComponent(ProjectileComponent::class.java)

    fun fixedUpdate(
        scene: Scene,
        deltaSeconds: Float,
        tracking: SpatialTrackingSnapshot,
    ) {
        val playerEntity = scene.queryEntity(playerQuery).firstOrNull() ?: return
        val player = playerEntity.components[PlayerComponent::class.java] ?: return
        val monsters = scene.queryEntity(monsterQuery)
        val projectiles = scene.queryEntity(projectileQuery)

        if (!didLogReady) {
            didLogReady = true
            Log.i(
                TAG,
                "Player core ready: hp=${player.currentHealth}/${player.maxHealth}, " +
                    "range=${player.attackRangeMeters}m, monsterPool=${monsters.size}, " +
                    "projectilePool=${projectiles.size}",
            )
        }

        synchronizePhysicalPosition(playerEntity, player, tracking)
        updateMonsterMeleeAttacks(playerEntity, player, monsters, deltaSeconds)
        updateHealthRegeneration(player, deltaSeconds)

        if (player.isGameOver) {
            deactivateAll(projectiles)
            GameRuntime.publishPlayerState(player)
            return
        }

        updateAutomaticAttack(playerEntity, player, monsters, projectiles, deltaSeconds)
        updateProjectiles(player, monsters, projectiles, deltaSeconds)
        GameRuntime.publishPlayerState(player)
    }

    fun resetDiagnostics() {
        didLogReady = false
        didLogTracking = false
    }

    private fun synchronizePhysicalPosition(
        playerEntity: Entity,
        player: PlayerComponent,
        tracking: SpatialTrackingSnapshot,
    ) {
        tracking.hmd?.hmdPose?.position?.let { trackingPosition ->
            // Tracking poses are in Stage/world space. Player transforms are local to the identity game root.
            val rootPosition =
                playerEntity.getParent()?.convertPositionFrom(trackingPosition, null)
                    ?: trackingPosition
            player.trackedHeadX = rootPosition.x
            player.trackedHeadY = rootPosition.y
            player.trackedHeadZ = rootPosition.z
            player.hasTrackingPose = true
            if (!didLogTracking) {
                didLogTracking = true
                Log.i(TAG, "HMD world pose synchronized to player ECS entity")
            }
        }

        playerEntity.components[TransformComponent::class.java]?.setPosition(
            Vector3(player.trackedHeadX, FLOOR_MARKER_HEIGHT_METERS, player.trackedHeadZ),
        )
    }

    private fun updateAutomaticAttack(
        playerEntity: Entity,
        player: PlayerComponent,
        monsters: List<Entity>,
        projectiles: List<Entity>,
        deltaSeconds: Float,
    ) {
        if (player.energyProjectileStacks <= 0) return
        player.attackCooldownSeconds =
            (player.attackCooldownSeconds - deltaSeconds).coerceAtLeast(0f)
        if (player.attackCooldownSeconds > 0f) return

        val targets = findNearestMonsters(playerEntity, player.attackRangeMeters, monsters, player.projectileCount)
        if (targets.isEmpty()) return
        val fired = targets.fold(false) { anyFired, target ->
            activateProjectile(playerEntity, player, target, projectiles) || anyFired
        }
        if (fired) {
            player.attackCooldownSeconds = player.attackIntervalSeconds
        }
    }

    private fun findNearestMonster(
        playerEntity: Entity,
        attackRangeMeters: Float,
        monsters: List<Entity>,
    ): Entity? {
        val playerPosition =
            playerEntity.components[TransformComponent::class.java]?.position ?: return null
        var nearest: Entity? = null
        var bestDistanceSquared = attackRangeMeters * attackRangeMeters

        monsters.forEach { monsterEntity ->
            val monster = monsterEntity.components[MonsterComponent::class.java] ?: return@forEach
            if (!monster.active) return@forEach
            val position =
                monsterEntity.components[TransformComponent::class.java]?.position ?: return@forEach
            val distanceSquared = distanceSquaredXZ(playerPosition, position)
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared
                nearest = monsterEntity
            }
        }
        return nearest
    }

    private fun findNearestMonsters(
        playerEntity: Entity,
        attackRangeMeters: Float,
        monsters: List<Entity>,
        count: Int,
    ): List<Entity> {
        val playerPosition = playerEntity.components[TransformComponent::class.java]?.position ?: return emptyList()
        val rangeSquared = attackRangeMeters * attackRangeMeters
        val targets = monsters.filter { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@filter false
            val position = entity.components[TransformComponent::class.java]?.position ?: return@filter false
            monster.active && distanceSquaredXZ(playerPosition, position) <= rangeSquared
        }.sortedBy { entity ->
            distanceSquaredXZ(playerPosition, entity.components[TransformComponent::class.java]!!.position)
        }
        if (targets.isEmpty()) return emptyList()
        return List(count.coerceAtLeast(1)) { targets[it % targets.size] }
    }

    private fun updateHealthRegeneration(player: PlayerComponent, deltaSeconds: Float) {
        if (player.healthRegenerationPerSecond <= 0 || player.currentHealth >= player.maxHealth) return
        player.healthRegenerationAccumulatorSeconds += deltaSeconds
        while (player.healthRegenerationAccumulatorSeconds >= 1f) {
            player.healthRegenerationAccumulatorSeconds -= 1f
            player.currentHealth =
                (player.currentHealth + player.healthRegenerationPerSecond).coerceAtMost(player.maxHealth)
        }
    }

    private fun activateProjectile(
        playerEntity: Entity,
        player: PlayerComponent,
        targetEntity: Entity,
        projectiles: List<Entity>,
    ): Boolean {
        val projectileEntity =
            projectiles.firstOrNull {
                it.components[ProjectileComponent::class.java]?.active == false
            } ?: return false
        val projectile = projectileEntity.components[ProjectileComponent::class.java] ?: return false
        val playerPosition =
            playerEntity.components[TransformComponent::class.java]?.position ?: return false
        val targetPosition =
            targetEntity.components[TransformComponent::class.java]?.position ?: return false
        val origin =
            Vector3(
                playerPosition.x,
                projectileOriginY(player),
                playerPosition.z,
            )
        val dx = targetPosition.x - origin.x
        val dy = targetPosition.y - origin.y
        val dz = targetPosition.z - origin.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length <= MIN_DIRECTION_LENGTH) return false

        projectile.active = true
        projectile.directionX = dx / length
        projectile.directionY = dy / length
        projectile.directionZ = dz / length
        projectile.speedMetersPerSecond = player.projectileSpeedMetersPerSecond
        projectile.remainingDistanceMeters = player.attackRangeMeters + PROJECTILE_RANGE_MARGIN_METERS
        projectile.damage =
            if (player.criticalChance > 0f && Random.Default.nextFloat() < player.criticalChance) {
                ceil(player.projectileDamage * player.criticalDamageMultiplier).toInt()
            } else {
                player.projectileDamage
            }
        projectile.remainingPierces = player.pierceCount
        projectileEntity.components[TransformComponent::class.java]?.apply {
            setPosition(origin)
            setScaleVector(Vector3(1f, 1f, 1f))
        }
        Log.i(
            TAG,
            "Energy projectile fired: damage=${projectile.damage}, " +
                "speed=${projectile.speedMetersPerSecond}m/s",
        )
        return true
    }

    private fun updateProjectiles(
        player: PlayerComponent,
        monsters: List<Entity>,
        projectiles: List<Entity>,
        deltaSeconds: Float,
    ) {
        projectiles.forEach { projectileEntity ->
            val projectile =
                projectileEntity.components[ProjectileComponent::class.java] ?: return@forEach
            if (!projectile.active) return@forEach
            val transform =
                projectileEntity.components[TransformComponent::class.java] ?: return@forEach
            val travel = projectile.speedMetersPerSecond * deltaSeconds
            val nextPosition =
                Vector3(
                    transform.position.x + projectile.directionX * travel,
                    transform.position.y + projectile.directionY * travel,
                    transform.position.z + projectile.directionZ * travel,
                )
            transform.setPosition(nextPosition)
            projectile.remainingDistanceMeters -= travel

            val hitMonster = findProjectileHit(nextPosition, monsters)
            if (hitMonster != null) {
                MonsterDamageRuntime.applyDamage(hitMonster, projectile.damage, "Energy projectile")
                applyChainLightning(player, hitMonster, monsters, projectile.damage)
                if (projectile.remainingPierces > 0) {
                    projectile.remainingPierces -= 1
                    transform.setPosition(
                        Vector3(
                            nextPosition.x + projectile.directionX * PIERCE_SKIP_METERS,
                            nextPosition.y + projectile.directionY * PIERCE_SKIP_METERS,
                            nextPosition.z + projectile.directionZ * PIERCE_SKIP_METERS,
                        ),
                    )
                } else {
                    deactivateProjectile(projectileEntity, projectile)
                }
            } else if (projectile.remainingDistanceMeters <= 0f) {
                deactivateProjectile(projectileEntity, projectile)
            }
        }
    }

    private fun findProjectileHit(position: Vector3, monsters: List<Entity>): Entity? =
        monsters.firstOrNull { monsterEntity ->
            val monster = monsterEntity.components[MonsterComponent::class.java]
            val monsterPosition = monsterEntity.components[TransformComponent::class.java]?.position
            if (monster == null || !monster.active || monsterPosition == null) {
                false
            } else {
                val radius = monster.contactRadiusMeters + ProjectileComponent.COLLISION_RADIUS_METERS
                distanceSquared(position, monsterPosition) <= radius * radius
            }
        }

    private fun applyChainLightning(
        player: PlayerComponent,
        primaryTarget: Entity,
        monsters: List<Entity>,
        baseDamage: Int,
    ) {
        if (player.chainLightningStacks <= 0) return
        val origin = primaryTarget.components[TransformComponent::class.java]?.position ?: return
        val evolved = UpgradeId.NINE_HEAVENS_THUNDER in player.ownedEvolutions
        val multiplier =
            CHAIN_LIGHTNING_BASE_DAMAGE_MULTIPLIER +
                CHAIN_LIGHTNING_STACK_DAMAGE_MULTIPLIER * (player.chainLightningStacks - 1)
        val firstBounceDamage = kotlin.math.ceil(baseDamage * multiplier).toInt().coerceAtLeast(1)
        val rangeSquared = CHAIN_LIGHTNING_RANGE_METERS * CHAIN_LIGHTNING_RANGE_METERS
        var previous = origin
        monsters
            .asSequence()
            .filter { it !== primaryTarget }
            .filter { it.components[MonsterComponent::class.java]?.active == true }
            .mapNotNull { entity ->
                entity.components[TransformComponent::class.java]?.position?.let { position ->
                    entity to distanceSquared(origin, position)
                }
            }
            .filter { (_, distanceSquared) -> distanceSquared <= rangeSquared }
            .sortedBy { (_, distanceSquared) -> distanceSquared }
            .take(if (evolved) 6 else CHAIN_LIGHTNING_MAX_BOUNCES)
            .forEachIndexed { index, (entity, _) ->
                val decay = if (evolved) 1.0 else Math.pow(0.8, index.toDouble())
                MonsterDamageRuntime.applyDamage(entity, ceil(firstBounceDamage * decay).toInt(), "Chain lightning")
                val next = entity.components[TransformComponent::class.java]?.position
                if (next != null) {
                    SkillFxGameplay.requestBolt(previous, next, evolved)
                    previous = next
                }
            }
    }

    /** Every grounded/chasing monster actively attacks once it reaches melee distance. */
    private fun updateMonsterMeleeAttacks(
        playerEntity: Entity,
        player: PlayerComponent,
        monsters: List<Entity>,
        deltaSeconds: Float,
    ) {
        val playerPosition =
            playerEntity.components[TransformComponent::class.java]?.position ?: return

        monsters.forEach { monsterEntity ->
            val monster = monsterEntity.components[MonsterComponent::class.java] ?: return@forEach
            if (!monster.active) return@forEach
            monster.contactCooldownSeconds =
                (monster.contactCooldownSeconds - deltaSeconds).coerceAtLeast(0f)
            val monsterPosition =
                monsterEntity.components[TransformComponent::class.java]?.position ?: return@forEach
            val canAttack =
                MonsterAttackRules.canMeleeAttack(
                    movementState = monster.movementState,
                    horizontalDistanceSquared = distanceSquaredXZ(playerPosition, monsterPosition),
                    monsterRadiusMeters = monster.contactRadiusMeters,
                    playerRadiusMeters = PlayerComponent.BODY_COLLISION_RADIUS_METERS,
                    cooldownSeconds = monster.contactCooldownSeconds,
                )
            if (canAttack) {
                PlayerDamageRuntime.applyDamage(
                    player = player,
                    damage = monster.contactDamage,
                    source = monster.monsterType.displayName,
                )
                monster.contactCooldownSeconds = MonsterComponent.CONTACT_DAMAGE_INTERVAL_SECONDS
            }
        }
    }

    fun resetScene(scene: Scene) {
        scene.queryEntity(playerQuery).firstOrNull()?.let { entity ->
            val player = entity.components[PlayerComponent::class.java]
            player?.reset()
            player?.let(PermanentProgressionRuntime::applyToPlayer)
            entity.components[TransformComponent::class.java]?.setPosition(
                Vector3(0f, FLOOR_MARKER_HEIGHT_METERS, 0f),
            )
        }
        clearProjectiles(scene)
        resetDiagnostics()
    }

    fun clearProjectiles(scene: Scene) {
        deactivateAll(scene.queryEntity(projectileQuery))
    }

    private fun deactivateAll(projectiles: List<Entity>) {
        projectiles.forEach { entity ->
            entity.components[ProjectileComponent::class.java]?.let { projectile ->
                deactivateProjectile(entity, projectile)
            }
        }
    }

    private fun deactivateProjectile(entity: Entity, projectile: ProjectileComponent) {
        projectile.active = false
        projectile.remainingDistanceMeters = 0f
        projectile.remainingPierces = 0
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(HIDDEN_POSITION)
            setScaleVector(Vector3(0f, 0f, 0f))
        }
    }

    private fun projectileOriginY(player: PlayerComponent): Float =
        (player.trackedHeadY - PROJECTILE_HEAD_OFFSET_METERS)
            .coerceIn(MIN_PROJECTILE_HEIGHT_METERS, MAX_PROJECTILE_HEIGHT_METERS)

    private fun distanceSquaredXZ(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private fun distanceSquared(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return dx * dx + dy * dy + dz * dz
    }

    private val HIDDEN_POSITION = Vector3(0f, -100f, 0f)
    private const val FLOOR_MARKER_HEIGHT_METERS = 0.02f
    private const val PROJECTILE_HEAD_OFFSET_METERS = 0.35f
    private const val MIN_PROJECTILE_HEIGHT_METERS = 0.75f
    private const val MAX_PROJECTILE_HEIGHT_METERS = 1.35f
    private const val PROJECTILE_RANGE_MARGIN_METERS = 0.35f
    private const val MIN_DIRECTION_LENGTH = 0.0001f
    private const val PIERCE_SKIP_METERS = 0.32f
    private const val CHAIN_LIGHTNING_MAX_BOUNCES = 3
    private const val CHAIN_LIGHTNING_RANGE_METERS = 2.2f
    private const val CHAIN_LIGHTNING_BASE_DAMAGE_MULTIPLIER = 0.45f
    private const val CHAIN_LIGHTNING_STACK_DAMAGE_MULTIPLIER = 0.15f
    private const val TAG = "PlayerGameplay"
}
