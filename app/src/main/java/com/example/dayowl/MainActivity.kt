package com.example.dayowl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.dayowl.service.HostService
import com.example.dayowl.ui.NavGraph
import com.example.dayowl.ui.theme.DayOwlTheme

class MainActivity : ComponentActivity() {

    private val mediaProjectionManager by lazy {
        getSystemService(MediaProjectionManager::class.java)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchProjection()
        } else {
            Log.e("MainActivity", "Audio permission denied")
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Log.d("MainActivity", "Projection result code: ${result.resultCode}")
        if (result.resultCode == RESULT_OK && result.data != null) {
            // Log.d("MainActivity", "Projection granted, starting HostService")
            val intent = Intent(this, HostService::class.java).apply {
                action = HostService.ACTION_START_BROADCAST
                putExtra(HostService.EXTRA_RESULT_DATA, result.data)
            }
            try {
                startForegroundService(intent)
                // Log.d("MainActivity", "startForegroundService called successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to startForegroundService", e)
            }
        } else {
            // Log.e("MainActivity", "Projection result NOT OK or data null")
        }
    }

    private fun launchProjection() {
        // Log.d("MainActivity", "Requesting projection")
        projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DayOwlTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    onRequestProjection = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                            == PackageManager.PERMISSION_GRANTED) {
                            launchProjection()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }
}
