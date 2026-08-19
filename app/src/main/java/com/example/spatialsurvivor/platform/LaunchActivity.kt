package com.example.spatialsurvivor.platform

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity

class LaunchActivity : SpatialLaunchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestSpatialRuntimePermissions()
    }

    private fun requestSpatialRuntimePermissions() {
        val missingPermissions =
            REQUIRED_RUNTIME_PERMISSIONS.filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                SPATIAL_PERMISSION_REQUEST_CODE,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SPATIAL_PERMISSION_REQUEST_CODE) {
            permissions.forEachIndexed { index, permission ->
                val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
                Log.i(TAG, "$permission granted=$granted")
            }
        }
    }

    private companion object {
        const val TAG = "SpatialPermissions"
        const val SPATIAL_PERMISSION_REQUEST_CODE = 1001
        const val SPATIAL_DATA_PERMISSION = "com.picovr.permission.SPATIAL_DATA"
        const val EYE_TRACKING_PERMISSION = "com.picovr.permission.EYE_TRACKING"

        val REQUIRED_RUNTIME_PERMISSIONS =
            arrayOf(
                SPATIAL_DATA_PERMISSION,
                EYE_TRACKING_PERMISSION,
            )
    }
}
