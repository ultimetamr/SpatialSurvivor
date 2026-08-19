package com.example.spatialsurvivor.game

import com.example.spatialsurvivor.exp.ExperienceCrystalComponent
import com.example.spatialsurvivor.monster.MonsterComponent
import com.example.spatialsurvivor.monster.MonsterType
import com.example.spatialsurvivor.monster.MonsterVisualRules
import com.example.spatialsurvivor.monster.BossAreaVisualComponent
import com.example.spatialsurvivor.monster.BossCombatRules
import com.example.spatialsurvivor.monster.FinalBossComponent
import com.example.spatialsurvivor.player.PlayerComponent
import com.example.spatialsurvivor.player.PlayerStats
import com.example.spatialsurvivor.player.ProjectileComponent
import com.example.spatialsurvivor.upgrade.AttackRangeHaloComponent
import com.example.spatialsurvivor.upgrade.LightningDomainVisualComponent
import com.example.spatialsurvivor.upgrade.OrbitingSwordComponent
import com.example.spatialsurvivor.upgrade.PoisonAuraVisualComponent
import com.example.spatialsurvivor.upgrade.LevelUpRingComponent
import com.example.spatialsurvivor.upgrade.SkillFxComponent
import com.example.spatialsurvivor.upgrade.SkillFxGameplay
import com.example.spatialsurvivor.upgrade.SkillVisualAsyncLoader
import com.example.spatialsurvivor.upgrade.SkillVisualId
import com.example.spatialsurvivor.upgrade.SkillVisualRules
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** Builds the small pooled ECS scene used by the player-core vertical slice. */
object GameplaySceneFactory {
    data class BuildResult(
        val root: Entity,
        val monsters: List<Entity>,
        val skillHydrateJobs: List<SkillVisualAsyncLoader.Job>,
    )

    fun create(): BuildResult {
        val skillJobs = mutableListOf<SkillVisualAsyncLoader.Job>()
        val gameRoot = Entity().apply { setName("GameplayRoot") }
        gameRoot.addChild(createPlayerEntity(skillJobs))
        val monsters = createMonsterPool()
        monsters.forEach(gameRoot::addChild)
        gameRoot.addChild(createBossAreaVisual())
        createOrbitingSwordPool(skillJobs).forEach(gameRoot::addChild)
        createProjectilePool(skillJobs).forEach(gameRoot::addChild)
        createSkillFxPools(skillJobs).forEach(gameRoot::addChild)
        createExperienceCrystalPool().forEach(gameRoot::addChild)
        SkillFxGameplay.logPoolReady(skillJobs.size)
        return BuildResult(
            root = gameRoot,
            monsters = monsters,
            skillHydrateJobs = skillJobs,
        )
    }

    private fun createPlayerEntity(skillJobs: MutableList<SkillVisualAsyncLoader.Job>): Entity {
        val player =
            Entity().apply {
                setName("PhysicalPlayer")
                components.set(PlayerComponent())
            }

        val markerMaterial =
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(Color4(0.12f, 0.95f, 1f, 0.72f))
                setOpacity(0.72f)
            }
        val marker =
            ModelEntity(
                MeshResource.createCylinder(radius = 0.12f, height = 0.035f),
                markerMaterial,
            ).apply { setName("PlayerPositionMarker") }
        marker.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0.02f, 0f))
        player.addChild(marker)

        val haloMaterial =
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(Color4(0.05f, 0.75f, 1f, 0.3f))
                setOpacity(0.3f)
            }
        val halo =
            ModelEntity(
                MeshResource.createTorus(
                    outerRingRadius = PlayerStats.DEFAULT_ATTACK_RANGE_METERS,
                    innerRingRadius = PlayerStats.DEFAULT_ATTACK_RANGE_METERS - HALO_BAND_WIDTH_METERS,
                ),
                haloMaterial,
            ).apply { setName("AttackRangeHalo") }
        halo.components.set(AttackRangeHaloComponent())
        halo.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, 0.012f, 0f))
            setEulerAngles(EulerAngles(0f, 0f, 0f))
        }
        player.addChild(halo)

        player.addChild(createPoisonAuraEntity(skillJobs))
        player.addChild(createLightningDomainEntity(skillJobs))

        val levelUpMaterial =
            UnlitMaterial.create(BlendingMode.ADD).apply {
                setBaseColor(Color4(1f, 0.9f, 0.22f, 0.9f))
            }
        val levelUpRing =
            ModelEntity(
                MeshResource.createTorus(outerRingRadius = 0.55f, innerRingRadius = 0.47f),
                levelUpMaterial,
            ).apply {
                setName("LevelUpExpansionRing")
                components.set(LevelUpRingComponent())
            }
        levelUpRing.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, 0.04f, 0f))
            setEulerAngles(EulerAngles(0f, 0f, 0f))
            setScaleVector(Vector3.ZERO)
        }
        player.addChild(levelUpRing)

        val victoryMaterial =
            UnlitMaterial.create(BlendingMode.ADD).apply {
                setBaseColor(Color4(1f, 0.86f, 0.26f, 0.45f))
            }
        val victoryLight =
            ModelEntity(
                MeshResource.createSphere(radius = 0.18f),
                victoryMaterial,
            ).apply {
                setName("VictoryLightEffect")
                components.set(VictoryLightEffectComponent())
            }
        victoryLight.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, 0.2f, 0f))
            setScaleVector(Vector3.ZERO)
        }
        player.addChild(victoryLight)
        return player
    }

    private fun createPoisonAuraEntity(skillJobs: MutableList<SkillVisualAsyncLoader.Job>): Entity {
        val component = PoisonAuraVisualComponent()
        return Entity().apply {
            setName("PoisonAuraVisual")
            components.set(component)
            val base = SkillVisualRules.createPoolVisual(SkillVisualId.POISON_AURA)
            val evo = SkillVisualRules.createPoolVisual(SkillVisualId.EVO_DARK_POISON_DOMAIN)
            component.visualChild = base
            component.evoVisualChild = evo
            addChild(base)
            addChild(evo)
            evo.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
            queueHydrate(skillJobs, this, SkillVisualId.POISON_AURA) { glb ->
                component.visualChild =
                    replaceVisual(component.visualChild, glb)
            }
            queueHydrate(skillJobs, this, SkillVisualId.EVO_DARK_POISON_DOMAIN) { glb ->
                component.evoVisualChild =
                    replaceVisual(component.evoVisualChild, glb)
                component.evoVisualChild?.components?.get(TransformComponent::class.java)
                    ?.setScaleVector(Vector3.ZERO)
            }
            components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, 0.014f, 0f))
                setEulerAngles(EulerAngles(0f, 0f, 0f))
                setScaleVector(Vector3.ZERO)
            }
        }
    }

    private fun createLightningDomainEntity(
        skillJobs: MutableList<SkillVisualAsyncLoader.Job>,
    ): Entity {
        val component = LightningDomainVisualComponent()
        return Entity().apply {
            setName("LightningDomainVisual")
            components.set(component)
            val base = SkillVisualRules.createPoolVisual(SkillVisualId.LIGHTNING_FIELD)
            component.visualChild = base
            addChild(base)
            queueHydrate(skillJobs, this, SkillVisualId.LIGHTNING_FIELD) { glb ->
                component.visualChild = replaceVisual(component.visualChild, glb)
            }
            components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, 0.016f, 0f))
                setEulerAngles(EulerAngles(0f, 0f, 0f))
                setScaleVector(Vector3.ZERO)
            }
        }
    }

    private fun createOrbitingSwordPool(
        skillJobs: MutableList<SkillVisualAsyncLoader.Job>,
    ): List<Entity> =
        List(ORBITING_SWORD_POOL_SIZE) { index ->
            Entity().apply {
                setName("OrbitingSword${index + 1}")
                val sword = OrbitingSwordComponent(index)
                components.set(sword)
                val base = SkillVisualRules.createPoolVisual(SkillVisualId.ORBIT_SWORD)
                val evo = SkillVisualRules.createPoolVisual(SkillVisualId.EVO_TEN_THOUSAND_SWORDS)
                sword.visualChild = base
                sword.evoVisualChild = evo
                addChild(base)
                addChild(evo)
                evo.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
                queueHydrate(skillJobs, this, SkillVisualId.ORBIT_SWORD) { glb ->
                    sword.visualChild = replaceVisual(sword.visualChild, glb)
                }
                queueHydrate(skillJobs, this, SkillVisualId.EVO_TEN_THOUSAND_SWORDS) { glb ->
                    sword.evoVisualChild = replaceVisual(sword.evoVisualChild, glb)
                    sword.evoVisualChild?.components?.get(TransformComponent::class.java)
                        ?.setScaleVector(Vector3.ZERO)
                }
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, -100f, 0f))
                    setScaleVector(Vector3.ZERO)
                }
            }
        }

    private fun createProjectilePool(
        skillJobs: MutableList<SkillVisualAsyncLoader.Job>,
    ): List<Entity> =
        List(PROJECTILE_POOL_SIZE) { index ->
            Entity().apply {
                setName("EnergyProjectile${index + 1}")
                components.set(ProjectileComponent())
                val visual = SkillVisualRules.createPoolVisual(SkillVisualId.ENERGY_PROJECTILE)
                addChild(visual)
                queueHydrate(skillJobs, this, SkillVisualId.ENERGY_PROJECTILE) { glb ->
                    visual.destroy()
                    addChild(glb)
                }
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, -100f, 0f))
                    setScaleVector(Vector3.ZERO)
                }
            }
        }

    private fun createSkillFxPools(
        skillJobs: MutableList<SkillVisualAsyncLoader.Job>,
    ): List<Entity> {
        val kinds =
            listOf(
                SkillVisualId.ICE_SPIKE to 6,
                SkillVisualId.EVO_ABSOLUTE_ZERO to 4,
                SkillVisualId.LAVA_BOMB to 4,
                SkillVisualId.EVO_METEOR_LAVA to 4,
                SkillVisualId.BLACK_HOLE to 2,
                SkillVisualId.EVO_VOID_BLACK_HOLE to 2,
                SkillVisualId.SWORD_RAIN_BLADE to 8,
                SkillVisualId.CHAIN_LIGHTNING to 8,
                SkillVisualId.EVO_HEAVENLY_THUNDER to 6,
                SkillVisualId.FREEZE_PULSE to 1,
                SkillVisualId.EXPLOSION to 4,
            )
        return kinds.flatMap { (kind, count) ->
            List(count) { index ->
                SkillFxGameplay.createPoolSlot(kind, index).also { slot ->
                    val fx = slot.components[SkillFxComponent::class.java]
                    queueHydrate(skillJobs, slot, kind) { glb ->
                        fx?.visualChild =
                            slot.replaceVisual(fx?.visualChild, glb)
                    }
                }
            }
        }
    }

    private fun createMonsterPool(): List<Entity> =
        MonsterType.entries.flatMap { type ->
            List(type.poolSize()) { index ->
                Entity().apply {
                    setName("${type.name}_${index + 1}")
                    val monsterComponent =
                        MonsterComponent().apply {
                            lodUpdatePhase = index
                            configure(type)
                        }
                    components.set(monsterComponent)
                    if (type == MonsterType.FINAL_BOSS) {
                        components.set(FinalBossComponent())
                    }
                    val visual = MonsterVisualRules.createPoolVisual(type)
                    monsterComponent.visualChild = visual
                    addChild(visual)
                    components[TransformComponent::class.java]?.apply {
                        setPosition(Vector3(0f, -100f, 0f))
                        setScaleVector(Vector3(0f, 0f, 0f))
                    }
                }
            }
        }

    private fun createBossAreaVisual(): Entity {
        val material =
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(Color4(1f, 0.08f, 0.12f, 0.42f))
                setOpacity(0.42f)
            }
        return ModelEntity(
            MeshResource.createTorus(
                outerRingRadius = BossCombatRules.AREA_ATTACK_RADIUS_METERS,
                innerRingRadius = BossCombatRules.AREA_ATTACK_RADIUS_METERS - BOSS_AREA_BAND_METERS,
            ),
            material,
        ).apply {
            setName("FinalBossAreaTelegraph")
            components.set(BossAreaVisualComponent())
            components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, -100f, 0f))
                setScaleVector(Vector3.ZERO)
                setEulerAngles(EulerAngles(0f, 0f, 0f))
            }
        }
    }

    private fun createExperienceCrystalPool(): List<Entity> {
        val sharedMesh =
            MeshResource.createBox(
                size = Vector3(0.11f, 0.2f, 0.11f),
                cornerRadius = 0.018f,
            )
        val sharedMaterial =
            UnlitMaterial.create(BlendingMode.ADD).apply {
                setBaseColor(Color4(0.12f, 1f, 0.72f, 1f))
            }
        return List(EXPERIENCE_CRYSTAL_POOL_SIZE) { index ->
            ModelEntity(sharedMesh, sharedMaterial).apply {
                setName("ExperienceCrystal${index + 1}")
                components.set(ExperienceCrystalComponent())
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, -100f, 0f))
                    setScaleVector(Vector3(0f, 0f, 0f))
                    setEulerAngles(EulerAngles(45f, 0f, 45f))
                }
            }
        }
    }

    private fun queueHydrate(
        jobs: MutableList<SkillVisualAsyncLoader.Job>,
        parent: Entity,
        kind: SkillVisualId,
        assign: (Entity) -> Unit,
    ) {
        jobs += SkillVisualAsyncLoader.Job(parent, kind, assign)
    }

    private fun Entity.replaceVisual(previous: Entity?, replacement: Entity): Entity {
        previous?.destroy()
        addChild(replacement)
        return replacement
    }

    private const val HALO_BAND_WIDTH_METERS = 0.06f
    private const val PROJECTILE_POOL_SIZE = 8
    private const val EXPERIENCE_CRYSTAL_POOL_SIZE = 48
    private const val ORBITING_SWORD_POOL_SIZE = 14
    private const val BOSS_AREA_BAND_METERS = 0.09f

    private fun MonsterType.poolSize(): Int =
        when (this) {
            MonsterType.NORMAL_BUG -> 6
            MonsterType.RUNNER -> 5
            MonsterType.ARMORED -> 4
            MonsterType.CEILING_DROPPER -> 3
            MonsterType.FINAL_BOSS -> 1
        }
}
