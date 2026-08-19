package com.example.spatialsurvivor.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.spatialsurvivor.mainApp
import com.example.spatialsurvivor.progression.PermanentProgressionRuntime

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PermanentProgressionRuntime.initialize(this)
        launch(::mainApp)
    }
}
