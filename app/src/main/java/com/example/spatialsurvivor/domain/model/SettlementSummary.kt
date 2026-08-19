package com.example.spatialsurvivor.domain.model

data class UpgradeHistoryItem(
    val id: String,
    val count: Int,
)

data class SettlementRewardLine(
    val label: String,
    val value: String,
)

data class SettlementSummary(
    val survivalTimeText: String,
    val waveText: String = "第1波",
    val upgradeCount: Int = 0,
    val earnedCrystals: Int = 0,
    val totalCrystals: Int = 0,
    val rewardLines: List<SettlementRewardLine> = emptyList(),
    val upgradeHistory: List<UpgradeHistoryItem> = emptyList(),
)
