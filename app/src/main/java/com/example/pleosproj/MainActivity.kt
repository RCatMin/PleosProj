package com.example.pleosproj

import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pleosproj.ui.demo.DemoApplication
import com.example.pleosproj.ui.map.MapSurface
import com.example.pleosproj.ui.theme.NavigationTheme
import com.example.pleosproj.ui.permissions.*
import com.example.pleosproj.vehicle.VehicleSignalReader

class MainActivity : ComponentActivity() {
    private lateinit var mapSurface: MapSurface
    private var vehicle: VehicleSignalReader? = null
    private var parked by mutableStateOf<Boolean?>(null)
    private var speedKph by mutableStateOf<Double?>(null)
    private var vehicleGeneration = 0
    private var permissions by mutableStateOf(emptyList<AccessStatus>())
    private var permissionRequestRunning = false
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRequestRunning = false
        refreshPermissions()
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) startVehicleReader()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissions()
        mapSurface = MapSurface(this, savedInstanceState)
        setContent {
            NavigationTheme(mapSurface.night) {
                DemoApplication(mapSurface, lifecycle, permissions, ::requestAccess, parked, speedKph)
            }
        }
        window.decorView.post {
            if (!isFinishing && !isDestroyed && !permissionRequestRunning) {
                launchPermissionRequest(initialAccessRequests(readPermissions()))
            }
        }
    }

    private fun permissionPreferences() = getSharedPreferences("permission-prompts", MODE_PRIVATE)

    private fun readPermissions(): List<AccessStatus> = AppAccess.entries.map { access ->
        val permission = access.permissions.first()
        val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val requested = permissionPreferences().getBoolean(access.historyKey, false) ||
            (access == AppAccess.SPEED && permissionPreferences().getBoolean("speed-requested", false))
        AccessStatus(access, granted, requested, shouldShowRequestPermissionRationale(permission),
            approximateLocation = access == AppAccess.LOCATION && !granted &&
                checkSelfPermission(access.permissions.last()) == PackageManager.PERMISSION_GRANTED)
    }

    private fun refreshPermissions() {
        val current = readPermissions()
        // 업데이트 전에 이미 허용된 권한도 기록해, 나중에 설정에서 취소한 선택을 존중한다.
        val previouslyGranted = current.filter { it.granted && !it.requested }
        if (previouslyGranted.isNotEmpty()) permissionPreferences().edit().also { edit ->
            previouslyGranted.forEach { edit.putBoolean(it.access.historyKey, true) }
        }.apply()
        permissions = current.map { if (it.granted) it.copy(requested = true) else it }
    }

    private fun requestAccess(access: AppAccess) {
        if (permissionRequestRunning) return
        val status = readPermissions().first { it.access == access }
        when (status.action) {
            AccessAction.NONE -> refreshPermissions()
            AccessAction.REQUEST -> launchPermissionRequest(listOf(access))
            AccessAction.SETTINGS -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun launchPermissionRequest(accesses: List<AppAccess>) {
        if (accesses.isEmpty() || permissionRequestRunning) return
        // 위치는 정확·대략 권한을 함께 요청해 Android 12 이후의 선택을 받는다.
        permissionPreferences().edit().also { edit -> accesses.forEach { edit.putBoolean(it.historyKey, true) } }.apply()
        permissionRequestRunning = true
        permissionRequest.launch(accesses.flatMap { it.permissions }.distinct().toTypedArray())
    }

    override fun onStart() {
        super.onStart()
        mapSurface.onStart()
        startVehicleReader()
    }

    private fun startVehicleReader() {
        vehicle?.stop()
        parked = null
        speedKph = null
        val generation = ++vehicleGeneration
        val reader = VehicleSignalReader(this) { snapshot ->
            runOnUiThread { if (generation == vehicleGeneration) { parked = snapshot.parked; speedKph = snapshot.speedKph } }
        }
        vehicle = reader
        runCatching { reader.start() }.onFailure { reader.stop(); parked = null; speedKph = null }
    }
    override fun onResume() {
        super.onResume()
        refreshPermissions()
        mapSurface.onResume()
    }
    override fun onPause() {
        mapSurface.onPause()
        super.onPause()
    }
    override fun onStop() {
        ++vehicleGeneration
        vehicle?.stop()
        vehicle = null
        parked = null
        speedKph = null
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
