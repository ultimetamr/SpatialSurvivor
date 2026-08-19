package com.example.spatialsurvivor.upgrade

import com.example.spatialsurvivor.domain.model.UpgradeLoadout
import kotlin.math.ceil
import kotlin.random.Random

enum class UpgradeCategory { WEAPON_SKILL, ATTRIBUTE, PASSIVE, EVOLUTION }

enum class UpgradeRarity(val baseWeight: Int) {
    COMMON(70), RARE(25), EPIC(4), LEGENDARY(1),
}

enum class UpgradeId(val category: UpgradeCategory) {
    ORBITING_SWORD(UpgradeCategory.WEAPON_SKILL), ENERGY_PROJECTILE(UpgradeCategory.WEAPON_SKILL),
    POISON_AURA(UpgradeCategory.WEAPON_SKILL), CHAIN_LIGHTNING(UpgradeCategory.WEAPON_SKILL),
    PIERCING_ICE_CONE(UpgradeCategory.WEAPON_SKILL), LAVA_BOMB(UpgradeCategory.WEAPON_SKILL),
    GRAVITY_BLACK_HOLE(UpgradeCategory.WEAPON_SKILL), SWORD_RAIN(UpgradeCategory.WEAPON_SKILL),
    LIGHTNING_DOMAIN(UpgradeCategory.WEAPON_SKILL),
    ATTACK_DAMAGE(UpgradeCategory.ATTRIBUTE), ATTACK_SPEED(UpgradeCategory.ATTRIBUTE),
    ATTACK_RANGE(UpgradeCategory.ATTRIBUTE), PICKUP_RANGE(UpgradeCategory.ATTRIBUTE),
    EXPERIENCE_GAIN(UpgradeCategory.ATTRIBUTE), MAX_HEALTH(UpgradeCategory.ATTRIBUTE),
    HEALTH_REGENERATION(UpgradeCategory.ATTRIBUTE), PROJECTILE_COUNT(UpgradeCategory.ATTRIBUTE),
    PIERCE_COUNT(UpgradeCategory.ATTRIBUTE), CRITICAL_CHANCE(UpgradeCategory.ATTRIBUTE),
    DAMAGE_REDUCTION(UpgradeCategory.ATTRIBUTE), CRITICAL_DAMAGE(UpgradeCategory.ATTRIBUTE),
    DODGE_CHANCE(UpgradeCategory.ATTRIBUTE), REGENERATING_SHIELD(UpgradeCategory.ATTRIBUTE),
    EXPERIENCE_MAGNET(UpgradeCategory.PASSIVE), KILL_HEAL(UpgradeCategory.PASSIVE),
    MAGNETIC_FIELD(UpgradeCategory.PASSIVE), EXPLOSIVE_REMAINS(UpgradeCategory.PASSIVE),
    FREEZE_PULSE(UpgradeCategory.PASSIVE), REROLL_MASTER(UpgradeCategory.PASSIVE),
    NEAR_DEATH_PROTECTION(UpgradeCategory.PASSIVE), EXTRA_OPTION(UpgradeCategory.PASSIVE),
    DOUBLE_CRYSTAL(UpgradeCategory.PASSIVE), GATHERING_AURA(UpgradeCategory.PASSIVE),
    MYRIAD_SWORDS(UpgradeCategory.EVOLUTION), NINE_HEAVENS_THUNDER(UpgradeCategory.EVOLUTION),
    NETHER_POISON_DOMAIN(UpgradeCategory.EVOLUTION), METEOR_LAVA(UpgradeCategory.EVOLUTION),
    ABSOLUTE_ZERO(UpgradeCategory.EVOLUTION), VOID_BLACK_HOLE(UpgradeCategory.EVOLUTION),
    MOVEMENT_SPEED(UpgradeCategory.PASSIVE),
}

data class UpgradeDefinition(
    val id: UpgradeId,
    val rarity: UpgradeRarity,
    val title: String,
    val description: String,
    val maxLevel: Int = 1,
    val unique: Boolean = maxLevel == 1,
)

data class UpgradeOption(val id: UpgradeId) {
    val definition: UpgradeDefinition get() = UpgradeCatalog.definition(id)
    val category: UpgradeCategory get() = id.category
    val rarity: UpgradeRarity get() = definition.rarity
    val isEvolution: Boolean get() = rarity == UpgradeRarity.LEGENDARY
}

typealias UpgradeProfile = UpgradeLoadout

fun interface EvolutionOptionProvider {
    fun eligibleOptions(profile: UpgradeProfile): List<UpgradeOption>
}

object EmptyEvolutionOptionProvider : EvolutionOptionProvider {
    override fun eligibleOptions(profile: UpgradeProfile): List<UpgradeOption> = emptyList()
}

object UpgradeCatalog {
    private fun d(id: UpgradeId, rarity: UpgradeRarity, title: String, description: String, max: Int = 1) =
        UpgradeDefinition(id, rarity, title, description, maxLevel = max, unique = max == 1)

    val definitions = listOf(
        d(UpgradeId.ORBITING_SWORD, UpgradeRarity.COMMON, "环绕飞剑", "3把飞剑环绕玩家，接触造成单体伤害。", 5),
        d(UpgradeId.ENERGY_PROJECTILE, UpgradeRarity.COMMON, "能量弹", "自动锁定最近怪物发射直线能量弹。", 5),
        d(UpgradeId.POISON_AURA, UpgradeRarity.COMMON, "毒雾光环", "玩家周身持续造成毒素范围伤害。", 5),
        d(UpgradeId.ATTACK_DAMAGE, UpgradeRarity.COMMON, "全伤害 +20%", "所有武器伤害提高20%。", 5),
        d(UpgradeId.ATTACK_SPEED, UpgradeRarity.COMMON, "攻击速度 +15%", "所有自动攻击间隔缩短。", 5),
        d(UpgradeId.ATTACK_RANGE, UpgradeRarity.COMMON, "攻击范围 +15%", "锁定与范围技能距离提高15%。", 5),
        d(UpgradeId.PICKUP_RANGE, UpgradeRarity.COMMON, "拾取范围 +0.3米", "经验晶石吸附距离扩大。", 5),
        d(UpgradeId.EXPERIENCE_GAIN, UpgradeRarity.COMMON, "经验获取 +20%", "晶石提供的经验提高20%。", 5),
        d(UpgradeId.MAX_HEALTH, UpgradeRarity.COMMON, "最大生命 +25", "最大生命和当前生命增加25。", 4),
        d(UpgradeId.HEALTH_REGENERATION, UpgradeRarity.COMMON, "生命恢复 +1", "每秒恢复1点生命。", 5),
        d(UpgradeId.EXPERIENCE_MAGNET, UpgradeRarity.COMMON, "经验磁铁", "晶石生成后会缓慢飘向玩家。"),
        d(UpgradeId.KILL_HEAL, UpgradeRarity.COMMON, "击杀回血", "每击杀怪物恢复0.5生命。"),
        d(UpgradeId.CHAIN_LIGHTNING, UpgradeRarity.RARE, "连锁闪电", "命中后弹射3个敌人，每次衰减20%。", 5),
        d(UpgradeId.PIERCING_ICE_CONE, UpgradeRarity.RARE, "穿透冰锥", "贯穿3个敌人并减速30%，持续2秒。", 5),
        d(UpgradeId.LAVA_BOMB, UpgradeRarity.RARE, "熔岩爆弹", "爆炸范围伤害并附加3秒灼烧。", 5),
        d(UpgradeId.PROJECTILE_COUNT, UpgradeRarity.RARE, "投射物数量 +1", "每轮自动攻击额外发射1个投射物。", 3),
        d(UpgradeId.PIERCE_COUNT, UpgradeRarity.RARE, "穿透次数 +1", "投射物额外贯穿1个目标。", 3),
        d(UpgradeId.CRITICAL_CHANCE, UpgradeRarity.RARE, "暴击概率 +10%", "攻击暴击概率提高10%。", 5),
        d(UpgradeId.DAMAGE_REDUCTION, UpgradeRarity.RARE, "伤害减免 +10%", "受到的伤害降低10%。", 5),
        d(UpgradeId.MAGNETIC_FIELD, UpgradeRarity.RARE, "磁吸领域", "更远处晶石也会缓慢吸附。"),
        d(UpgradeId.EXPLOSIVE_REMAINS, UpgradeRarity.RARE, "爆炸残骸", "怪物死亡时对周围敌人造成爆炸伤害。"),
        d(UpgradeId.FREEZE_PULSE, UpgradeRarity.RARE, "冻结脉冲", "每次升级确认后冻结全场怪物2秒。"),
        d(UpgradeId.REROLL_MASTER, UpgradeRarity.RARE, "重roll大师", "每次升级获得1次刷新卡牌机会。"),
        d(UpgradeId.GRAVITY_BLACK_HOLE, UpgradeRarity.EPIC, "引力黑洞", "每5秒生成黑洞牵引并持续伤害。", 3),
        d(UpgradeId.SWORD_RAIN, UpgradeRarity.EPIC, "剑雨轰击", "每4秒从上空进行大范围多段轰击。", 3),
        d(UpgradeId.LIGHTNING_DOMAIN, UpgradeRarity.EPIC, "雷电领域", "常驻雷击领域并有10%概率麻痹。", 3),
        d(UpgradeId.CRITICAL_DAMAGE, UpgradeRarity.EPIC, "暴击伤害 +50%", "暴击额外伤害提高50%。", 3),
        d(UpgradeId.DODGE_CHANCE, UpgradeRarity.EPIC, "闪避概率 +10%", "获得10%闪避概率。", 3),
        d(UpgradeId.REGENERATING_SHIELD, UpgradeRarity.EPIC, "再生护盾", "获得20%最大生命护盾，脱战5秒恢复。", 3),
        d(UpgradeId.NEAR_DEATH_PROTECTION, UpgradeRarity.EPIC, "濒死庇护", "低于20%生命时获得50%减伤3秒。"),
        d(UpgradeId.EXTRA_OPTION, UpgradeRarity.EPIC, "额外选项", "后续升级弹窗由三选一变为四选一。"),
        d(UpgradeId.DOUBLE_CRYSTAL, UpgradeRarity.EPIC, "双倍晶石", "怪物有15%概率掉落双倍经验晶石。"),
        d(UpgradeId.GATHERING_AURA, UpgradeRarity.EPIC, "聚怪光环", "怪物会更积极地向玩家聚拢。"),
        d(UpgradeId.MYRIAD_SWORDS, UpgradeRarity.LEGENDARY, "万剑归宗", "飞剑翻倍、无限穿透，总伤害+120%。"),
        d(UpgradeId.NINE_HEAVENS_THUNDER, UpgradeRarity.LEGENDARY, "九天雷劫", "弹射6次且无衰减，30%概率麻痹1秒。"),
        d(UpgradeId.NETHER_POISON_DOMAIN, UpgradeRarity.LEGENDARY, "玄冥毒域", "范围和毒伤翻倍，敌人移速减半。"),
        d(UpgradeId.METEOR_LAVA, UpgradeRarity.LEGENDARY, "陨星熔岩", "一次发射3颗爆弹，爆炸范围+50%。"),
        d(UpgradeId.ABSOLUTE_ZERO, UpgradeRarity.LEGENDARY, "绝对零度", "无限穿透、冻结1秒并使目标受伤+50%。"),
        d(UpgradeId.VOID_BLACK_HOLE, UpgradeRarity.LEGENDARY, "虚空黑洞", "范围+80%、牵引翻倍、持续时间+2秒。"),
    )
    private val byId = definitions.associateBy { it.id }
    val baseOptions = definitions.filter { it.rarity != UpgradeRarity.LEGENDARY }.map { UpgradeOption(it.id) }

    fun definition(id: UpgradeId): UpgradeDefinition = checkNotNull(byId[id]) { "Missing definition for $id" }

    fun dynamicWeights(completedUpgradeCount: Int): Map<UpgradeRarity, Int> {
        val steps = completedUpgradeCount.coerceAtLeast(0) / 3
        return mapOf(
            UpgradeRarity.COMMON to (70 - 5 * steps).coerceAtLeast(5),
            UpgradeRarity.RARE to 25 + 3 * steps,
            UpgradeRarity.EPIC to 4 + 2 * steps,
            UpgradeRarity.LEGENDARY to 1,
        )
    }

    fun eligibleEvolutionIds(profile: UpgradeProfile): List<UpgradeId> = buildList {
        fun ready(weapon: UpgradeId, weaponLevel: Int, support: UpgradeId, supportLevel: Int) =
            profile.stackOf(weapon) >= weaponLevel && profile.stackOf(support) >= supportLevel
        if (ready(UpgradeId.ORBITING_SWORD, 5, UpgradeId.ATTACK_DAMAGE, 5)) add(UpgradeId.MYRIAD_SWORDS)
        if (ready(UpgradeId.CHAIN_LIGHTNING, 5, UpgradeId.ATTACK_SPEED, 5)) add(UpgradeId.NINE_HEAVENS_THUNDER)
        if (ready(UpgradeId.POISON_AURA, 5, UpgradeId.ATTACK_RANGE, 5)) add(UpgradeId.NETHER_POISON_DOMAIN)
        if (ready(UpgradeId.LAVA_BOMB, 5, UpgradeId.PROJECTILE_COUNT, 3)) add(UpgradeId.METEOR_LAVA)
        if (ready(UpgradeId.PIERCING_ICE_CONE, 5, UpgradeId.ATTACK_SPEED, 5)) add(UpgradeId.ABSOLUTE_ZERO)
        if (ready(UpgradeId.GRAVITY_BLACK_HOLE, 3, UpgradeId.ATTACK_RANGE, 5)) add(UpgradeId.VOID_BLACK_HOLE)
    }.filterNot(profile::owns)

    fun eligibleBaseOptions(profile: UpgradeProfile): List<UpgradeOption> {
        val allWeaponsUnlocked = BASE_WEAPONS.all(profile::owns)
        return baseOptions.filter { option ->
            val definition = option.definition
            (!allWeaponsUnlocked || option.category != UpgradeCategory.WEAPON_SKILL) &&
                profile.stackOf(option.id) < definition.maxLevel &&
                (!definition.unique || !profile.owns(option.id))
        }
    }

    fun draw(
        random: Random,
        profile: UpgradeProfile,
        requestedCount: Int = if (profile.owns(UpgradeId.EXTRA_OPTION)) 4 else 3,
    ): List<UpgradeOption> {
        val candidates = eligibleBaseOptions(profile).toMutableList()
        val result = mutableListOf<UpgradeOption>()
        eligibleEvolutionIds(profile).randomOrNull(random)?.let { result += UpgradeOption(it) }
        while (result.size < requestedCount && candidates.isNotEmpty()) {
            val selected = weightedPick(random, candidates, dynamicWeights(profile.completedUpgradeCount))
            result += selected
            candidates.removeAll { it.id == selected.id }
        }
        if (profile.completedUpgradeCount >= 9 && result.none { it.rarity != UpgradeRarity.COMMON }) {
            val high = eligibleBaseOptions(profile).filter { it.rarity == UpgradeRarity.RARE || it.rarity == UpgradeRarity.EPIC }
                .filterNot { candidate -> result.any { it.id == candidate.id } }
            if (high.isNotEmpty() && result.isNotEmpty()) {
                result[result.lastIndex] = weightedPick(random, high, dynamicWeights(profile.completedUpgradeCount))
            }
        }
        return result.distinctBy { it.id }.take(requestedCount)
    }

    fun drawThree(random: Random, profile: UpgradeProfile, evolutionProvider: EvolutionOptionProvider = EmptyEvolutionOptionProvider): List<UpgradeOption> {
        val normal = draw(random, profile, 3).toMutableList()
        evolutionProvider.eligibleOptions(profile).firstOrNull()?.let { evolution ->
            if (normal.isNotEmpty()) normal[normal.lastIndex] = evolution
        }
        return normal.distinctBy { it.id }
    }

    private fun weightedPick(random: Random, candidates: List<UpgradeOption>, weights: Map<UpgradeRarity, Int>): UpgradeOption {
        val presentRarities = candidates.map { it.rarity }.distinct()
        val total = presentRarities.sumOf { weights[it] ?: 0 }.coerceAtLeast(1)
        var roll = random.nextInt(total)
        val rarity = presentRarities.firstOrNull { candidateRarity ->
            roll -= weights[candidateRarity] ?: 0
            roll < 0
        } ?: presentRarities.last()
        return candidates.filter { it.rarity == rarity }.random(random)
    }

    val BASE_WEAPONS = setOf(
        UpgradeId.ORBITING_SWORD, UpgradeId.ENERGY_PROJECTILE, UpgradeId.POISON_AURA,
        UpgradeId.CHAIN_LIGHTNING, UpgradeId.PIERCING_ICE_CONE, UpgradeId.LAVA_BOMB,
        UpgradeId.GRAVITY_BLACK_HOLE, UpgradeId.SWORD_RAIN, UpgradeId.LIGHTNING_DOMAIN,
    )
    const val CARD_COUNT = 3
}

object UpgradeMath {
    fun increasedDamage(current: Int): Int = ceil(current * ATTACK_DAMAGE_MULTIPLIER).toInt()
    fun fasterAttackInterval(currentSeconds: Float): Float = (currentSeconds / ATTACK_SPEED_MULTIPLIER).coerceAtLeast(MIN_ATTACK_INTERVAL_SECONDS)
    fun increasedAttackRange(currentMeters: Float): Float = (currentMeters * ATTACK_RANGE_MULTIPLIER).coerceAtMost(MAX_ATTACK_RANGE_METERS)
    fun increasedExperienceMultiplier(current: Float): Float = current * EXPERIENCE_GAIN_MULTIPLIER
    fun increasedMovementMultiplier(current: Float): Float = current * MOVEMENT_SPEED_MULTIPLIER
    fun experienceWithMultiplier(baseValue: Int, multiplier: Float): Int = ceil(baseValue * multiplier).toInt().coerceAtLeast(1)
    const val ATTACK_DAMAGE_MULTIPLIER = 1.2f
    const val ATTACK_SPEED_MULTIPLIER = 1.15f
    const val ATTACK_RANGE_MULTIPLIER = 1.15f
    const val EXPERIENCE_GAIN_MULTIPLIER = 1.2f
    const val MOVEMENT_SPEED_MULTIPLIER = 1.15f
    const val PICKUP_RANGE_INCREASE_METERS = 0.3f
    const val MIN_ATTACK_INTERVAL_SECONDS = 0.12f
    const val MAX_ATTACK_RANGE_METERS = 8f
}
