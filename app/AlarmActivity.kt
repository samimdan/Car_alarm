
package com.example.pugout405samrtkey

import android.app.Activity
import android.content.Context
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
import android.graphics.Color
import android.graphics.Typeface

class AlarmActivity : Activity() {

    companion object {
        const val ACTION_STOP_ALARM =
            "com.example.pugout405samrtkey.STOP_ALARM"
    }

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    private val handler = Handler(Looper.getMainLooper())

    private var alarmActive = false

    private val beepRunnable = object : Runnable {
        override fun run() {
            if (!alarmActive) return

            try {
                toneGenerator?.startTone(
                    ToneGenerator.TONE_PROP_BEEP2,
                    350
                )
            } catch (_: Exception) {
            }

            handler.postDelayed(this, 1200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreen()
        createAlarmScreen()
        startAlarmEffects()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTaskDescription(
                android.app.ActivityManager.TaskDescription(
                    "Peugeot 405 Alarm",
                    null,
                    Color.rgb(150, 0, 0)
                )
            )
        }
    }

    private fun createAlarmScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 48)
            setBackgroundColor(Color.rgb(35, 0, 0))
        }

        val title = TextView(this).apply {
            text = "🚨 PEUGEOT 405"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = "هشدار امنیتی خودرو!\n\nاحتمال دستکاری یا ورود غیرمجاز به خودرو وجود دارد."
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12, 50, 12, 50)
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
            setBackgroundColor(Color.rgb(0, 125, 60))
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
                65
            )
        )

        setContentView(root)
    }

    private fun startAlarmEffects() {
        if (alarmActive) return

        alarmActive = true

        try {
            toneGenerator = ToneGenerator(
                AudioManager.STREAM_ALARM,
                100
            )

            handler.post(beepRunnable)
        } catch (_: Exception) {
            toneGenerator = null
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

        } catch (_: Exception) {
        }
    }

    private fun stopAlarm() {
        alarmActive = false

        handler.removeCallbacksAndMessages(null)

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {
        }

        toneGenerator = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }

        vibrator = null

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as android.app.NotificationManager

        manager.cancel(2)

        finish()
    }

    override fun onDestroy() {
        if (alarmActive) {
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

            alarmActive = false
        }

        super.onDestroy()
    }
}
