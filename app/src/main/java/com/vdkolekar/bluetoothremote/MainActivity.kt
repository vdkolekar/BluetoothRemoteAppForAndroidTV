package com.vdkolekar.bluetoothremote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private val viewModel: RemoteViewModel by viewModels {
        RemoteViewModel.Factory(bluetoothManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothManager = BluetoothManager(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0F172A), // Slate 900
                    surface = Color(0xFF1E293B),    // Slate 800
                    primary = Color(0xFF3B82F6),    // Blue 500
                    onPrimary = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RemoteApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun RemoteApp(viewModel: RemoteViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val foundDevices by viewModel.foundDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val context = LocalContext.current

    var showDevicePicker by remember { mutableStateOf(false) }

    // Permission handling
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.onConnectClicked()
            showDevicePicker = true
        }
    }

    if (showDevicePicker) {
        DevicePicker(
            devices = foundDevices.toList(),
            isScanning = isScanning,
            onDeviceSelected = { device ->
                viewModel.onDeviceSelected(device)
                showDevicePicker = false
            },
            onDismiss = { showDevicePicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Header / Status
        StatusBanner(
            state = connectionState,
            onConnectClick = {
                Log.d("MainActivity", "Connect banner clicked")
                val hasPermissions = permissionsToRequest.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                Log.d("MainActivity", "Has permissions: $hasPermissions")
                if (hasPermissions) {
                    viewModel.onConnectClicked()
                    showDevicePicker = true
                } else {
                    permissionLauncher.launch(permissionsToRequest)
                }
            }
        )

        Spacer(modifier = Modifier.height(64.dp))

        // D-Pad Area
        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF334155), // Slate 700
                            Color(0xFF1E293B)  // Slate 800
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Up
            RemoteButton(
                icon = Icons.Default.KeyboardArrowUp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                onClick = { viewModel.onDpadUpClicked() }
            )
            // Down
            RemoteButton(
                icon = Icons.Default.KeyboardArrowDown,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                onClick = { viewModel.onDpadDownClicked() }
            )
            // Left
            RemoteButton(
                icon = Icons.Default.KeyboardArrowLeft,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                onClick = { viewModel.onDpadLeftClicked() }
            )
            // Right
            RemoteButton(
                icon = Icons.Default.KeyboardArrowRight,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                onClick = { viewModel.onDpadRightClicked() }
            )
            
            // OK Center Button
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                Color(0xFF60A5FA) // Blue 400
                            )
                        )
                    )
                    .clickableWithHaptics { viewModel.onOkClicked() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Utilities
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            UtilityButton(
                icon = Icons.Default.ArrowBack,
                label = "Back",
                onClick = { viewModel.onBackClicked() }
            )
            UtilityButton(
                icon = Icons.Default.Home,
                label = "Home",
                onClick = { viewModel.onHomeClicked() }
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun DevicePicker(
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Android TV") },
        text = {
            Column {
                if (isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (devices.isEmpty()) {
                    Text("Searching for devices...")
                } else {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(devices) { device ->
                            @SuppressLint("MissingPermission")
                            val deviceName = device.name ?: "Unknown Device"
                            ListItem(
                                headlineContent = { Text(deviceName) },
                                supportingContent = { Text(device.address) },
                                modifier = Modifier.clickable { onDeviceSelected(device) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StatusBanner(state: Int, onConnectClick: () -> Unit) {
    val (statusText, statusColor) = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "Connected" to Color(0xFF10B981) // Emerald 500
        BluetoothProfile.STATE_CONNECTING -> "Connecting..." to Color(0xFFF59E0B) // Amber 500
        else -> "Tap to Connect" to Color(0xFFEF4444) // Red 500
    }

    val animatedColor by animateColorAsState(targetValue = statusColor, label = "color")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B))
            .clickable(onClick = onConnectClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Android TV",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = statusText,
                color = animatedColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(animatedColor)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = animatedColor
                )
        )
    }
}

@Composable
fun RemoteButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickableWithHaptics(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun UtilityButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickableWithHaptics(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

// Custom modifier for adding haptic feedback and scale animation to clicks
@Composable
fun Modifier.clickableWithHaptics(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null // We handle our own visual feedback with scaling
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
}
