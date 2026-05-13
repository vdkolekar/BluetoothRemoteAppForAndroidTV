package com.vdkolekar.bluetoothremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    private val _foundDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val foundDevices: StateFlow<Set<BluetoothDevice>> = _foundDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { d ->
                        _foundDevices.update { it + d }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _isScanning.value = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _isScanning.value = false
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
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (connectedDevice == device) {
                    connectedDevice = null
                }
            }
            _connectionState.value = state
        }
    }

    fun initialize() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        makeDiscoverable()
        bluetoothAdapter?.getProfileProxy(
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
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        _foundDevices.value = emptySet()
        bluetoothAdapter?.startDiscovery()
    }

    fun stopScanning() {
        bluetoothAdapter?.cancelDiscovery()
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopScanning()
        hidDevice?.connect(device)
    }

    fun release() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
    }

    companion object {
        private const val TAG = "BluetoothManager"

        // Standard HID Keyboard Descriptor
        private val DESCRIPTOR_KEYBOARD = byteArrayOf(
            0x05, 0x01,       // Usage Page (Generic Desktop)
            0x09, 0x06,       // Usage (Keyboard)
            0xA1.toByte(), 0x01,       // Collection (Application)
            0x85.toByte(), 0x01,       //   Report ID (1)
            0x05, 0x07,       //   Usage Page (Key Codes)
            0x19, 0xE0.toByte(),       //   Usage Minimum (224)
            0x29, 0xE7.toByte(),       //   Usage Maximum (231)
            0x15, 0x00,       //   Logical Minimum (0)
            0x25, 0x01,       //   Logical Maximum (1)
            0x75, 0x01,       //   Report Size (1)
            0x95.toByte(), 0x08,       //   Report Count (8)
            0x81.toByte(), 0x02,       //   Input (Data, Variable, Absolute) - Modifier byte
            0x95.toByte(), 0x01,       //   Report Count (1)
            0x75, 0x08,       //   Report Size (8)
            0x81.toByte(), 0x01,       //   Input (Constant) - Reserved byte
            0x95.toByte(), 0x05,       //   Report Count (5)
            0x75, 0x01,       //   Report Size (1)
            0x05, 0x08,       //   Usage Page (LEDs)
            0x19, 0x01,       //   Usage Minimum (1)
            0x29, 0x05,       //   Usage Maximum (5)
            0x91.toByte(), 0x02,       //   Output (Data, Variable, Absolute) - LED report
            0x95.toByte(), 0x01,       //   Report Count (1)
            0x75, 0x03,       //   Report Size (3)
            0x91.toByte(), 0x01,       //   Output (Constant) - LED report padding
            0x95.toByte(), 0x06,       //   Report Count (6)
            0x75, 0x08,       //   Report Size (8)
            0x15, 0x00,       //   Logical Minimum (0)
            0x25, 0x65,       //   Logical Maximum (101)
            0x05, 0x07,       //   Usage Page (Key Codes)
            0x19, 0x00,       //   Usage Minimum (0)
            0x29, 0x65,       //   Usage Maximum (101)
            0x81.toByte(), 0x00,       //   Input (Data, Array) - Key arrays (6 bytes)
            0xC0.toByte()              // End Collection
        )
    }
}
