package com.example.pugout405samrtkey.ui.theme

import android.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.pugout405samrtkey.BleAdvertisingService
import com.example.pugout405samrtkey.Esp32Telemetry

private val Midnight = Color(0xFF070D19)
private val Panel = Color(0xFF0D192A)
private val Cyan = Color(0xFF62DFFF)
private val Muted = Color(0xFF8DA2BD)
private val Bright = Color(0xFFF0F7FF)
data class CarStatus(
    val isLocked: Boolean,
    val bluetoothConnected: Boolean,
    val rssi: Int
)
enum class BluetoothStatus {
    CONNECTED,
    DISCONNECTED
}
enum class RssiLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}
data class BluetoothInfo(
    val status: BluetoothStatus,
    val rssiLevel: RssiLevel,
    val btUi: BTUi
)
enum class WifiStats{
DISCONNECTED,
    CONNECTED
}
enum class DoorStatus{
    OPEN,
    CLOSE
}
enum class EcaStatus{
   ON,
   OFF
}
enum class EngineStatus{
    ON,
    OFF
}
data class BTUi(
    val btIcon: ImageVector,
    val btIconColor: Color
)
@Composable
fun CarAlarmScreen(
    modifier: Modifier = Modifier,
    carName: String = "405",
    onLockClick:()->Unit,
    onTrunkClick:()->Unit,
    onFindClick:()->Unit,
    onAntiTheftClick:()-> Unit,
    RSSI: String,
    Bluethooth: Boolean,

) {
    val esp32Connected by BleAdvertisingService
        .isEsp32Connected.collectAsState(initial = false)
    var isLocked by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val InternetAccess = remember { mutableStateOf(WifiStats.CONNECTED) }
    val weakBT = BluetoothInfo(BluetoothStatus.CONNECTED, RssiLevel.LOW, BTUi(Icons.Filled.SignalCellularAlt1Bar, Color.White))
    val mediumBT = BluetoothInfo(BluetoothStatus.CONNECTED, RssiLevel.MEDIUM, BTUi(Icons.Filled.SignalCellularAlt2Bar, Color.White))
    val strongBT = BluetoothInfo(BluetoothStatus.CONNECTED, RssiLevel.HIGH, BTUi(Icons.Filled.SignalCellularAlt, Color.White))
    val disconnectedBT = BluetoothInfo(
        BluetoothStatus.DISCONNECTED,
        RssiLevel.NONE,
        BTUi(Icons.Filled.BluetoothDisabled, Color.White)
    )
    val esp32Rssi by BleAdvertisingService
        .esp32Rssi.collectAsState(initial = null)
    val telemetry by BleAdvertisingService
        .telemetry.collectAsState(initial = null)

    LaunchedEffect(esp32Rssi, esp32Connected) {
        android.util.Log.d(
            "RSSI_DEBUG",
            "Connected: $esp32Connected | RSSI: $esp32Rssi"
        )
    }

    val btInfo = when {
        !esp32Connected -> disconnectedBT
        esp32Rssi == null -> mediumBT
        esp32Rssi!! >= -60 -> strongBT
        esp32Rssi!! >= -75 -> mediumBT
        else -> weakBT
    } // Replace with actual Bluetooth info
    var doorStatus by remember { mutableStateOf(DoorStatus.OPEN) }
    var ecaStatus by remember { mutableStateOf(EcaStatus.ON) }
    var engineStatue by remember {mutableStateOf(EngineStatus.OFF)}
    val uiMotion = rememberInfiniteTransition(label = "ui_motion")
    val bgShift by uiMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bg_shift",
    )
    val bgPulse by uiMotion.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bg_pulse",
    )
    val scanSweep by uiMotion.animateFloat(
        initialValue = -200f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan_sweep",
    )

    Scaffold(
        modifier = modifier,
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0C1630),
                            Midnight,
                            Color(0xFF040811),
                        ),
                        startY = -220f * bgShift,
                        endY = 1800f + (220f * bgShift),
                    ),
                )
                .padding(insets),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.26f * bgPulse)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x6630C9FF), Color.Transparent),
                            center = Offset(220f + (420f * bgShift), 320f),
                            radius = 560f,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.14f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x66FFFFFF),
                                Color.Transparent,
                            ),
                            start = Offset(0f, scanSweep),
                            end = Offset(1200f, scanSweep + 260f),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "Samim VEHICLE",
                            color = Bright,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp,
                        )
                        Text(
                            "CONTROL CENTER",
                            color = Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Bright),
                    ) {
                        Text("⚙", fontSize = 22.sp)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF11213A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF284365)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x33238FFF), Color.Transparent),
                                    center = Offset(180f, 130f),
                                    radius = 310f,
                                ),
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Eyebrow("YOUR CAR")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    carName,
                                    color = Bright,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedCar(
                            isLocked = isLocked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                                .padding(horizontal = 12.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF21344D)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if(Bluethooth) {
                            BTIcon()
                        }
                        else{
                  if (btInfo.status == BluetoothStatus.CONNECTED) {
                      BTConnected()
                  }else{
                      BTDisconnected()
                  }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                if (btInfo.status == BluetoothStatus.CONNECTED) "Bluetooth connected" else "Bluetooth disconnected",
                                color = Bright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                        }
                        TickIcon()
                        VerticalDivider(
                            modifier = Modifier
                                .height(32.dp)
                                .padding(horizontal = 12.dp),
                            color = Color(0xFF21344D),
                            thickness = 1.dp,
                        )
                        if(InternetAccess.value == WifiStats.CONNECTED)InternetAccess() else InternetDisconnect()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                if (InternetAccess.value == WifiStats.CONNECTED) "Internet connected" else "Internet disconnected",
                                color = Bright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                        }
                        TickIcon()
                        VerticalDivider(
                            modifier = Modifier
                                .height(32.dp)
                                .padding(horizontal = 12.dp),
                            color = Color(0xFF21344D),
                            thickness = 1.dp,
                        )
                        BTStatusBar(btInfo.btUi)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                text = "Connected: $esp32Connected | Phone RSSI: ${telemetry?.phoneRssiDbm?.let { "$it dBm" } ?: "waiting"}",
                                color = Bright,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Lock: ${
                                    when (telemetry?.lockState) {
                                        Esp32Telemetry.LockState.LOCKED -> "Locked"
                                        Esp32Telemetry.LockState.UNLOCKED -> "Unlocked"
                                        else -> "Unknown"
                                    }
                                } | Alarm: ${
                                    when {
                                        telemetry == null -> "Unknown"
                                        telemetry?.alarmPending == true -> "Pending"
                                        else -> "Clear"
                                    }
                                }",
                                color = Bright,
                                fontSize = 12.sp
                            )
                        }




                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF21344D)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (doorStatus == DoorStatus.CLOSE) DoorClose() else DoorOpen()
                            Text(
                                if (doorStatus == DoorStatus.CLOSE) "Door closed" else "Door is open",
                                color = Bright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 8.dp),
                            color = Color(0xFF21344D),
                            thickness = 1.dp,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Ecu(ecaStatus)
                            Text(
                                text = if (ecaStatus == EcaStatus.ON) "ECA On" else "ECA Off",
                                color = Bright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 8.dp),
                            color = Color(0xFF21344D),
                            thickness = 1.dp,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Engine(engineStatue)
                            Text(
                                if (engineStatue == EngineStatus.ON) "Engine started" else "Engine stopped",
                                color = Bright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                Card(
                    onClick = { if (isLocked) {
                        android.util.Log.d("BLE_COMMAND", "UNLOCK BUTTON CLICKED")
                        BleAdvertisingService.requestUnlock()
                        isLocked = false
                    } else {
                        android.util.Log.d("BLE_COMMAND", "LOCK BUTTON CLICKED")
                        BleAdvertisingService.requestLock()
                        isLocked = true
                    }},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(248.dp),

                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1626)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243B58)),
                ) {

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "SECURITY STATUS",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 20.dp),
                            color = Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                        )
                        Spacer(Modifier.height(8.dp))

                        val unlockRingTransition =
                            rememberInfiniteTransition(label = "unlock_ring")
                        val unlockRingRotation by unlockRingTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "unlock_ring_rotation",
                        )
                        val lockedRingRotation by unlockRingTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 3400, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "locked_ring_rotation",
                        )
                        val accentRingRotation by unlockRingTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 5100, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "accent_ring_rotation",
                        )
                        val glowPulse by unlockRingTransition.animateFloat(
                            initialValue = 0.62f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 1450,
                                    easing = FastOutSlowInEasing
                                ),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "ring_glow_pulse",
                        )

                        val primaryRingBrush = if (isLocked) {
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF22C55E),
                                    Color(0xFF86EFAC),
                                    Color(0x4022C55E),
                                    Color(0xFF0E9F6E),
                                    Color(0xFF22C55E),
                                ),
                            )
                        } else {
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF1744),
                                    Color(0xFFFF6B6B),
                                    Color(0x40FF6B6B),
                                    Color(0xFFFFD166),
                                    Color(0xFFFF1744),
                                ),
                            )
                        }
                        val accentRingBrush = if (isLocked) {
                            Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF62DFFF),
                                    Color.Transparent,
                                    Color(0xFF34D399),
                                    Color.Transparent,
                                ),
                            )
                        } else {
                            Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFFFF8A80),
                                    Color.Transparent,
                                    Color(0xFFFF3D7F),
                                    Color.Transparent,
                                ),
                            )
                        }

                        val primaryRotation =
                            if (isLocked) -lockedRingRotation else unlockRingRotation
                        val secondaryRotation = -accentRingRotation

                        Box(
                            modifier = Modifier
                                .size(152.dp)
                                .shadow(
                                    30.dp,
                                    CircleShape,
                                    ambientColor = if (isLocked) {
                                        Color(0xFF5CF2A5).copy(alpha = 0.85f * glowPulse)
                                    } else {
                                        Color(0xFFFF4D67).copy(alpha = 0.9f * glowPulse)
                                    },
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isLocked) Color(0xFF0D2A1C) else Color(
                                        0xFF2B1219
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.38f * glowPulse)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                if (isLocked) Color(0x4434D399) else Color(
                                                    0x44FF6B6B
                                                ),
                                                Color.Transparent,
                                            ),
                                        ),
                                        CircleShape,
                                    ),
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .rotate(primaryRotation)
                                    .border(4.dp, primaryRingBrush, CircleShape),
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .rotate(secondaryRotation)
                                    .border(2.dp, accentRingBrush, CircleShape),
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isLocked) Color(0xFF133125) else Color(
                                            0xFF3A1822
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                LockIllustration(
                                    isLocked = isLocked,
                                    modifier = Modifier.size(70.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (isLocked) "LOCKED" else "UNLOCKED",
                            color = Bright,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (isLocked) "Your vehicle is secure" else "Vehicle access is enabled",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(
                            onClick = {
                                isLocked = !isLocked
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .height(80.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF133F73)
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data("file:///android_asset/trunk.svg")
                                            .decoderFactory(SvgDecoder.Factory())
                                            .build()
                                    ),
                                    contentDescription = "Trunk",
                                    modifier = Modifier.size(30.dp),
                                    tint = Color.White,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Trunk",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text("Open Car Trunk", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(
                            onClick = {
                                isLocked = !isLocked

                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .height(80.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF133F73)
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = "Place",
                                    modifier = Modifier.size(30.dp),
                                    tint = Color.White,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Find car",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text("Horne", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(
                            onClick = {
                                isLocked = !isLocked
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .height(80.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF133F73)
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data("file:///android_asset/anti-theft-system.svg")
                                            .decoderFactory(SvgDecoder.Factory())
                                            .build()
                                    ),
                                    contentDescription = "Anti theft",
                                    modifier = Modifier.size(30.dp),
                                    tint = Color.White,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Anti Theft",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text("Stop the car!", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (showSettings) {
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        title = { Text("Vehicle settings") },
                        text = { Text("Manage your vehicle connection and security preferences.") },
                        confirmButton = {
                            TextButton(onClick = { showSettings = false }) {
                                Text("Done")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
}
@Composable
private fun InternetAccess(modifier: Modifier= Modifier) {
    Icon(
        imageVector = Icons.Filled.Wifi,
        contentDescription = "WiFi Icon",
        modifier = Modifier.size(24.dp),
        tint = Color.White,
    )
}
@Composable
private fun DoorClose(modifier: Modifier= Modifier) {
    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .crossfade(true)
        .build()
    AsyncImage(
        model = "file:///android_asset/closedoor.svg",
        contentDescription = "Car Image",
        imageLoader = imageLoader,
        modifier = modifier.size(28.dp),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(Color(0xFF197953))

    )
}
@Composable
private fun DoorOpen(modifier: Modifier= Modifier) {
    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .crossfade(true)
        .build()
    AsyncImage(
        model = "file:///android_asset/opendoor.svg",
        contentDescription = "Car Image",
        imageLoader = imageLoader,
        modifier = modifier.size(20.dp),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(Color(0xFF791919))


    )

}
@Composable
private fun Ecu(status: EcaStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ecuColor= if (status == EcaStatus.ON) {
        Color(0xFF24720F)
    } else {
        Color(0xFF484848)
    }
    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .crossfade(true)
        .build()
    AsyncImage(
        model = "file:///android_asset/ecu.svg",
        contentDescription = "Car Image",
        imageLoader = imageLoader,
        modifier = modifier.size(28.dp),
        contentScale = ContentScale.Fit,

        colorFilter = ColorFilter.tint(ecuColor)
    )

}
@Composable
private fun InternetDisconnect() {
    Icon(
        imageVector = Icons.Filled.WifiOff,
        contentDescription = "Internet Disconnect Icon",
        modifier = Modifier.size(24.dp),
        tint = Color.Red,
    )
}



@Composable
private fun Engine(status: EngineStatus, modifier: Modifier = Modifier){
    val context = LocalContext.current
    val engineColor = if (status == EngineStatus.ON) {
        Color(0xFF42D519)
    } else {
        Color(0xFF484848)
    }
    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .crossfade(true)
        .build()
    AsyncImage(
        model = "file:///android_asset/engine.svg",
        contentDescription = "Engine status",
        imageLoader = imageLoader,
        modifier = modifier.size(28.dp),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(engineColor),
    )
}
@Composable
private fun BTIcon() {
    Icon(
      imageVector = Icons.Filled.BluetoothConnected,
        contentDescription = "Bluetooth Icon",
        modifier = Modifier.size(24.dp),
        tint = Color.White,
    )
}

@Composable
private fun TickIcon() {
    Icon(
        imageVector = Icons.Outlined.CheckCircleOutline,
        contentDescription = "Tick Icon",
        modifier = Modifier.size(24.dp),
        tint = Color(0xFF42D519),
    )
}
@Composable
private fun BTConnected() {
    Icon(
        imageVector = Icons.Filled.BluetoothConnected,
        contentDescription = "Bluetooth Status Icon",
        modifier = Modifier.size(24.dp),
        tint = Color(0xFF42D519),
    )
}
@Composable
private fun BTDisconnected() {
    Icon(
        imageVector = Icons.Filled.BluetoothDisabled,
        contentDescription = "Bluetooth Status Icon",
        modifier = Modifier.size(24.dp),
        tint = Color(0xFFD51945),
    )
}
@Composable
private fun BTStatusBar(btUi: BTUi) {
    val imageVector: ImageVector = btUi.btIcon

    Icon(
        imageVector = imageVector,
        contentDescription = "Bluetooth Status Icon",
        modifier = Modifier.size(24.dp),
        tint = btUi.btIconColor,
    )
}
@Composable

private fun LockIllustration(isLocked: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.2f, h * 0.48f)
            quadraticTo(w * 0.2f, h * 0.39f, w * 0.3f, h * 0.39f)
            lineTo(w * 0.7f, h * 0.39f)
            quadraticTo(w * 0.8f, h * 0.39f, w * 0.8f, h * 0.48f)
            lineTo(w * 0.8f, h * 0.84f)
            quadraticTo(w * 0.8f, h * 0.94f, w * 0.7f, h * 0.94f)
            lineTo(w * 0.3f, h * 0.94f)
            quadraticTo(w * 0.2f, h * 0.94f, w * 0.2f, h * 0.84f)
            close()
        }
        drawPath(body, Color(0xFFE9F7FF))

        val shackle = Path().apply {
            if (isLocked) {
                moveTo(w * 0.34f, h * 0.43f)
                lineTo(w * 0.34f, h * 0.29f)
                cubicTo(w * 0.34f, h * 0.08f, w * 0.66f, h * 0.08f, w * 0.66f, h * 0.29f)
                lineTo(w * 0.66f, h * 0.43f)
            } else {
                moveTo(w * 0.34f, h * 0.43f)
                lineTo(w * 0.34f, h * 0.29f)
                cubicTo(w * 0.34f, h * 0.08f, w * 0.66f, h * 0.08f, w * 0.66f, h * 0.29f)
            }
        }
        drawPath(
            shackle,
            Color(0xFF66DFFF),
            style = Stroke(width = w * 0.085f, cap = StrokeCap.Round),
        )
        drawCircle(Color(0xFF155EA8), radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.6f))
        drawLine(
            Color(0xFF155EA8),
            Offset(w * 0.5f, h * 0.63f),
            Offset(w * 0.5f, h * 0.7f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round,
        )
    }
}
