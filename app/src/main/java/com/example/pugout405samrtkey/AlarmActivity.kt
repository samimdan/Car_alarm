
package com.example.pugout405samrtkey

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log

class AlarmActivity : Activity() {

    companion object {
        private const val TAG = "PeugeotAlarmActivity"
    }

    private val handler = Handler(Looper.getMainLooper())

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var alarmActive = false

    private val beepRunnable = object : Runnable {
        override fun run() {
            if (!alarmActive) return

            try {
                toneGenerator?.startTone(
                    ToneGenerator.TONE_PROP_BEEP2,
                    400
                )
            } catch (e: Exception) {
                Log.e(TAG, "Beep error", e)
            }

            handler.postDelayed(this, 1300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "AlarmActivity created")

        configureLockScreen()
        createAlarmScreen()
        startAlarmEffects()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        Log.d(TAG, "AlarmActivity received new intent")

        if (!alarmActive) {
            startAlarmEffects()
        }
    }

    private fun configureLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = Color.BLACK
            window.statusBarColor = Color.rgb(100, 0, 0)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    private fun createAlarmScreen() {
        val messageText = intent.getStringExtra("alarm_message")
            ?: "هشدار امنیتی خودرو!\nاحتمال ورود غیرمجاز وجود دارد."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 40)
            setBackgroundColor(Color.rgb(45, 0, 0))
        }

        val title = TextView(this).apply {
            text = "🚨 PEUGEOT 405"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = messageText
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 50, 10, 40)
        }

        val instruction = TextView(this).apply {
            text = "برای قطع صدا و ویبره، دکمه زیر را فشار دهید."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 30)
        }

        val stopButton = Button(this).apply {
            text = "تأیید و قطع هشدار"
            textSize = 19f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 130, 65))
            setOnClickListener {
                stopAlarm()
            }
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            message,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            instruction,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            stopButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                68
            )
        )

        setContentView(root)
    }

    private fun startAlarmEffects() {
        if (alarmActive) return

        alarmActive = true

        Log.d(TAG, "Starting alarm sound and vibration")

        try {
            toneGenerator = ToneGenerator(
                AudioManager.STREAM_ALARM,
                100
            )

            handler.post(beepRunnable)

        } catch (e: Exception) {
            Log.e(TAG, "Unable to start beep", e)
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager

                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(
                0, 700, 250, 700, 250, 1200
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        pattern,
                        0
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unable to start vibration", e)
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "User pressed stop alarm")

        alarmActive = false
        handler.removeCallbacksAndMessages(null)

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tone", e)
        }

        toneGenerator = null

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration", e)
        }

        vibrator = null

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as android.app.NotificationManager

        manager.cancel(2)

        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {
        }

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }

        toneGenerator = null
        vibrator = null
        alarmActive = false

        Log.d(TAG, "AlarmActivity destroyed")

        super.onDestroy()
    }
}
