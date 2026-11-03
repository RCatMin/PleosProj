package com.example.pleosproj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.example.pleosproj.ui.guidance.GuidancePreviewHost
import com.example.pleosproj.ui.map.MapSurface

class MainActivity : ComponentActivity() {
    private lateinit var mapSurface: MapSurface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapSurface = MapSurface(this, savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                GuidancePreviewHost(mapSurface)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapSurface.onStart()
    }
    override fun onResume() {
        super.onResume()
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
}
