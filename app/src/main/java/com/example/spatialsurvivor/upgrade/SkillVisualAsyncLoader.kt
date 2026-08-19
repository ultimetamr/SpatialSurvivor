package com.example.spatialsurvivor.upgrade

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Staggered off-main-thread GLB hydrate for skill placeholders. */
object SkillVisualAsyncLoader {
    data class Job(val parent: Entity, val kind: SkillVisualId, val assign: (Entity) -> Unit)

    suspend fun hydrate(jobs: List<Job>) {
        if (jobs.isEmpty()) return
        Log.i(TAG, "Starting async skill GLB hydration for ${jobs.size} slots")
        var loaded = 0
        var missing = 0
        val missingKinds = linkedSetOf<SkillVisualId>()
        jobs.forEach { job ->
            val glb =
                withContext(Dispatchers.IO) {
                    SkillVisualRules.loadGlbEntity(job.kind)
                }
            if (glb == null) {
                missing += 1
                missingKinds += job.kind
                return@forEach
            }
            withContext(Dispatchers.Main.immediate) {
                val wrapped = SkillVisualRules.wrapLoadedGlb(glb, job.kind)
                job.assign(wrapped)
                loaded += 1
                Log.i(
                    TAG,
                    "Hydrated skill visual ${job.kind.name} scale=${SkillVisualRules.visualScale(job.kind)}",
                )
            }
            delay(FRAME_DELAY_MILLIS)
        }
        if (missingKinds.isNotEmpty()) {
            Log.w(
                TAG,
                "Skill GLB assets missing or unloadable (${missing} slots): " +
                    missingKinds.joinToString { kind ->
                        "${kind.name}->${SkillVisualRules.assetUri(kind)}"
                    } +
                    ". Keep placeholders until files exist under app/src/main/assets/models/",
            )
        }
        Log.i(TAG, "Async skill GLB hydration complete: loaded=$loaded missing=$missing")
    }

    private const val TAG = "SkillVisualAsyncLoader"
    private const val FRAME_DELAY_MILLIS = 24L
}

/** Swaps a parent's visual child for a newly loaded GLB on the main thread. */
fun Entity.replaceSkillVisualChild(
    previous: Entity?,
    replacement: Entity,
): Entity {
    previous?.destroy()
    addChild(replacement)
    return replacement
}
