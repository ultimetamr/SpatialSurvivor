package com.example.spatialsurvivor.domain.model

import com.example.spatialsurvivor.upgrade.UpgradeId

/** Pure snapshot used by weighted draws; it deliberately has no ECS dependency. */
data class UpgradeLoadout(
    val stacks: Map<UpgradeId, Int> = emptyMap(),
    val ownedUnique: Set<UpgradeId> = emptySet(),
    val completedUpgradeCount: Int = 0,
) {
    fun stackOf(id: UpgradeId): Int = stacks[id] ?: 0
    fun owns(id: UpgradeId): Boolean = stackOf(id) > 0 || id in ownedUnique
}
