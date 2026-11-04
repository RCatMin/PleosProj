package com.example.pleosproj

import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pleosproj.ui.demo.DemoApplication
import com.example.pleosproj.ui.map.MapSurface

class MainActivity : ComponentActivity() {
    private lateinit var mapSurface: MapSurface
    private var speedPermissionGranted by mutableStateOf(false)
    private val speedPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        speedPermissionGranted = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSpeedPermission()
        mapSurface = MapSurface(this, savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                DemoApplication(mapSurface, lifecycle, speedPermissionGranted, ::requestSpeedPermission)
            }
        }
        if (!speedPermissionGranted && !permissionPreferences().getBoolean("speed-requested", false)) {
            window.decorView.post { requestSpeedPermission() }
        }
    }

    private fun permissionPreferences() = getSharedPreferences("permission-prompts", MODE_PRIVATE)

    private fun refreshSpeedPermission() {
        speedPermissionGranted = checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSpeedPermission() {
        if (speedPermissionGranted) return
        val requested = permissionPreferences().getBoolean("speed-requested", false)
        if (requested && !shouldShowRequestPermissionRationale(SPEED_PERMISSION)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            permissionPreferences().edit().putBoolean("speed-requested", true).apply()
            speedPermissionRequest.launch(SPEED_PERMISSION)
        }
    }

    override fun onStart() {
        super.onStart()
        mapSurface.onStart()
    }
    override fun onResume() {
        super.onResume()
        refreshSpeedPermission()
        mapSurface.onResume()
    }
    override fun onPause() {
        mapSurface.onPause()
        super.onPause()
    }
    override fun onStop() {
        mapSurface.onStop()
        super.onStop()
    }
    override fun onLowMemory() {
        super.onLowMemory()
        mapSurface.onLowMemory()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        mapSurface.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        mapSurface.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
