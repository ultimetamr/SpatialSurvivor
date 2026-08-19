package com.example.spatialsurvivor.monster

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Loads GLB visuals off the main thread after cold start placeholders are shown. */
object MonsterVisualAsyncLoader {
    suspend fun hydratePool(monsters: List<Entity>) {
        if (monsters.isEmpty()) return
        Log.i(TAG, "Starting async GLB hydration for ${monsters.size} pool slots")
        monsters.forEach { poolEntity ->
            val monster = poolEntity.components[MonsterComponent::class.java] ?: return@forEach
            val type = monster.monsterType
            if (MonsterVisualRules.assetUri(type) == null) return@forEach

            val glb =
                withContext(Dispatchers.IO) {
                    MonsterVisualRules.loadGlbEntity(type)
                } ?: return@forEach

            withContext(Dispatchers.Main.immediate) {
                MonsterVisualRules.applyVisualLocalPose(glb, type)
                val previous = monster.visualChild
                previous?.destroy()
                poolEntity.addChild(glb)
                monster.visualChild = glb
                poolEntity.components[TransformComponent::class.java]
                    ?.let(MonsterVisualRules::applyGameplayRootPose)
                val offset = MonsterVisualRules.visualLocalOffset(type)
                Log.i(
                    TAG,
                    "Hydrated ${type.name}: scale=${MonsterVisualRules.visualScale(type)}, " +
                        "localY=${offset.y}, anchor=${type.anchorOffsetYMeters}",
                )
            }
            delay(HYDRATE_FRAME_DELAY_MILLIS)
        }
        Log.i(TAG, "Async GLB hydration complete")
    }

    private const val TAG = "MonsterVisualAsyncLoader"
    private const val HYDRATE_FRAME_DELAY_MILLIS = 32L
}
