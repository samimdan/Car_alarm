
package com.example.pugout405samrtkey

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import com.example.pugout405samrtkey.ui.theme.CarAlarmScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PeugeotBLE"
    }

    private var serviceStartRequested = false

    // ---------------------------------------------------------
    // PERMISSION LAUNCHER
    // ---------------------------------------------------------

    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            if (hasBluetoothPermissions()) {

                Log.i(TAG, "Bluetooth permissions granted")

                val notificationGranted =
                    Build.VERSION.SDK_INT < 33 ||
                            permissions[Manifest.permission.POST_NOTIFICATIONS] == true ||
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED

                if (!notificationGranted) {
                    Log.w(TAG, "Notification permission denied")
                }

                startBleService()

            } else {
                Log.e(TAG, "Bluetooth permissions denied")
            }
        }

    // ---------------------------------------------------------
    // ON CREATE
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBluetoothPermissions()
        requestFullScreenAccess()

        setContent {
            CarAlarmScreen(
                Bluethooth = false,

                onLockClick = {
                    Log.i(TAG, "Lock button clicked")
                    startBleService()
                },

                onTrunkClick = {
                    Log.i(TAG, "Trunk button clicked")
                },

                onFindClick = {
                    Log.i(TAG, "Find button clicked")
                },

                onAntiTheftClick = {
                    Log.i(TAG, "Anti-theft button clicked")
                },

                RSSI = "--"
            )
        }
    }

    // ---------------------------------------------------------
    // CHECK BLUETOOTH PERMISSIONS
    // ---------------------------------------------------------

    private fun hasBluetoothPermissions(): Boolean {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val required = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )

            required.all {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }

        } else {

            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------------------------------------------------------
    // REQUEST BLUETOOTH PERMISSIONS
    // ---------------------------------------------------------

    private fun requestBluetoothPermissions() {

        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)

        } else {

            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        Log.i(TAG, "Missing permissions: $missingPermissions")

        if (missingPermissions.isEmpty()) {

            Log.i(TAG, "All Bluetooth permissions granted")
            startBleService()

        } else {

            bluetoothPermissionLauncher.launch(
                missingPermissions.toTypedArray()
            )
        }
    }

    // ---------------------------------------------------------
    // START BLE SERVICE
    // ---------------------------------------------------------

    private fun startBleService() {

        if (serviceStartRequested) {
            Log.i(TAG, "BLE service start already requested")
            return
        }

        Log.i(TAG, "Starting BLE service")

        val intent = Intent(
            this,
            BleAdvertisingService::class.java
        ).apply {
            action = BleAdvertisingService.ACTION_START_BLE
        }

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

            serviceStartRequested = true

            Log.i(TAG, "BLE service start requested successfully")

        } catch (e: Exception) {

            serviceStartRequested = false

            Log.e(TAG, "Failed to start BLE service", e)
        }
    }

    // ---------------------------------------------------------
    // STOP BLE SERVICE
    // ---------------------------------------------------------

    private fun stopBleService() {

        Log.i(TAG, "Stopping BLE service")

        val intent = Intent(
            this,
            BleAdvertisingService::class.java
        ).apply {
            action = BleAdvertisingService.ACTION_STOP_BLE
        }

        try {

            startService(intent)
            serviceStartRequested = false

        } catch (e: Exception) {

            Log.e(TAG, "Failed to stop BLE service", e)
        }
    }

    // ---------------------------------------------------------
    // FULL SCREEN PERMISSION
    // ---------------------------------------------------------

    private fun requestFullScreenAccess() {

        if (Build.VERSION.SDK_INT >= 34) {

            val notificationManager =
                getSystemService(NotificationManager::class.java)

            if (!notificationManager.canUseFullScreenIntent()) {

                try {

                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )

                    startActivity(intent)

                } catch (e: Exception) {

                    Log.e(TAG, "Full screen permission error", e)
                }
            }
        }
    }
}
