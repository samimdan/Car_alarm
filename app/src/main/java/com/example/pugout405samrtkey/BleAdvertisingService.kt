package com.example.pugout405samrtkey

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.app.PendingIntent

import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.annotation.RequiresPermission
import java.security.MessageDigest
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleAdvertisingService : Service() {

    companion object {

        const val ACTION_ACK_ALARM = "ACKNOWLEDGE_CAR_ALARM"
        const val ACTION_START_BLE = "START_BLE"
        const val ACTION_STOP_BLE = "STOP_BLE"

        private const val TAG = "PeugeotBLE"

        private const val CHANNEL_ID = "PeugeotSmartKeyChannel"
        private const val NOTIFICATION_ID = 1

        // New channel ID so Android creates the updated alarm channel.
        private const val ALARM_CHANNEL_ID = "CarAlarmChannelV3"
        private const val ALARM_NOTIFICATION_ID = 2

        private val SERVICE_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789001")

        private val CHALLENGE_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789011")

        private val RESPONSE_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789012")

        private val ALARM_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789013")

        // IMPORTANT:
        // Replace with your NEW 64-character hexadecimal key.
        // Use exactly the same key in your ESP32 firmware.
        // This key was present in a previously shared source file.
        // Rotate it before real-world use and set the same new random 32-byte key
        // in the ESP32 firmware. Do not commit or share the key.
        private const val SECRET_HEX =
            "PUT_YOUR_NEW_64_HEX_CHARACTER_KEY_HERE"
        private val AUTH_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789015")

        private val COMMAND_UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789014")

        private val commandLock = Any()

        @Volatile
        private var pendingCommandCode: Byte? = null

        @Volatile
        private var pendingCommandTime: Long = 0L

        fun requestLock() {
            queueCommand(0x01)
        }

        fun requestUnlock() {
            queueCommand(0x02)
        }

        private fun queueCommand(code: Int) {
            synchronized(commandLock) {
                pendingCommandCode = code.toByte()
                pendingCommandTime = android.os.SystemClock.elapsedRealtime()
            }
        }

        private val _isEsp32Connected = MutableStateFlow(false)
        val isEsp32Connected: StateFlow<Boolean> = _isEsp32Connected

        private val _esp32Rssi = MutableStateFlow<Int?>(null)
        val esp32Rssi: StateFlow<Int?> = _esp32Rssi
    }

    private var bluetoothManager: BluetoothManager? = null

    private var gattServer: BluetoothGattServer? = null

    private var advertiserCallback: AdvertiseCallback? = null

    private var challengeCharacteristic: BluetoothGattCharacteristic? = null

    private var responseCharacteristic: BluetoothGattCharacteristic? = null

    private var alarmCharacteristic: BluetoothGattCharacteristic? = null

    private var isAdvertising = false

    private var bluetoothReceiverRegistered = false
    private var authCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    private val challengeByDevice =
        java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    private val responseByDevice =
        java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    private val authenticatedChallengeByDevice =
        java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    // =========================================================
    // BLUETOOTH STATE RECEIVER
    // =========================================================

    private val bluetoothStateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) {
                return
            }

            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                -1
            )

            when (state) {

                BluetoothAdapter.STATE_TURNING_OFF -> {

                    Log.w(TAG, "Bluetooth is turning OFF")

                    stopBle()

                    updateNotification("Bluetooth is turning off")
                }

                BluetoothAdapter.STATE_OFF -> {

                    Log.w(TAG, "Bluetooth is OFF")

                    stopBle()

                    updateNotification("Bluetooth is disabled")
                }

                BluetoothAdapter.STATE_TURNING_ON -> {

                    Log.d(TAG, "Bluetooth is turning ON")
                }

                BluetoothAdapter.STATE_ON -> {

                    Log.d(TAG, "Bluetooth is ON - restarting BLE server")

                    stopBle()

                    if (hasBluetoothPermissions()) {
                        startBle()
                    } else {
                        Log.e(TAG, "Bluetooth permissions missing")
                        updateNotification("Bluetooth permissions missing")
                    }
                }
            }
        }
    }

    // =========================================================
    // SERVICE LIFECYCLE
    // =========================================================

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "BleAdvertisingService created")

        bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        createNotificationChannel()

        registerBluetoothReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d(TAG, "onStartCommand called. Action: ${intent?.action}")

        when (intent?.action) {

            ACTION_STOP_BLE -> {

                Log.d(TAG, "Stop BLE command received")

                stopBle()

                stopForeground(STOP_FOREGROUND_REMOVE)

                stopSelf()
            }

            ACTION_START_BLE, null -> {

                try {

                    startForeground(
                        NOTIFICATION_ID,
                        createNotification("Starting secure BLE server...")
                    )

                    Log.d(TAG, "Foreground service started")

                    startBle()

                } catch (e: Exception) {

                    Log.e(TAG, "Unable to start foreground service", e)

                    stopSelf()
                }
            }

            else -> {
                Log.w(TAG, "Unknown service action: ${intent.action}")
            }
        }

        return START_NOT_STICKY
    }

    // =========================================================
    // REGISTER BLUETOOTH RECEIVER
    // =========================================================

    private fun registerBluetoothReceiver() {

        if (bluetoothReceiverRegistered) {
            return
        }

        try {

            val filter = IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED
            )

            ContextCompat.registerReceiver(
                this,
                bluetoothStateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            bluetoothReceiverRegistered = true

            Log.d(TAG, "Bluetooth state receiver registered")

        } catch (e: Exception) {

            Log.e(TAG, "Failed to register Bluetooth receiver", e)
        }
    }

    // =========================================================
    // PERMISSION CHECKS
    // =========================================================

    private fun hasBluetoothConnectPermission(): Boolean {

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothAdvertisePermission(): Boolean {

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermissions(): Boolean {

        return hasBluetoothConnectPermission() &&
                hasBluetoothAdvertisePermission()
    }

    // =========================================================
    // INITIALIZE BLE
    // =========================================================

    @SuppressLint("MissingPermission")
    private fun startBle() {

        Log.d(TAG, "startBle entered")

        if (!hasBluetoothPermissions()) {

            Log.e(TAG, "Bluetooth permissions are missing")

            updateNotification("Bluetooth permissions are missing")

            return
        }

        try {

            val manager = bluetoothManager
                ?: throw IllegalStateException("BluetoothManager unavailable")

            val adapter = manager.adapter
                ?: throw IllegalStateException("Bluetooth adapter unavailable")

            if (!adapter.isEnabled) {

                Log.w(TAG, "Bluetooth is disabled")

                updateNotification("Bluetooth is disabled")

                return
            }

            if (gattServer != null) {

                Log.w(TAG, "GATT server already initialized")

                return
            }

            if (adapter.bluetoothLeAdvertiser == null) {

                Log.e(TAG, "BLE advertiser unavailable")

                updateNotification("BLE advertising unavailable")

                return
            }

            val server = manager.openGattServer(
                this,
                gattCallback
            ) ?: throw IllegalStateException(
                "openGattServer returned null"
            )

            gattServer = server

            Log.d(TAG, "GATT server opened successfully")

            val service = BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            challengeCharacteristic =
                BluetoothGattCharacteristic(
                    CHALLENGE_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )

            responseCharacteristic =
                BluetoothGattCharacteristic(
                    RESPONSE_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )

            alarmCharacteristic =
                BluetoothGattCharacteristic(
                    ALARM_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )

            authCharacteristic =
                BluetoothGattCharacteristic(
                    AUTH_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )

            commandCharacteristic =
                BluetoothGattCharacteristic(
                    COMMAND_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )

            val challengeAdded =
                service.addCharacteristic(challengeCharacteristic!!)

            val responseAdded =
                service.addCharacteristic(responseCharacteristic!!)

            val alarmAdded =
                service.addCharacteristic(alarmCharacteristic!!)
            val authAdded =
                service.addCharacteristic(authCharacteristic!!)

            val commandAdded =
                service.addCharacteristic(commandCharacteristic!!)

            Log.d(TAG, "Challenge added: $challengeAdded")
            Log.d(TAG, "Response added: $responseAdded")
            Log.d(TAG, "Alarm added: $alarmAdded")

            Log.d(
                TAG,
                "Characteristic count: ${service.characteristics.size}"
            )

            if (
                !challengeAdded ||
                !responseAdded ||
                !alarmAdded ||
                !authAdded ||
                !commandAdded ||
                service.characteristics.size != 5
            ) {

                Log.e(TAG, "Characteristic registration failed")

                updateNotification("Characteristic registration failed")

                stopBle()

                return
            }

            service.characteristics.forEach {

                Log.d(TAG, "Characteristic UUID: ${it.uuid}")
            }

            updateNotification("Registering GATT service...")

            val added = server.addService(service)

            Log.d(TAG, "addService returned: $added")

            if (!added) {

                Log.e(TAG, "addService returned false")

                updateNotification("GATT service registration failed")

                stopBle()
            }

        } catch (e: SecurityException) {

            Log.e(TAG, "SecurityException in startBle", e)

            updateNotification("Bluetooth permission error")

        } catch (e: Exception) {

            Log.e(TAG, "Unexpected error in startBle", e)

            updateNotification("BLE initialization error")
        }
    }

    // =========================================================
    // GATT SERVER CALLBACK
    // =========================================================

    private val gattCallback =
        object : BluetoothGattServerCallback() {

            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {

                Log.d(
                    TAG,
                    "Connection state changed. Status: $status, State: $newState"
                )

                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "BLE client connected")
                        // Update the connection state
                        _isEsp32Connected.value = true
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        challengeByDevice.remove(device.address)
                        responseByDevice.remove(device.address)
                        authenticatedChallengeByDevice.remove(device.address)
                        Log.d(TAG, "BLE client disconnected")
                        // Update the connection state
                        _isEsp32Connected.value = false
                    }
                }
            }

            // -------------------------------------------------
            // SERVICE REGISTRATION
            // -------------------------------------------------

            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService
            ) {

                Log.d(TAG, "Service UUID: ${service.uuid}")
                Log.d(TAG, "Registration status: $status")

                Log.d(
                    TAG,
                    "Characteristic count: ${service.characteristics.size}"
                )

                if (
                    status == BluetoothGatt.GATT_SUCCESS &&
                    service.uuid == SERVICE_UUID &&
                    service.characteristics.size == 5
                ) {

                    Log.d(TAG, "GATT service registered successfully")

                    updateNotification(
                        "GATT service registered successfully"
                    )

                    startAdvertising()

                } else {

                    Log.e(TAG, "GATT service registration failed: $status")

                    updateNotification(
                        "GATT service registration failed: $status"
                    )

                    stopBle()
                }
            }

            // -------------------------------------------------
            // CHARACTERISTIC WRITE
            // -------------------------------------------------


            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {

                Log.d(
                    TAG,
                    "Characteristic write request: ${characteristic.uuid}"
                )

                if (!hasBluetoothConnectPermission()) {

                    Log.e(TAG, "Bluetooth connect permission missing")

                    return
                }

                // =============================================
                // ALARM COMMAND
                // =============================================

                if (characteristic.uuid == ALARM_UUID) {

                    if (
                        preparedWrite ||
                        offset != 0 ||
                        value.size != 17
                    ) {

                        Log.e(TAG, "Invalid alarm packet")

                        if (responseNeeded) {
                            sendGattResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                0,
                                null
                            )
                        }

                        return
                    }

                    try {

                        val command = value.copyOfRange(0, 5)

                        val receivedSignature =
                            value.copyOfRange(5, 17)

                        val commandText = String(
                            command,
                            Charsets.UTF_8
                        )

                        if (commandText != "ALARM") {
                            throw IllegalArgumentException("Unknown command")
                        }

                        val expectedSignature =
                            calculateHmac(command).copyOfRange(0, 12)

                        val validSignature =
                            MessageDigest.isEqual(
                                expectedSignature,
                                receivedSignature
                            )

                        if (!validSignature) {
                            throw SecurityException("Invalid alarm signature")
                        }

                        if (responseNeeded) {

                            sendGattResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                null
                            )
                        }

                        Log.w(
                            TAG,
                            "Authenticated alarm command received"
                        )

                        showAlarmNotification(
                            "هشدار! احتمال دستکاری یا ورود غیرمجاز به خودرو وجود دارد."
                        )

                    } catch (e: Exception) {

                        Log.e(TAG, "Alarm command rejected", e)

                        if (responseNeeded) {

                            sendGattResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                0,
                                null
                            )
                        }
                    }

                    return
                }
                // =============================================
                // ESP32 AUTHENTICATION PROOF
                // =============================================

                if (characteristic.uuid == AUTH_UUID) {

                    val address = device.address
                    val challenge = challengeByDevice[address]

                    try {

                        if (
                            preparedWrite ||
                            offset != 0 ||
                            value.size != 32 ||
                            challenge == null
                        ) {
                            throw SecurityException("Invalid authentication proof")
                        }

                        val authMessage =
                            challenge + "AUTH".toByteArray(Charsets.US_ASCII)

                        val expected = calculateHmac(authMessage)

                        if (!MessageDigest.isEqual(expected, value)) {
                            throw SecurityException("Authentication proof mismatch")
                        }

                        authenticatedChallengeByDevice[address] =
                            challenge.copyOf()

                        challengeByDevice.remove(address)

                        if (responseNeeded) {
                            sendGattResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                null
                            )
                        }

                        Log.i(TAG, "ESP32 authenticated successfully")

                        return

                    } catch (e: Exception) {

                        Log.e(TAG, "ESP32 authentication rejected", e)

                        challengeByDevice.remove(address)
                        authenticatedChallengeByDevice.remove(address)

                        if (responseNeeded) {
                            sendGattResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                0,
                                null
                            )
                        }

                        return
                    }
                }

                // =============================================
                // CHALLENGE COMMAND
                // =============================================

                if (
                    characteristic.uuid != CHALLENGE_UUID ||
                    preparedWrite ||
                    offset != 0 ||
                    value.size != 16
                ) {

                    Log.e(TAG, "Invalid challenge write request")

                    if (responseNeeded) {

                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    }

                    return
                }

                try {

                    Log.d(TAG, "Challenge received: ${value.size} bytes")

                    val response = calculateHmac(value)
                    challengeByDevice[device.address] = value.copyOf()
                    responseByDevice[device.address] = response.copyOf()
                    authenticatedChallengeByDevice.remove(device.address)



                    if (responseNeeded) {

                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null
                        )
                    }

                    Log.d(TAG, "Challenge processed successfully")

                    Log.d(
                        TAG,
                        "HMAC response generated: ${response.size} bytes"
                    )

                    updateNotification("Challenge processed successfully")

                } catch (e: Exception) {

                    challengeByDevice.remove(device.address)
                    responseByDevice.remove(device.address)
                    authenticatedChallengeByDevice.remove(device.address)
                    Log.e(TAG, "HMAC calculation failed", e)

                    if (responseNeeded) {

                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    }
                }
            }

            // -------------------------------------------------
            // RESPONSE READ REQUEST
            // -------------------------------------------------

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {

                Log.d(
                    TAG,
                    "Characteristic read request: ${characteristic.uuid}"
                )

                if (!hasBluetoothConnectPermission()) {

                    Log.e(TAG, "Bluetooth connect permission missing")

                    return
                }
// =============================================
// COMMAND READ REQUEST
// =============================================

                if (characteristic.uuid == COMMAND_UUID) {

                    if (offset != 0) {
                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            offset,
                            null
                        )
                        return
                    }

                    val address = device.address

                    val challenge = authenticatedChallengeByDevice[address]

                    if (challenge == null) {
                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                        return
                    }

                    val code: Byte? = synchronized(commandLock) {

                        val current = pendingCommandCode

                        if (current == null) {
                            null
                        } else if (
                            android.os.SystemClock.elapsedRealtime() -
                            pendingCommandTime > 15_000L
                        ) {
                            pendingCommandCode = null
                            null
                        } else {
                            pendingCommandCode = null
                            current
                        }
                    }


                    if (code == null) {

                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            byteArrayOf(0)
                        )

                        return
                    }

                    try {

                        val signedData = challenge + byteArrayOf(code)

                        val signature =
                            calculateHmac(signedData).copyOfRange(0, 12)

                        val packet =
                            byteArrayOf(code) + signature

                        // One command per authenticated session.
                        authenticatedChallengeByDevice.remove(address)

                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            packet
                        )

                        Log.i(TAG, "Authenticated command delivered: $code")

                    } catch (e: Exception) {

                        Log.e(TAG, "Command generation failed", e)

                        sendGattResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    }

                    return
                }
                if (
                    characteristic.uuid != RESPONSE_UUID ||
                    offset != 0
                ) {

                    Log.e(TAG, "Invalid response read request")

                    sendGattResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null
                    )

                    return
                }

                val response = responseByDevice[device.address]

                if (response == null) {

                    Log.e(TAG, "No HMAC response available")

                    sendGattResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )

                    return
                }

                Log.d(
                    TAG,
                    "Sending HMAC response: ${response.size} bytes"
                )

                sendGattResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    response
                )
            }
        }

    // =========================================================
    // ALARM NOTIFICATION CHANNEL
    // =========================================================


    private fun createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(
                NotificationManager::class.java
            )

            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Peugeot 405 Car Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peugeot 405 security alarm"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                // AlarmActivity handles sound and vibration.
                setSound(null, null)
                enableVibration(false)
            }

            manager.createNotificationChannel(channel)

            Log.d(TAG, "Alarm channel importance: ${channel.importance}")
        }
    }

    // =========================================================
    // SHOW ALARM NOTIFICATION
    // =========================================================


    @RequiresPermission(Manifest.permission.USE_FULL_SCREEN_INTENT)
    private fun showAlarmNotification(message: String) {
        Log.w(TAG, "🚨 AUTHENTICATED CAR ALARM RECEIVED")

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "POST_NOTIFICATIONS permission missing")
            return
        }

        createAlarmNotificationChannel()

        val alarmIntent = Intent(
            this,
            AlarmActivity::class.java
        ).apply {
            action = "com.example.pugout405samrtkey.OPEN_ALARM"

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra("alarm_message", message)
        }

        val alarmPendingIntent = PendingIntent.getActivity(
            this,
            2026,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            this,
            ALARM_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 PEUGEOT 405 SECURITY ALERT")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(message)
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(alarmPendingIntent)
            .setFullScreenIntent(alarmPendingIntent, true)
            .build()

        try {
            val notificationManager =
                getSystemService(NotificationManager::class.java)

            if (Build.VERSION.SDK_INT >= 34) {
                Log.i(
                    TAG,
                    "Full-screen access: ${notificationManager.canUseFullScreenIntent()}"
                )
            }

            NotificationManagerCompat.from(this).notify(
                ALARM_NOTIFICATION_ID,
                notification
            )

            Log.w(TAG, "🚨 Full-screen alarm notification submitted")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to display alarm notification", e)
        }

    }
    // =========================================================
    // SEND GATT RESPONSE
    // =========================================================

    @SuppressLint("MissingPermission")
    private fun sendGattResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?
    ) {

        if (!hasBluetoothConnectPermission()) {

            Log.e(TAG, "Cannot send GATT response: permission missing")

            return
        }

        try {

            val server = gattServer

            if (server == null) {

                Log.e(TAG, "GATT server is null")

                return
            }

            val result = server.sendResponse(
                device,
                requestId,
                status,
                offset,
                value
            )

            Log.d(TAG, "sendResponse result: $result")

        } catch (e: Exception) {

            Log.e(TAG, "Failed to send GATT response", e)
        }
    }

    // =========================================================
    // HMAC-SHA256
    // =========================================================

    private fun calculateHmac(data: ByteArray): ByteArray {

        require(SECRET_HEX.length == 64) {
            "13254ca0d0f795a0e2a88b3f94f36f84a6800f2d0c0620a1564e0e87f04b486c"
        }

        require(
            SECRET_HEX.all {
                it.digitToIntOrNull(16) != null
            }
        ) {
            "Secret key contains invalid hexadecimal characters"
        }

        val keyBytes = SECRET_HEX
            .chunked(2)
            .map {
                it.toInt(16).toByte()
            }
            .toByteArray()

        val mac = Mac.getInstance("HmacSHA256")

        mac.init(
            SecretKeySpec(keyBytes, "HmacSHA256")
        )

        return mac.doFinal(data)
    }

    // =========================================================
    // BLE ADVERTISING
    // =========================================================

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {

        Log.d(TAG, "startAdvertising entered")

        if (!hasBluetoothPermissions()) {

            Log.e(TAG, "Advertising permissions are missing")

            updateNotification("Bluetooth permissions are missing")

            return
        }

        try {

            val manager = bluetoothManager
                ?: throw IllegalStateException("BluetoothManager unavailable")

            val adapter = manager.adapter

            if (!adapter.isEnabled) {

                Log.w(TAG, "Bluetooth is disabled")

                return
            }

            val advertiser = adapter.bluetoothLeAdvertiser
                ?: throw IllegalStateException("BLE advertiser unavailable")

            if (isAdvertising) {

                Log.w(TAG, "Advertising already active")

                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(
                    AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                )
                .setTxPowerLevel(
                    AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
                )
                .setConnectable(true)
                .setTimeout(0)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build()

            advertiserCallback = object : AdvertiseCallback() {

                override fun onStartSuccess(
                    settingsInEffect: AdvertiseSettings
                ) {

                    isAdvertising = true

                    Log.d(TAG, "Advertising started")

                    updateNotification("Secure BLE server is active")
                }

                override fun onStartFailure(errorCode: Int) {

                    isAdvertising = false

                    Log.e(TAG, "Advertising failed: $errorCode")

                    updateNotification(
                        "BLE advertising failed: $errorCode"
                    )
                }
            }

            advertiser.startAdvertising(
                settings,
                data,
                advertiserCallback
            )

            Log.d(TAG, "Advertising request submitted")

        } catch (e: Exception) {

            Log.e(TAG, "Failed to start advertising", e)

            updateNotification("BLE advertising initialization error")
        }
    }

    // =========================================================
    // STOP BLE
    // =========================================================

    @SuppressLint("MissingPermission")
    private fun stopBle() {

        Log.d(TAG, "stopBle entered")

        try {

            val callback = advertiserCallback

            if (callback != null && hasBluetoothPermissions()) {

                bluetoothManager
                    ?.adapter
                    ?.bluetoothLeAdvertiser
                    ?.stopAdvertising(callback)

                Log.d(TAG, "Advertising stop requested")
            }

        } catch (e: Exception) {

            Log.e(TAG, "Error stopping advertising", e)
        }

        try {

            gattServer?.close()

            Log.d(TAG, "GATT server closed")

        } catch (e: Exception) {

            Log.e(TAG, "Error closing GATT server", e)
        }

        advertiserCallback = null

        gattServer = null

        challengeCharacteristic = null

        responseCharacteristic = null

        alarmCharacteristic = null

        isAdvertising = false
        authCharacteristic = null
        commandCharacteristic = null

        challengeByDevice.clear()
        responseByDevice.clear()
        authenticatedChallengeByDevice.clear()

        synchronized(commandLock) {
            pendingCommandCode = null
        }
        Log.d(TAG, "BLE resources released")
    }

    // =========================================================
    // FOREGROUND NOTIFICATION CHANNEL
    // =========================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Peugeot Smart Key",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description = "Background Bluetooth service"

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)

            Log.d(TAG, "Foreground notification channel created")
        }
    }

    // =========================================================
    // FOREGROUND NOTIFICATION
    // =========================================================

    private fun createNotification(message: String): Notification {

        val builder = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {

            Notification.Builder(this, CHANNEL_ID)

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Peugeot Smart Key")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {

        Log.d(TAG, "Notification update: $message")

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            createNotification(message)
        )
    }

    // =========================================================
    // SERVICE DESTROYED
    // =========================================================

    override fun onDestroy() {

        Log.d(TAG, "BleAdvertisingService destroyed")

        if (bluetoothReceiverRegistered) {

            try {

                unregisterReceiver(bluetoothStateReceiver)

                bluetoothReceiverRegistered = false

                Log.d(TAG, "Bluetooth receiver unregistered")

            } catch (e: Exception) {

                Log.e(TAG, "Error unregistering Bluetooth receiver", e)
            }
        }

        stopBle()

        super.onDestroy()
    }
}
