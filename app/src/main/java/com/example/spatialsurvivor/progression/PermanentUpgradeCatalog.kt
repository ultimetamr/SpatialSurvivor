package com.example.spatialsurvivor.progression

enum class PermanentUpgradeCategory {
    COMBAT,
    ECONOMY,
}

enum class PermanentUpgradeType {
    STARTING_MAX_HEALTH,
    STARTING_ATTACK_DAMAGE,
    STARTING_ATTACK_SPEED,
    STARTING_ATTACK_RANGE,
    STARTING_PICKUP_RANGE,
    STARTING_EXPERIENCE_GAIN,
    CRYSTAL_MULTIPLIER,
    WAVE_REWARD,
}

data class PermanentUpgradeDefinition(
    val type: PermanentUpgradeType,
    val category: PermanentUpgradeCategory,
    val title: String,
    val description: String,
    val maxLevel: Int,
    val baseCost: Int,
    val costStep: Int,
)

object PermanentUpgradeCatalog {
    val all: List<PermanentUpgradeDefinition> =
        listOf(
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.STARTING_MAX_HEALTH,
                category = PermanentUpgradeCategory.COMBAT,
                title = "起始生命",
                description = "开局最大生命值提高",
                maxLevel = 10,
                baseCost = 40,
                costStep = 25,
            ),
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.STARTING_ATTACK_DAMAGE,
                category = PermanentUpgradeCategory.COMBAT,
                title = "初始攻击",
                description = "开局能量弹伤害提高",
                maxLevel = 10,
                baseCost = 45,
                costStep = 30,
            ),
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.STARTING_ATTACK_SPEED,
                category = PermanentUpgradeCategory.COMBAT,
                title = "初始攻速",
                description = "开局自动攻击间隔缩短",
                maxLevel = 8,
                baseCost = 50,
                costStep = 32,
            ),
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.STARTING_ATTACK_RANGE,
                category = PermanentUpgradeCategory.COMBAT,
                title = "初始范围",
                description = "开局攻击锁定与投射距离提高",
                maxLevel = 8,
                baseCost = 40,
                costStep = 28,
            ),
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.STARTING_PICKUP_RANGE,
                category = PermanentUpgradeCategory.COMBAT,
                title = "拾取范围",
                description = "开局经验晶石吸附距离提高",
                maxLevel = 8,
                baseCost = 35,
                costStep = 25,
            ),
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.STARTING_EXPERIENCE_GAIN,
                category = PermanentUpgradeCategory.COMBAT,
                title = "经验倍率",
                description = "开局经验获取倍率提高",
                maxLevel = 8,
                baseCost = 45,
                costStep = 30,
            ),
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.CRYSTAL_MULTIPLIER,
                category = PermanentUpgradeCategory.ECONOMY,
                title = "晶石倍率",
                description = "整局结算晶核总收益提高",
                maxLevel = 10,
                baseCost = 50,
                costStep = 35,
            ),
            PermanentUpgradeDefinition(
                type = PermanentUpgradeType.WAVE_REWARD,
                category = PermanentUpgradeCategory.ECONOMY,
                title = "波次奖励",
                description = "波次奖励收益提高",
                maxLevel = 10,
                baseCost = 60,
                costStep = 40,
            ),
        )

    fun definition(type: PermanentUpgradeType): PermanentUpgradeDefinition =
        all.first { it.type == type }

    fun byCategory(category: PermanentUpgradeCategory): List<PermanentUpgradeDefinition> =
        all.filter { it.category == category }
}
