package com.example.spatialsurvivor.upgrade

import com.example.spatialsurvivor.monster.MonsterComponent
import com.example.spatialsurvivor.monster.MonsterDamageRuntime
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.player.PlayerStats
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EntityQueryCondition
import com.pico.spatial.core.ecs.Scene
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/** Fixed-step runtime for acquired weapons and range visuals. */
object UpgradeWeaponGameplay {
    private val playerQuery = EntityQueryCondition.hasComponent(PlayerComponent::class.java)
    private val monsterQuery = EntityQueryCondition.hasComponent(MonsterComponent::class.java)
    private val swordQuery = EntityQueryCondition.hasComponent(OrbitingSwordComponent::class.java)
    private val haloQuery = EntityQueryCondition.hasComponent(AttackRangeHaloComponent::class.java)
    private val poisonVisualQuery =
        EntityQueryCondition.hasComponent(PoisonAuraVisualComponent::class.java)
    private val lightningDomainQuery =
        EntityQueryCondition.hasComponent(LightningDomainVisualComponent::class.java)
    private var orbitDegrees: Float = 0f

    fun reset() {
        orbitDegrees = 0f
        SkillFxGameplay.reset()
    }

    fun resetScene(scene: Scene) {
        reset()
        scene.queryEntity(swordQuery).forEach { entity ->
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(HIDDEN_POSITION)
                setScaleVector(Vector3.ZERO)
            }
        }
        scene.queryEntity(poisonVisualQuery).forEach { entity ->
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
        }
        scene.queryEntity(lightningDomainQuery).forEach { entity ->
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
        }
        scene.queryEntity(haloQuery).forEach { entity ->
            entity.components[TransformComponent::class.java]?.setScaleVector(Vector3.ONE)
        }
        SkillFxGameplay.resetScene(scene)
    }

    fun fixedUpdate(scene: Scene, deltaSeconds: Float) {
        val playerEntity = scene.queryEntity(playerQuery).firstOrNull() ?: return
        val player = playerEntity.components[PlayerComponent::class.java] ?: return
        val playerPosition =
            playerEntity.components[TransformComponent::class.java]?.position ?: return
        val monsters = scene.queryEntity(monsterQuery)
        updateAttackHalo(scene, player)
        updatePoisonAura(scene, player, playerPosition, monsters, deltaSeconds)
        updateLightningDomainVisual(scene, player)
        updateOrbitingSwords(scene, player, playerPosition, monsters, deltaSeconds)
        updateAutomaticBurstSkills(player, playerPosition, monsters, deltaSeconds)
        SkillFxGameplay.fixedUpdate(scene, deltaSeconds)
    }

    private fun updateAttackHalo(scene: Scene, player: PlayerComponent) {
        val scale = player.attackRangeMeters / PlayerStats.DEFAULT_ATTACK_RANGE_METERS
        scene.queryEntity(haloQuery).firstOrNull()
            ?.components?.get(TransformComponent::class.java)
            ?.setScaleVector(Vector3(scale, scale, scale))
    }

    private fun updatePoisonAura(
        scene: Scene,
        player: PlayerComponent,
        playerPosition: Vector3,
        monsters: List<Entity>,
        deltaSeconds: Float,
    ) {
        val visualTransform =
            scene.queryEntity(poisonVisualQuery).firstOrNull()
                ?.components?.get(TransformComponent::class.java)
        if (player.poisonAuraStacks <= 0) {
            visualTransform?.setScaleVector(Vector3.ZERO)
            return
        }
        val evolved = UpgradeId.NETHER_POISON_DOMAIN in player.ownedEvolutions
        toggleDualVisual(
            scene.queryEntity(poisonVisualQuery).firstOrNull()
                ?.components?.get(PoisonAuraVisualComponent::class.java),
            evolved,
        )
        val evolutionScale = if (evolved) 2f else 1f
        // Always larger than the player's attack halo.
        val poisonRange =
            player.attackRangeMeters * POISON_AURA_RANGE_FACTOR * evolutionScale
        val visualScale = poisonRange / AURA_VISUAL_BASE_RADIUS_METERS
        visualTransform?.setScaleVector(Vector3(visualScale, 1f, visualScale))
        val rangeSquared = poisonRange * poisonRange
        val pulseInterval = AutomaticWeaponScalingRules.interval(POISON_PULSE_SECONDS, player.attackIntervalSeconds)
        val damagePerPulse = AutomaticWeaponScalingRules.damage(
            POISON_DAMAGE_PER_PULSE * player.poisonAuraStacks * evolutionScale.toInt(),
            player.projectileDamage,
        )
        monsters.forEach { entity ->
            val monster = entity.components[MonsterComponent::class.java] ?: return@forEach
            if (!monster.active) return@forEach
            val position = entity.components[TransformComponent::class.java]?.position ?: return@forEach
            if (distanceSquaredXZ(playerPosition, position) <= rangeSquared) {
                monster.poisonDamageAccumulatorSeconds += deltaSeconds
                while (monster.poisonDamageAccumulatorSeconds >= pulseInterval && monster.active) {
                    monster.poisonDamageAccumulatorSeconds -= pulseInterval
                    MonsterDamageRuntime.applyDamage(entity, damagePerPulse, "Poison aura")
                }
            } else {
                monster.poisonDamageAccumulatorSeconds = 0f
            }
        }
    }

    private fun updateOrbitingSwords(
        scene: Scene,
        player: PlayerComponent,
        playerPosition: Vector3,
        monsters: List<Entity>,
        deltaSeconds: Float,
    ) {
        monsters.forEach { entity ->
            entity.components[MonsterComponent::class.java]?.let { monster ->
                monster.swordContactCooldownSeconds =
                    (monster.swordContactCooldownSeconds - deltaSeconds).coerceAtLeast(0f)
            }
        }
        orbitDegrees = (orbitDegrees - ORBIT_DEGREES_PER_SECOND * deltaSeconds)
        if (orbitDegrees < 0f) orbitDegrees += 360f
        orbitDegrees %= 360f
        val swords = scene.queryEntity(swordQuery)
        val baseSwordCount = if (player.orbitingSwordStacks > 0) player.orbitingSwordStacks + 2 else 0
        val visibleCount = min(
            if (UpgradeId.MYRIAD_SWORDS in player.ownedEvolutions) baseSwordCount * 2 else baseSwordCount,
            swords.size,
        )
        val swordDamageBase = AutomaticWeaponScalingRules.damage(
            ORBITING_SWORD_BASE_DAMAGE +
                ORBITING_SWORD_STACK_DAMAGE * (player.orbitingSwordStacks - 1).coerceAtLeast(0),
            player.projectileDamage,
        )
        val swordDamage =
            if (UpgradeId.MYRIAD_SWORDS in player.ownedEvolutions) ceil(swordDamageBase * 2.2).toInt()
            else swordDamageBase
        val swordHitCooldown = AutomaticWeaponScalingRules.interval(
            ORBITING_SWORD_HIT_COOLDOWN_SECONDS,
            player.attackIntervalSeconds,
        )
        swords.forEach { entity ->
            val sword = entity.components[OrbitingSwordComponent::class.java] ?: return@forEach
            val transform = entity.components[TransformComponent::class.java] ?: return@forEach
            if (sword.orbitIndex >= visibleCount) {
                transform.setPosition(HIDDEN_POSITION)
                transform.setScaleVector(Vector3.ZERO)
                return@forEach
            }
            val phaseDegrees = orbitDegrees + 360f * sword.orbitIndex / visibleCount
            val radians = Math.toRadians(phaseDegrees.toDouble())
            val position =
                Vector3(
                    playerPosition.x + cos(radians).toFloat() * ORBIT_RADIUS_METERS,
                    ORBIT_HEIGHT_METERS,
                    playerPosition.z + sin(radians).toFloat() * ORBIT_RADIUS_METERS,
                )
            transform.setPosition(position)
            transform.setScaleVector(Vector3.ONE)
            // Face travel direction after reversing orbital sense.
            transform.setEulerAngles(EulerAngles(0f, phaseDegrees, ORBIT_SWORD_TILT_DEGREES))
            val evolved = UpgradeId.MYRIAD_SWORDS in player.ownedEvolutions
            toggleSwordVisual(sword, evolved)

            monsters.firstOrNull { monsterEntity ->
                val monster = monsterEntity.components[MonsterComponent::class.java]
                val monsterPosition =
                    monsterEntity.components[TransformComponent::class.java]?.position
                monster != null && monster.active && monster.swordContactCooldownSeconds <= 0f &&
                    monsterPosition != null &&
                    Vector3.distance(position, monsterPosition) <=
                    monster.contactRadiusMeters + ORBITING_SWORD_COLLISION_RADIUS_METERS
            }?.let { hit ->
                val monster = hit.components[MonsterComponent::class.java] ?: return@let
                monster.swordContactCooldownSeconds = swordHitCooldown
                MonsterDamageRuntime.applyDamage(hit, swordDamage, "Orbiting sword")
            }
        }
    }

    /** The late unlocks are deterministic area/target attacks and never block the combat clock. */
    private fun updateAutomaticBurstSkills(
        player: PlayerComponent,
        playerPosition: Vector3,
        monsters: List<Entity>,
        deltaSeconds: Float,
    ) {
        player.iceConeCooldownSeconds = (player.iceConeCooldownSeconds - deltaSeconds).coerceAtLeast(0f)
        player.lavaBombCooldownSeconds = (player.lavaBombCooldownSeconds - deltaSeconds).coerceAtLeast(0f)
        player.gravityBlackHoleCooldownSeconds = (player.gravityBlackHoleCooldownSeconds - deltaSeconds).coerceAtLeast(0f)
        player.swordRainCooldownSeconds = (player.swordRainCooldownSeconds - deltaSeconds).coerceAtLeast(0f)

        if (player.piercingIceConeStacks > 0 && player.iceConeCooldownSeconds <= 0f) {
            val evolved = UpgradeId.ABSOLUTE_ZERO in player.ownedEvolutions
            val targets = if (evolved) monsters.size else 3
            val hit = nearest(monsters, playerPosition, targets)
            damageNearest(monsters, playerPosition, targets, scaledDamage(ICE_CONE_DAMAGE * player.piercingIceConeStacks, player), "Piercing ice cone")
            hit.forEach { entity ->
                val pos = entity.components[TransformComponent::class.java]?.position ?: return@forEach
                SkillFxGameplay.request(
                    SkillFxSpawn(
                        kind = if (evolved) SkillVisualId.EVO_ABSOLUTE_ZERO else SkillVisualId.ICE_SPIKE,
                        worldPosition = Vector3(pos.x, pos.y + 0.35f, pos.z),
                        durationSeconds = 0.4f,
                    ),
                )
            }
            player.iceConeCooldownSeconds = scaledInterval(ICE_CONE_INTERVAL_SECONDS, player)
        }
        if (player.lavaBombStacks > 0 && player.lavaBombCooldownSeconds <= 0f) {
            nearest(monsters, playerPosition, 1).firstOrNull()?.let { target ->
                val center = target.components[TransformComponent::class.java]?.position ?: return@let
                val meteor = UpgradeId.METEOR_LAVA in player.ownedEvolutions
                val repeats = if (meteor) 3 else 1
                val radius = LAVA_BOMB_RADIUS_METERS * if (meteor) 1.5f else 1f
                repeat(repeats) {
                    damageWithin(monsters, center, radius, scaledDamage(LAVA_BOMB_DAMAGE * player.lavaBombStacks, player), "Lava bomb")
                    SkillFxGameplay.request(
                        SkillFxSpawn(
                            kind = if (meteor) SkillVisualId.EVO_METEOR_LAVA else SkillVisualId.LAVA_BOMB,
                            worldPosition = Vector3(center.x, center.y + 0.4f, center.z),
                            durationSeconds = 0.5f,
                        ),
                    )
                }
            }
            player.lavaBombCooldownSeconds = scaledInterval(LAVA_BOMB_INTERVAL_SECONDS, player)
        }
        if (player.gravityBlackHoleStacks > 0 && player.gravityBlackHoleCooldownSeconds <= 0f) {
            val evolved = UpgradeId.VOID_BLACK_HOLE in player.ownedEvolutions
            val targets = if (evolved) 8 else 4
            damageNearest(monsters, playerPosition, targets, scaledDamage(BLACK_HOLE_DAMAGE * player.gravityBlackHoleStacks, player), "Gravity black hole")
            SkillFxGameplay.request(
                SkillFxSpawn(
                    kind = if (evolved) SkillVisualId.EVO_VOID_BLACK_HOLE else SkillVisualId.BLACK_HOLE,
                    worldPosition = Vector3(playerPosition.x, playerPosition.y + 0.2f, playerPosition.z),
                    durationSeconds = 1.1f,
                ),
            )
            player.gravityBlackHoleCooldownSeconds = scaledInterval(BLACK_HOLE_INTERVAL_SECONDS, player)
        }
        if (player.swordRainStacks > 0 && player.swordRainCooldownSeconds <= 0f) {
            val hit = nearest(monsters, playerPosition, 5)
            damageNearest(monsters, playerPosition, 5, scaledDamage(SWORD_RAIN_DAMAGE * player.swordRainStacks, player), "Sword rain")
            hit.forEach { entity ->
                val pos = entity.components[TransformComponent::class.java]?.position ?: return@forEach
                SkillFxGameplay.request(
                    SkillFxSpawn(
                        kind = SkillVisualId.SWORD_RAIN_BLADE,
                        worldPosition = Vector3(pos.x, pos.y + 1.4f, pos.z),
                        durationSeconds = 0.55f,
                        eulerAngles = EulerAngles(75f, 0f, 0f),
                    ),
                )
            }
            player.swordRainCooldownSeconds = scaledInterval(SWORD_RAIN_INTERVAL_SECONDS, player)
        }
        if (player.lightningDomainStacks > 0) {
            player.lightningDomainAccumulatorSeconds += deltaSeconds
            val pulseInterval = scaledInterval(LIGHTNING_DOMAIN_PULSE_SECONDS, player)
            val lightningRange = player.attackRangeMeters * LIGHTNING_DOMAIN_RANGE_FACTOR
            while (player.lightningDomainAccumulatorSeconds >= pulseInterval) {
                player.lightningDomainAccumulatorSeconds -= pulseInterval
                damageWithin(
                    monsters,
                    playerPosition,
                    lightningRange,
                    scaledDamage(LIGHTNING_DOMAIN_DAMAGE * player.lightningDomainStacks, player),
                    "Lightning domain",
                )
            }
        }
    }

    private fun updateLightningDomainVisual(scene: Scene, player: PlayerComponent) {
        val entity = scene.queryEntity(lightningDomainQuery).firstOrNull() ?: return
        val transform = entity.components[TransformComponent::class.java] ?: return
        if (player.lightningDomainStacks <= 0) {
            transform.setScaleVector(Vector3.ZERO)
            return
        }
        val lightningRange = player.attackRangeMeters * LIGHTNING_DOMAIN_RANGE_FACTOR
        val visualScale = lightningRange / AURA_VISUAL_BASE_RADIUS_METERS
        transform.setScaleVector(Vector3(visualScale, 1f, visualScale))
    }

    private fun toggleDualVisual(component: PoisonAuraVisualComponent?, evolved: Boolean) {
        if (component == null) return
        setChildVisible(component.visualChild, !evolved)
        setChildVisible(component.evoVisualChild, evolved)
    }

    private fun toggleSwordVisual(sword: OrbitingSwordComponent, evolved: Boolean) {
        setChildVisible(sword.visualChild, !evolved)
        setChildVisible(sword.evoVisualChild, evolved)
    }

    private fun setChildVisible(child: Entity?, visible: Boolean) {
        val transform = child?.components?.get(TransformComponent::class.java) ?: return
        // Show/hide only; GLB unit correction lives on the loaded child under the visual wrapper.
        transform.setScaleVector(if (visible) Vector3.ONE else Vector3.ZERO)
    }

    private fun damageNearest(
        monsters: List<Entity>,
        origin: Vector3,
        count: Int,
        damage: Int,
        source: String,
    ) = nearest(monsters, origin, count).forEach { MonsterDamageRuntime.applyDamage(it, damage, source) }

    private fun nearest(monsters: List<Entity>, origin: Vector3, count: Int): List<Entity> =
        monsters.asSequence()
            .filter { it.components[MonsterComponent::class.java]?.active == true }
            .mapNotNull { entity ->
                entity.components[TransformComponent::class.java]?.position?.let { entity to distanceSquaredXZ(origin, it) }
            }
            .sortedBy { it.second }
            .take(count)
            .map { it.first }
            .toList()

    private fun damageWithin(
        monsters: List<Entity>,
        origin: Vector3,
        radiusMeters: Float,
        damage: Int,
        source: String,
    ) {
        val radiusSquared = radiusMeters * radiusMeters
        monsters.forEach { entity ->
            val position = entity.components[TransformComponent::class.java]?.position ?: return@forEach
            if (entity.components[MonsterComponent::class.java]?.active == true && distanceSquaredXZ(origin, position) <= radiusSquared) {
                MonsterDamageRuntime.applyDamage(entity, damage, source)
            }
        }
    }

    private fun distanceSquaredXZ(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private fun scaledDamage(baseDamage: Int, player: PlayerComponent): Int =
        AutomaticWeaponScalingRules.damage(baseDamage, player.projectileDamage)

    private fun scaledInterval(baseIntervalSeconds: Float, player: PlayerComponent): Float =
        AutomaticWeaponScalingRules.interval(baseIntervalSeconds, player.attackIntervalSeconds)

    private val HIDDEN_POSITION = Vector3(0f, -100f, 0f)
    /** Multiplier over the player's attack halo so poison always outranges the blue ring. */
    private const val POISON_AURA_RANGE_FACTOR = 1.35f
    private const val POISON_DAMAGE_PER_PULSE = 2
    private const val POISON_PULSE_SECONDS = 0.25f
    private const val ORBIT_RADIUS_METERS = 1.05f
    private const val ORBIT_HEIGHT_METERS = 0.5f
    private const val ORBIT_DEGREES_PER_SECOND = 115f
    private const val ORBIT_SWORD_TILT_DEGREES = 18f
    private const val ORBITING_SWORD_BASE_DAMAGE = 14
    private const val ORBITING_SWORD_STACK_DAMAGE = 6
    private const val ORBITING_SWORD_COLLISION_RADIUS_METERS = 0.22f
    private const val ORBITING_SWORD_HIT_COOLDOWN_SECONDS = 0.35f
    private const val ICE_CONE_DAMAGE = 11
    private const val ICE_CONE_INTERVAL_SECONDS = 1.4f
    private const val LAVA_BOMB_DAMAGE = 18
    private const val LAVA_BOMB_INTERVAL_SECONDS = 2.2f
    private const val LAVA_BOMB_RADIUS_METERS = 1.25f
    private const val BLACK_HOLE_DAMAGE = 8
    private const val BLACK_HOLE_INTERVAL_SECONDS = 3f
    private const val SWORD_RAIN_DAMAGE = 12
    private const val SWORD_RAIN_INTERVAL_SECONDS = 2.8f
    private const val LIGHTNING_DOMAIN_DAMAGE = 6
    /** Multiplier over the player's attack halo so lightning always outranges the blue ring. */
    private const val LIGHTNING_DOMAIN_RANGE_FACTOR = 1.55f
    private const val LIGHTNING_DOMAIN_PULSE_SECONDS = 0.4f
    /** Authored aura mesh radius used to map gameplay meters onto parent XZ scale. */
    private const val AURA_VISUAL_BASE_RADIUS_METERS = 1.35f
}

/** Pure shared scaling so post-skill attribute upgrades affect every automatic weapon. */
object AutomaticWeaponScalingRules {
    fun damage(baseDamage: Int, currentProjectileDamage: Int): Int =
        ceil(
            baseDamage.coerceAtLeast(0) *
                currentProjectileDamage.coerceAtLeast(0).toDouble() /
                PlayerStats.DEFAULT_PROJECTILE_DAMAGE,
        ).toInt()

    fun interval(baseIntervalSeconds: Float, currentAttackIntervalSeconds: Float): Float =
        (baseIntervalSeconds *
            currentAttackIntervalSeconds.coerceAtLeast(MINIMUM_ATTACK_INTERVAL_SECONDS) /
            PlayerStats.DEFAULT_ATTACK_INTERVAL_SECONDS)
            .coerceAtLeast(MINIMUM_ATTACK_INTERVAL_SECONDS)

    private const val MINIMUM_ATTACK_INTERVAL_SECONDS = 0.05f
}
