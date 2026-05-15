package com.vdkolekar.bluetoothremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

@SuppressLint("MissingPermission") // Permissions should be handled by UI before calling this
class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _foundDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val foundDevices: StateFlow<Set<BluetoothDevice>> = _foundDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive: action=${intent.action}")
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND, BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = device?.name
                    val address = device?.address
                    Log.d(TAG, "Device found/updated: $name [$address]")
                    device?.let { d ->
                        _foundDevices.update { it + d }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    Log.d(TAG, "Bond state changed: ${device?.name} state=$bondState")
                    
                    if (bondState == BluetoothDevice.BOND_BONDED && device != null) {
                        Log.d(TAG, "Bonding complete, attempting HID connection")
                        hidDevice?.connect(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Discovery started")
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished")
                    _isScanning.value = false
                }
            }
        }
    }

    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: state=$state")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                _connectedDeviceName.value = device.name
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (connectedDevice == device) {
                    connectedDevice = null
                    _connectedDeviceName.value = null
                }
            }
            _connectionState.value = state
        }
    }

    private var isReceiverRegistered = false

    fun initialize() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        isReceiverRegistered = true

        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter is null")
            return
        }
        
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled")
            // In a real app, you'd prompt to enable it
        }

        makeDiscoverable()
        adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        Log.d(TAG, "HID Profile connected")
                        hidDevice = proxy as BluetoothHidDevice
                        registerApp()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        Log.d(TAG, "HID Profile disconnected")
                        hidDevice = null
                    }
                }
            },
            BluetoothProfile.HID_DEVICE
        )
    }

    private fun makeDiscoverable() {
        val adapter = bluetoothAdapter ?: return
        if (adapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            val intent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun registerApp() {
        if (hidDevice == null) return

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Android TV Remote",
            "Antigravity",
            "Antigravity",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD.toByte(),
            DESCRIPTOR_KEYBOARD
        )

        hidDevice?.registerApp(
            sdpSettings,
            null,
            null,
            Executors.newSingleThreadExecutor(),
            hidDeviceCallback
        )
    }

    fun sendReport(reportId: Int, reportData: ByteArray) {
        if (connectedDevice != null && hidDevice != null) {
            hidDevice?.sendReport(connectedDevice!!, reportId, reportData)
        } else {
            Log.w(TAG, "Cannot send report, device not connected")
        }
    }

    fun startScanning() {
        Log.d(TAG, "startScanning called")
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter is null")
            return
        }
        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            return
        }

        if (adapter.isDiscovering) {
            Log.d(TAG, "Discovery already in progress, canceling")
            adapter.cancelDiscovery()
        }
        
        // Add paired devices initially so they are immediately visible
        val pairedDevices = adapter.bondedDevices ?: emptySet()
        Log.d(TAG, "Adding ${pairedDevices.size} paired devices")
        _foundDevices.value = pairedDevices
        
        val started = adapter.startDiscovery()
        Log.d(TAG, "startDiscovery result: $started")
        if (!started) {
            Log.e(TAG, "Failed to start discovery")
        }
    }

    fun isLikelyAndroidTV(device: BluetoothDevice): Boolean {
        val name = device.name ?: ""
        val deviceClass = device.bluetoothClass?.deviceClass ?: 0
        val majorClass = device.bluetoothClass?.majorDeviceClass ?: 0
        
        Log.d(TAG, "Checking device: $name, Class: $deviceClass, Major: $majorClass")

        // Check if name contains TV
        if (name.contains("TV", ignoreCase = true) || name.contains("Android", ignoreCase = true)) {
            return true
        }

        // Major class AUDIO_VIDEO (0x0400)
        if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            // Check specific TV classes using literal values to avoid resolution issues
            // 1032: VIDEO_DISPLAY_DEVICE, 1040: SET_TOP_BOX, 1036: VIDEO_MONITOR
            return deviceClass == 1032 || deviceClass == 1040 || deviceClass == 1036
        }
        
        return false
    }

    fun stopScanning() {
        bluetoothAdapter?.cancelDiscovery()
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopScanning()
        
        // If not bonded, initiate bonding first
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.d(TAG, "Device not bonded, initiating bonding: ${device.name}")
            device.createBond()
            // We'll wait for the bond state change via a receiver or just let the user tap again
            // after bonding is complete. For now, we attempt connection anyway as createBond
            // is asynchronous.
        }
        
        Log.d(TAG, "Connecting to HID device: ${device.name}")
        hidDevice?.connect(device)
    }

    fun release() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
    }

    companion object {
        private const val TAG = "BluetoothManager"

        // Mixed Keyboard and Consumer Control Descriptor
        private val DESCRIPTOR_KEYBOARD = byteArrayOf(
            // --- Keyboard Report (Report ID 1) ---
            0x05, 0x01,                         // Usage Page (Generic Desktop)
            0x09, 0x06,                         // Usage (Keyboard)
            0xA1.toByte(), 0x01,                // Collection (Application)
            0x85.toByte(), 0x01,                //   Report ID (1)
            0x05, 0x07,                         //   Usage Page (Key Codes)
            0x19, 0xE0.toByte(),                //   Usage Minimum (224)
            0x29, 0xE7.toByte(),                //   Usage Maximum (231)
            0x15, 0x00,                         //   Logical Minimum (0)
            0x25, 0x01,                         //   Logical Maximum (1)
            0x75, 0x01,                         //   Report Size (1)
            0x95.toByte(), 0x08,                //   Report Count (8)
            0x81.toByte(), 0x02,                //   Input (Data, Variable, Absolute) - Modifier byte
            0x95.toByte(), 0x01,                //   Report Count (1)
            0x75, 0x08,                         //   Report Size (8)
            0x81.toByte(), 0x01,                //   Input (Constant) - Reserved byte
            0x95.toByte(), 0x06,                //   Report Count (6)
            0x75, 0x08,                         //   Report Size (8)
            0x15, 0x00,                         //   Logical Minimum (0)
            0x25, 0x65,                         //   Logical Maximum (101)
            0x05, 0x07,                         //   Usage Page (Key Codes)
            0x19, 0x00,                         //   Usage Minimum (0)
            0x29, 0x65,                         //   Usage Maximum (101)
            0x81.toByte(), 0x00,                //   Input (Data, Array) - Key arrays (6 bytes)
            0xC0.toByte(),                      // End Collection

            // --- Consumer Control Report (Report ID 2) ---
            0x05, 0x0C,                         // Usage Page (Consumer)
            0x09, 0x01,                         // Usage (Consumer Control)
            0xA1.toByte(), 0x01,                // Collection (Application)
            0x85.toByte(), 0x02,                //   Report ID (2)
            0x15, 0x00,                         //   Logical Minimum (0)
            0x25, 0x01,                         //   Logical Maximum (1)
            0x75, 0x01,                         //   Report Size (1)
            0x95.toByte(), 0x08,                //   Report Count (8)
            0x09, 0xB5.toByte(),                //   Scan Next Track
            0x09, 0xB6.toByte(),                //   Scan Previous Track
            0x09, 0xB7.toByte(),                //   Stop
            0x09, 0xCD.toByte(),                //   Play/Pause
            0x09, 0xE2.toByte(),                //   Mute
            0x09, 0xE9.toByte(),                //   Volume Up
            0x09, 0xEA.toByte(),                //   Volume Down
            0x09, 0x30.toByte(),                //   Power
            0x81.toByte(), 0x02,                //   Input (Data, Variable, Absolute)
            
            // Back, Home and App Shortcuts
            0x0A, 0x23, 0x02,                   //   Usage (Home) - Bit 0
            0x0A, 0x24, 0x02,                   //   Usage (Back) - Bit 1
            0x0A, 0x82.toByte(), 0x01,          //   Usage (AL GUI Browser/Netflix) - Bit 2
            0x0A, 0x89.toByte(), 0x01,          //   Usage (AL Selection/YT) - Bit 3
            0x0A, 0x94.toByte(), 0x01,          //   Usage (AL Local Browser/Prime) - Bit 4
            0x0A, 0x83.toByte(), 0x01,          //   Usage (AL Config/Hotstar) - Bit 5
            
            0x15, 0x00,                         //   Logical Minimum (0)
            0x25, 0x01,                         //   Logical Maximum (1)
            0x75, 0x01,                         //   Report Size (1)
            0x95.toByte(), 0x06,                //   Report Count (6)
            0x81.toByte(), 0x02,                //   Input (Data, Var, Abs)
            
            0x95.toByte(), 0x02,                //   Report Count (2) - Padding to 2 bytes total
            0x81.toByte(), 0x01,                //   Input (Constant)
            
            0xC0.toByte()                       // End Collection
        )
    }
}
