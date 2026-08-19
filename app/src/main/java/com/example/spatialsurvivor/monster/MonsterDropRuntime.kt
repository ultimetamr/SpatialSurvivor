package com.example.spatialsurvivor.monster

import android.util.Log

data class ExperienceDropRequest(
    val monsterType: MonsterType,
    val experienceValue: Int,
    val worldPosition: NavigationPoint,
)

fun interface MonsterDeathDropSink {
    fun requestExperienceDrop(request: ExperienceDropRequest)
}

/** Integration seam for the future exp module. */
object MonsterDropRuntime {
    private val loggingSink =
        MonsterDeathDropSink { request ->
            Log.i(
                TAG,
                "EXP drop requested: type=${request.monsterType}, value=${request.experienceValue}, " +
                    "position=${request.worldPosition}",
            )
        }

    @Volatile
    private var sink: MonsterDeathDropSink = loggingSink

    fun bind(dropSink: MonsterDeathDropSink) {
        sink = dropSink
    }

    /** Clears pending ownership only when no gameplay sink is registered. */
    fun reset() {
        if (sink === loggingSink) return
        // Keep the bound ExperienceGameplay sink across monster pool resets.
    }

    fun unbind() {
        sink = loggingSink
    }

    fun request(request: ExperienceDropRequest) {
        if (request.experienceValue <= 0) return
        sink.requestExperienceDrop(request)
    }

    private const val TAG = "MonsterDropRuntime"
}
