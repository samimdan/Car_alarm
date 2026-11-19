
package com.example.pugout405samrtkey

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class EmergencyActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("ACKNOWLEDGE_ALARM", false)) {

            val stopIntent = Intent(
                this,
                BleAdvertisingService::class.java
            ).apply {
                action = BleAdvertisingService.ACTION_ACK_ALARM
            }

            startService(stopIntent)

            intent.removeExtra("ACKNOWLEDGE_ALARM")
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContent {
            MaterialTheme {
                EmergencyScreen(
                    onStop = { finish() }
                )
            }
        }
    }
}

@Composable
fun EmergencyScreen(onStop: () -> Unit) {

    val backgroundColor = Color(0xFF180A0A)
    val red = Color(0xFFFF3030)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "⚠",
            fontSize = 90.sp,
            color = red
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "هشدار سرقت خودرو",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = red
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "PEUGEOT 405",
            fontSize = 22.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "فرمان اضطراری از خودرو دریافت شد!",
            fontSize = 16.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = red
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "بستن صفحه هشدار",
                fontSize = 18.sp,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
