package com.example.spatialsurvivor

import com.example.spatialsurvivor.game.FixedStepClock
import com.example.spatialsurvivor.game.GameLoopRoute
import com.example.spatialsurvivor.game.GameLoopRoutingRules
import com.example.spatialsurvivor.game.GameTimeRules
import com.example.spatialsurvivor.exp.ExperienceProgressionRules
import com.example.spatialsurvivor.exp.ExperienceCrystalMotionRules
import com.example.spatialsurvivor.exp.ExperienceState
import com.example.spatialsurvivor.monster.MonsterType
import com.example.spatialsurvivor.monster.MonsterAttackRules
import com.example.spatialsurvivor.monster.MonsterMovementState
import com.example.spatialsurvivor.monster.ExperienceDropRequest
import com.example.spatialsurvivor.monster.MonsterDropRuntime
import com.example.spatialsurvivor.monster.NavigationBounds
import com.example.spatialsurvivor.monster.NavigationPoint
import com.example.spatialsurvivor.monster.ObstacleFootprint
import com.example.spatialsurvivor.monster.SpatialNavigationMap
import com.example.spatialsurvivor.player.CombatTarget
import com.example.spatialsurvivor.player.PlayerCombatRules
import com.example.spatialsurvivor.player.PlayerStats
import com.example.spatialsurvivor.upgrade.EvolutionOptionProvider
import com.example.spatialsurvivor.upgrade.UpgradeCatalog
import com.example.spatialsurvivor.upgrade.UpgradeCategory
import com.example.spatialsurvivor.upgrade.UpgradeId
import com.example.spatialsurvivor.upgrade.UpgradeMath
import com.example.spatialsurvivor.upgrade.UpgradeOption
import com.example.spatialsurvivor.upgrade.UpgradeProfile
import com.example.spatialsurvivor.upgrade.UpgradeRarity
import com.example.spatialsurvivor.upgrade.AutomaticWeaponScalingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ExampleUnitTest {
    @Test
    fun fixedClockRunsAtNinetyTicksPerSecond() {
        val clock = FixedStepClock(maxFrameDeltaSeconds = 1.0, maxStepsPerFrame = 100)
        var simulatedSeconds = 0f

        val steps = clock.advance(1f) { simulatedSeconds += it }

        assertEquals(90, steps)
        assertEquals(90L, clock.tick)
        assertEquals(1f, simulatedSeconds, 0.0001f)
    }

    @Test
    fun fixedClockCapsCatchUpWork() {
        val clock = FixedStepClock(maxStepsPerFrame = 4)

        val steps = clock.advance(1f) { }

        assertEquals(4, steps)
        assertEquals(4L, clock.tick)
    }

    @Test
    fun onlySettlementPausesCombatGameplayTime() {
        assertTrue(com.example.spatialsurvivor.game.GameTimeRules.shouldAdvanceGameplay(settlementActive = false))
        assertFalse(com.example.spatialsurvivor.game.GameTimeRules.shouldAdvanceGameplay(settlementActive = true))
    }

    @Test
    fun trackingPauseStopsOnlyNormalGameplay() {
        assertEquals(
            GameLoopRoute.TRACKING_PAUSED,
            GameLoopRoutingRules.select(
                settlementActive = false,
                trackingPaused = true,
            ),
        )
        assertEquals(
            GameLoopRoute.GAMEPLAY,
            GameLoopRoutingRules.select(
                settlementActive = false,
                trackingPaused = false,
            ),
        )
    }

    @Test
    fun upgradePauseStopsCombatTime() {
        assertFalse(GameTimeRules.shouldAdvanceGameplay(upgradeActive = true))
        assertEquals(
            GameLoopRoute.UPGRADE_PAUSED,
            GameLoopRoutingRules.select(false, false, upgradeActive = true),
        )
    }

    @Test
    fun mainMenuAndPermanentPanelPauseGameplayBeforeSettlement() {
        assertFalse(GameTimeRules.shouldAdvanceGameplay(mainMenuActive = true))
        assertFalse(GameTimeRules.shouldAdvanceGameplay(permanentPanelActive = true))
        assertFalse(GameTimeRules.shouldAdvanceGameplay(pausePanelActive = true))
        assertEquals(
            GameLoopRoute.MAIN_MENU,
            GameLoopRoutingRules.select(
                settlementActive = true,
                trackingPaused = false,
                mainMenuActive = true,
            ),
        )
        assertEquals(
            GameLoopRoute.PERMANENT_PANEL,
            GameLoopRoutingRules.select(
                settlementActive = true,
                trackingPaused = false,
                permanentPanelActive = true,
            ),
        )
        assertEquals(
            GameLoopRoute.PAUSE_PANEL,
            GameLoopRoutingRules.select(
                settlementActive = false,
                trackingPaused = false,
                pausePanelActive = true,
            ),
        )
    }

    @Test
    fun upgradeCatalogContainsTheCompleteRarityPool() {
        assertEquals(33, UpgradeCatalog.baseOptions.size)
        assertEquals(
            9,
            UpgradeCatalog.baseOptions.count { it.category == UpgradeCategory.WEAPON_SKILL },
        )
        assertEquals(
            14,
            UpgradeCatalog.baseOptions.count { it.category == UpgradeCategory.ATTRIBUTE },
        )
        assertEquals(
            10,
            UpgradeCatalog.baseOptions.count { it.category == UpgradeCategory.PASSIVE },
        )
    }

    @Test
    fun upgradeDrawReturnsThreeUniqueNormalCards() {
        val options = UpgradeCatalog.drawThree(Random(12), UpgradeProfile())

        assertEquals(3, options.size)
        assertEquals(3, options.map { it.id }.toSet().size)
    }

    @Test
    fun eligibleEvolutionReplacesOneNormalCardThroughReservedInterface() {
        val evolution =
            UpgradeOption(UpgradeId.MYRIAD_SWORDS)
        val provider = EvolutionOptionProvider { listOf(evolution) }

        val options =
            UpgradeCatalog.drawThree(
                random = Random(22),
                profile = UpgradeProfile(stacks = mapOf(UpgradeId.ORBITING_SWORD to 5)),
                evolutionProvider = provider,
            )

        assertEquals(3, options.size)
        assertTrue(options.contains(evolution))
    }

    @Test
    fun repeatedAttributeUpgradesStackMultiplicatively() {
        val damageAfterTwo = UpgradeMath.increasedDamage(UpgradeMath.increasedDamage(20))
        val intervalAfterTwo =
            UpgradeMath.fasterAttackInterval(UpgradeMath.fasterAttackInterval(1f))

        assertEquals(29, damageAfterTwo)
        assertEquals(0.7561f, intervalAfterTwo, 0.0002f)
        assertEquals(2.76f, UpgradeMath.increasedAttackRange(2.4f), 0.0001f)
    }

    @Test
    fun tenthUpgradeAlwaysContainsRareOrEpicAndNeverDuplicates() {
        repeat(100) { seed ->
            val options = UpgradeCatalog.draw(Random(seed), UpgradeProfile(completedUpgradeCount = 9))
            assertEquals(options.size, options.map { it.id }.toSet().size)
            assertTrue(options.any { it.rarity == UpgradeRarity.RARE || it.rarity == UpgradeRarity.EPIC })
        }
    }

    @Test
    fun eligibleLegendaryEvolutionIsForcedIntoTheDraw() {
        val profile = UpgradeProfile(
            stacks = mapOf(UpgradeId.ORBITING_SWORD to 5, UpgradeId.ATTACK_DAMAGE to 5),
        )
        val options = UpgradeCatalog.draw(Random(44), profile)
        assertTrue(options.any { it.id == UpgradeId.MYRIAD_SWORDS && it.rarity == UpgradeRarity.LEGENDARY })
    }

    @Test
    fun cappedAndOwnedUniqueEntriesAreFilteredBeforeDrawing() {
        val profile = UpgradeProfile(
            stacks = mapOf(UpgradeId.ATTACK_DAMAGE to 5),
            ownedUnique = setOf(UpgradeId.REROLL_MASTER),
        )
        val eligible = UpgradeCatalog.eligibleBaseOptions(profile).map { it.id }
        assertFalse(UpgradeId.ATTACK_DAMAGE in eligible)
        assertFalse(UpgradeId.REROLL_MASTER in eligible)
    }

    @Test
    fun globalDamageAndAttackSpeedScaleEveryAutomaticWeapon() {
        assertEquals(13, AutomaticWeaponScalingRules.damage(10, currentProjectileDamage = 26))
        assertEquals(
            0.8f,
            AutomaticWeaponScalingRules.interval(1f, currentAttackIntervalSeconds = 0.8f),
            0.0001f,
        )
    }

    @Test
    fun experienceMultiplierRoundsCrystalRewardUp() {
        assertEquals(2, UpgradeMath.experienceWithMultiplier(baseValue = 1, multiplier = 1.3f))
        assertEquals(6, UpgradeMath.experienceWithMultiplier(baseValue = 4, multiplier = 1.3f))
    }

    @Test
    fun playerDefaultsMatchCoreDesign() {
        val stats = PlayerStats()

        assertEquals(100, stats.maxHealth)
        assertEquals(2f, stats.attackRangeMeters, 0f)
        assertEquals(1f, stats.attackIntervalSeconds, 0f)
        assertEquals(0.5f, stats.pickupRangeMeters, 0f)
        assertEquals(20, stats.projectileDamage)
    }

    @Test
    fun experienceAccumulatesBeforeLevelRequirement() {
        val result = ExperienceProgressionRules.applyGain(ExperienceState(), amount = 2)

        assertFalse(result.leveledUp)
        assertEquals(1, result.state.level)
        assertEquals(2, result.state.currentExperience)
        assertEquals(5, result.state.experienceRequired)
    }

    @Test
    fun levelUpClearsExperienceAndRaisesNextRequirement() {
        val result =
            ExperienceProgressionRules.applyGain(
                ExperienceState(level = 1, currentExperience = 3, experienceRequired = 5),
                amount = 2,
            )

        assertTrue(result.leveledUp)
        assertEquals(2, result.state.level)
        assertEquals(0, result.state.currentExperience)
        assertEquals(8, result.state.experienceRequired)
    }

    @Test
    fun pickupRangeCanExpandForFutureUpgrades() {
        assertEquals(
            1.25f,
            ExperienceProgressionRules.expandedPickupRange(0.5f, 0.75f),
            0f,
        )
        assertEquals(
            ExperienceProgressionRules.MAXIMUM_PICKUP_RANGE_METERS,
            ExperienceProgressionRules.expandedPickupRange(7.8f, 1f),
            0f,
        )
    }

    @Test
    fun crystalPickupUsesDynamicHorizontalRange() {
        assertTrue(
            ExperienceCrystalMotionRules.isWithinPickupRangeXZ(
                crystalX = 0.3f,
                crystalZ = 0.4f,
                playerX = 0f,
                playerZ = 0f,
                pickupRangeMeters = 0.5f,
            ),
        )
        assertFalse(
            ExperienceCrystalMotionRules.isWithinPickupRangeXZ(
                crystalX = 0.31f,
                crystalZ = 0.4f,
                playerX = 0f,
                playerZ = 0f,
                pickupRangeMeters = 0.5f,
            ),
        )
    }

    @Test
    fun crystalHoverAnimationStaysWithinConfiguredAmplitude() {
        val offset =
            ExperienceCrystalMotionRules.hoverOffsetMeters(
                elapsedSeconds = 0.25f,
                amplitudeMeters = 0.045f,
                cyclesPerSecond = 0.7f,
            )

        assertTrue(abs(offset) <= 0.045f)
        assertTrue(abs(offset) > 0f)
    }

    @Test
    fun automaticAttackSelectsNearestActiveMonsterInsideRange() {
        val targets =
            listOf(
                CombatTarget("far", x = 0f, z = -1.9f),
                CombatTarget("inactive", x = 0f, z = -0.2f, active = false),
                CombatTarget("nearest", x = 0.8f, z = -0.5f),
                CombatTarget("outside", x = 0f, z = -2.1f),
            )

        val selected =
            PlayerCombatRules.nearestTargetIndex(
                playerX = 0f,
                playerZ = 0f,
                attackRangeMeters = 2f,
                targets = targets,
            )

        assertEquals(2, selected)
    }

    @Test
    fun automaticAttackReturnsNoTargetWhenEverythingIsOutsideRange() {
        val selected =
            PlayerCombatRules.nearestTargetIndex(
                playerX = 0f,
                playerZ = 0f,
                attackRangeMeters = 2f,
                targets = listOf(CombatTarget("outside", x = 2.01f, z = 0f)),
            )

        assertNull(selected)
    }

    @Test
    fun contactDamageClampsHealthAtZeroForGameOver() {
        assertEquals(88, PlayerCombatRules.healthAfterDamage(100, 12))
        assertEquals(0, PlayerCombatRules.healthAfterDamage(8, 12))
    }

    @Test
    fun everyChasingMonsterArchetypeCanActivelyMeleeAttack() {
        MonsterType.entries.forEach { type ->
            assertTrue(
                type.displayName,
                MonsterAttackRules.canMeleeAttack(
                    movementState = MonsterMovementState.CHASING,
                    horizontalDistanceSquared = 0f,
                    monsterRadiusMeters = type.collisionRadiusMeters,
                    playerRadiusMeters = 0.3f,
                    cooldownSeconds = 0f,
                ),
            )
        }
        assertFalse(
            MonsterAttackRules.canMeleeAttack(
                movementState = MonsterMovementState.DROPPING,
                horizontalDistanceSquared = 0f,
                monsterRadiusMeters = MonsterType.CEILING_DROPPER.collisionRadiusMeters,
                playerRadiusMeters = 0.3f,
                cooldownSeconds = 0f,
            ),
        )
    }

    @Test
    fun chasingMonstersSprintInsideAggroRadius() {
        assertEquals(
            MonsterAttackRules.AGGRO_SPRINT_SPEED_MULTIPLIER,
            MonsterAttackRules.pursuitSpeedMultiplier(2f),
            0f,
        )
        assertEquals(1f, MonsterAttackRules.pursuitSpeedMultiplier(3f), 0f)
    }

    @Test
    fun monsterArchetypesHaveDistinctCombatProfiles() {
        assertTrue(
            MonsterType.RUNNER.moveSpeedMetersPerSecond >
                MonsterType.NORMAL_BUG.moveSpeedMetersPerSecond,
        )
        assertTrue(MonsterType.ARMORED.maxHealth > MonsterType.NORMAL_BUG.maxHealth)
        assertTrue(MonsterType.ARMORED.contactDamage > MonsterType.NORMAL_BUG.contactDamage)
        assertTrue(
            MonsterType.ARMORED.moveSpeedMetersPerSecond <
                MonsterType.NORMAL_BUG.moveSpeedMetersPerSecond,
        )
    }

    @Test
    fun monsterDeathDropRuntimeForwardsExperienceValueAndWorldPosition() {
        val expected =
            ExperienceDropRequest(
                monsterType = MonsterType.ARMORED,
                experienceValue = MonsterType.ARMORED.experienceValue,
                worldPosition = NavigationPoint(1f, 0.5f, -2f),
            )
        var received: ExperienceDropRequest? = null
        MonsterDropRuntime.bind { request -> received = request }
        try {
            MonsterDropRuntime.request(expected)
        } finally {
            MonsterDropRuntime.reset()
        }

        assertEquals(expected, received)
    }

    @Test
    fun sceneMeshBoundarySpawnStaysAtLeastThreeMetersFromPlayer() {
        val navigation =
            SpatialNavigationMap.build(
                revision = 1L,
                bounds = NavigationBounds(-5f, 5f, -5f, 5f),
                obstacles = emptyList(),
                floorHeight = 0f,
                ceilingSpawnPoints = emptyList(),
            )

        val spawn =
            navigation.findBoundarySpawn(
                playerX = 0f,
                playerZ = 0f,
                minimumDistanceMeters = 3f,
                random = Random(42),
            )

        assertNotNull(spawn)
        val distanceSquared = spawn!!.x * spawn.x + spawn.z * spawn.z
        assertTrue(distanceSquared >= 9f)
    }

    @Test
    fun sceneMeshBoundarySpawnUsesFirstWalkableRingInsideWalls() {
        val navigation =
            SpatialNavigationMap.build(
                revision = 1L,
                bounds = NavigationBounds(-5f, 5f, -3f, 3f),
                obstacles =
                    listOf(
                        ObstacleFootprint(-5f, -4.85f, -3f, 3f),
                        ObstacleFootprint(4.85f, 5f, -3f, 3f),
                        ObstacleFootprint(-5f, 5f, -3f, -2.85f),
                        ObstacleFootprint(-5f, 5f, 2.85f, 3f),
                    ),
                floorHeight = 0f,
                ceilingSpawnPoints = emptyList(),
            )

        val spawn = navigation.findBoundarySpawn(0f, 0f, 3f, Random(42))

        assertNotNull(spawn)
        val distanceSquared = spawn!!.x * spawn.x + spawn.z * spawn.z
        assertTrue(distanceSquared >= 9f)
        assertFalse(navigation.isPointBlocked(spawn))
    }

    @Test
    fun ceilingSpawnUsesCeilingSemanticPointOutsidePlayerSafetyRadius() {
        val expected = NavigationPoint(4f, 2.8f, 0f)
        val navigation =
            SpatialNavigationMap.build(
                revision = 1L,
                bounds = NavigationBounds(-5f, 5f, -5f, 5f),
                obstacles = emptyList(),
                floorHeight = 0f,
                ceilingSpawnPoints =
                    listOf(
                        NavigationPoint(1f, 2.8f, 0f),
                        expected,
                    ),
            )

        val spawn = navigation.findCeilingSpawn(0f, 0f, 3f, Random(7))

        assertEquals(expected, spawn)
    }

    @Test
    fun sceneMeshPathfinderRoutesAroundFurnitureFootprint() {
        val navigation =
            SpatialNavigationMap.build(
                revision = 1L,
                bounds = NavigationBounds(-5f, 5f, -5f, 5f),
                obstacles =
                    listOf(
                        ObstacleFootprint(
                            minX = -0.5f,
                            maxX = 0.5f,
                            minZ = -2f,
                            maxZ = 2f,
                        ),
                    ),
                floorHeight = 0f,
                ceilingSpawnPoints = emptyList(),
            )
        val start = NavigationPoint(-4f, 0f, 0f)
        val target = NavigationPoint(4f, 0f, 0f)

        assertFalse(navigation.isSegmentWalkable(start, target))
        val path = navigation.findPath(start, target)

        assertTrue(path.isNotEmpty())
        assertTrue(path.none(navigation::isPointBlocked))
        assertTrue(path.any { abs(it.z) > 2.1f })
        assertTrue(navigation.isSegmentWalkable(start, navigation.nextWaypoint(start, target)))
    }
}
