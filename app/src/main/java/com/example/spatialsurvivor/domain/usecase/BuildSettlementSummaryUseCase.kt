package com.example.spatialsurvivor.domain.usecase

import com.example.spatialsurvivor.domain.model.SettlementSummary
import com.example.spatialsurvivor.domain.model.SettlementRewardLine
import com.example.spatialsurvivor.domain.model.UpgradeHistoryItem
import com.example.spatialsurvivor.progression.SettlementCrystalReward
import kotlin.math.floor

class BuildSettlementSummaryUseCase {
    operator fun invoke(
        survivalSeconds: Float,
        upgradeIds: List<String>,
        waveText: String,
        crystalReward: SettlementCrystalReward,
        totalCrystals: Int,
    ): SettlementSummary {
        val totalSeconds = floor(survivalSeconds.coerceAtLeast(0f)).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val counts = linkedMapOf<String, Int>()
        upgradeIds.forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
        return SettlementSummary(
            survivalTimeText = "%02d:%02d".format(minutes, seconds),
            waveText = waveText,
            upgradeCount = upgradeIds.size,
            earnedCrystals = crystalReward.totalReward,
            totalCrystals = totalCrystals,
            rewardLines =
                listOf(
                    SettlementRewardLine("时间收益", "+${crystalReward.baseTimeReward}"),
                    SettlementRewardLine("击杀收益", "+${crystalReward.baseKillReward}"),
                    SettlementRewardLine("波次奖励", "+${crystalReward.baseWaveReward}"),
                ) +
                    if (crystalReward.victoryBonus > 0) {
                        listOf(SettlementRewardLine("通关奖励", "+${crystalReward.victoryBonus}"))
                    } else {
                        emptyList()
                    },
            upgradeHistory = counts.map { (id, count) -> UpgradeHistoryItem(id, count) },
        )
    }
}
