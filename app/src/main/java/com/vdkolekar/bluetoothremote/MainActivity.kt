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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            MaterialTheme(
                colorScheme = if (isDarkMode) {
                    darkColorScheme(
                        background = Color(0xFF0F172A),
                        surface = Color(0xFF1E293B),
                        primary = Color(0xFF3B82F6),
                        onPrimary = Color.White
                    )
                } else {
                    lightColorScheme(
                        background = Color(0xFFF8FAFC),
                        surface = Color.White,
                        primary = Color(0xFF2563EB),
                        onPrimary = Color.White
                    )
                }
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
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val foundDevices by viewModel.foundDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val context = LocalContext.current

    var showDevicePicker by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        InfoDialog(onDismiss = { showInfoDialog = false })
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
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
        val filterOnlyTVs by viewModel.filterOnlyTVs.collectAsState()
        DevicePicker(
            devices = foundDevices.toList(),
            isScanning = isScanning,
            filterOnlyTVs = filterOnlyTVs,
            onDeviceSelected = { device ->
                viewModel.onDeviceSelected(device)
                showDevicePicker = false
            },
            onRefresh = { viewModel.onConnectClicked() },
            onToggleFilter = { viewModel.toggleFilter() },
            onDismiss = { showDevicePicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Power Button
                IconButton(
                    onClick = { viewModel.onPowerClicked() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = Color.Red
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Info Button
                IconButton(
                    onClick = { showInfoDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Dark Mode Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isDarkMode) "Dark" else "Light",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() },
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        StatusBanner(
            state = connectionState,
            deviceName = connectedDeviceName ?: "Android TV",
            onConnectClick = {
                val hasPermissions = permissionsToRequest.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (hasPermissions) {
                    viewModel.onConnectClicked()
                    showDevicePicker = true
                } else {
                    permissionLauncher.launch(permissionsToRequest)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Volume Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteButton(
                icon = Icons.Default.VolumeDown,
                onClick = { viewModel.onVolumeDownClicked() },
                modifier = Modifier.size(56.dp)
            )
            RemoteButton(
                icon = Icons.Default.VolumeMute,
                onClick = { viewModel.onMuteClicked() },
                modifier = Modifier.size(56.dp)
            )
            RemoteButton(
                icon = Icons.Default.VolumeUp,
                onClick = { viewModel.onVolumeUpClicked() },
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // D-Pad Area
        Box(
            modifier = Modifier
                .size(260.dp)
                .shadow(elevation = 15.dp, shape = CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            RemoteButton(
                icon = Icons.Default.KeyboardArrowUp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                onClick = { viewModel.onDpadUpClicked() }
            )
            RemoteButton(
                icon = Icons.Default.KeyboardArrowDown,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                onClick = { viewModel.onDpadDownClicked() }
            )
            RemoteButton(
                icon = Icons.Default.KeyboardArrowLeft,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                onClick = { viewModel.onDpadLeftClicked() }
            )
            RemoteButton(
                icon = Icons.Default.KeyboardArrowRight,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                onClick = { viewModel.onDpadRightClicked() }
            )
            
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickableWithHaptics { viewModel.onOkClicked() },
                contentAlignment = Alignment.Center
            ) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

            // App Shortcuts (Commented out for future use)
            /*
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AppShortcutButton("NF", "Netflix", Color(0xFFE50914)) { viewModel.onNetflixClicked() }
                AppShortcutButton("YT", "YouTube", Color(0xFFFF0000)) { viewModel.onYouTubeClicked() }
                AppShortcutButton("AP", "Prime", Color(0xFF00A8E1)) { viewModel.onPrimeClicked() }
                AppShortcutButton("HS", "Hotstar", Color(0xFF001944)) { viewModel.onHotstarClicked() }
            }
            */

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DevicePicker(
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    filterOnlyTVs: Boolean,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onRefresh: () -> Unit,
    onToggleFilter: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Device")
                Switch(checked = filterOnlyTVs, onCheckedChange = { onToggleFilter() }, modifier = Modifier.scale(0.7f))
            }
        },
        text = {
            Column {
                if (isScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (devices.isEmpty()) {
                    Text("No devices found.", modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
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
        confirmButton = { TextButton(onClick = onRefresh) { Text("Scan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StatusBanner(state: Int, deviceName: String, onConnectClick: () -> Unit) {
    val (statusText, statusColor) = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "Connected" to Color(0xFF10B981)
        BluetoothProfile.STATE_CONNECTING -> "Connecting..." to Color(0xFFF59E0B)
        else -> "Tap to Connect" to Color(0xFFEF4444)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onConnectClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(deviceName, style = MaterialTheme.typography.titleMedium)
        Text(statusText, color = statusColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Connect Your Remote to Android TV") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Follow these simple steps to pair your mobile remote with your Android TV via Bluetooth:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("1. Enable Bluetooth:", fontWeight = FontWeight.Bold)
                Text("Turn on Bluetooth on both your mobile device and your Android TV.")
                Spacer(modifier = Modifier.height(8.dp))

                Text("2. Initial Pairing:", fontWeight = FontWeight.Bold)
                Text("Navigate to your mobile device's Bluetooth settings, look for your Android TV in the available devices list, and select it to connect. (Note: If the connection drops on the first attempt, please try once or twice more).")
                Spacer(modifier = Modifier.height(8.dp))

                Text("3. App Setup:", fontWeight = FontWeight.Bold)
                Text("Open the 3Dfier app, grant the necessary Bluetooth permissions when prompted, and select your Android TV from the in-app device list.")
                Spacer(modifier = Modifier.height(8.dp))

                Text("4. Ready to Use:", fontWeight = FontWeight.Bold)
                Text("Your mobile remote is now securely connected and ready to control your TV!")
                
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "For technical inquiries, bug reports, or product feedback, contact our development team at 3dfier.in@gmail.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Thank you,", fontWeight = FontWeight.Medium)
                Text("Team 3Dfier", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AppShortcutButton(label: String, fullName: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 70.dp, height = 45.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .clickableWithHaptics(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = fullName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
fun RemoteButton(icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableWithHaptics(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun UtilityButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickableWithHaptics(onClick = onClick).padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Modifier.clickableWithHaptics(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else 1f, label = "scale")
    return this.scale(scale).clickable(interactionSource = interactionSource, indication = null) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
}
