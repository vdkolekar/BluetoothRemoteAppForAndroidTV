package com.vdkolekar.bluetoothremote

import android.util.Log

class ReportSender(private val bluetoothManager: BluetoothManager) {

    // Common Keyboard Usage Codes
    object KeyCodes {
        const val UP = 0x52
        const val DOWN = 0x51
        const val LEFT = 0x50
        const val RIGHT = 0x4F
        const val ENTER = 0x28 // OK/Select
        const val ESCAPE = 0x29 // Back
        const val HOME = 0x4A // Home key
    }

    fun sendKeyPress(keyCode: Int) {
        Log.d(TAG, "Sending key press: $keyCode")
        // Keyboard report format: [Modifier, Reserved, Key1, Key2, Key3, Key4, Key5, Key6]
        val downReport = ByteArray(8)
        downReport[2] = keyCode.toByte()
        bluetoothManager.sendReport(1, downReport)
    }

    fun sendKeyRelease() {
        Log.d(TAG, "Sending key release")
        // Empty report means all keys released
        val upReport = ByteArray(8)
        bluetoothManager.sendReport(1, upReport)
    }

    fun sendClick(keyCode: Int) {
        sendKeyPress(keyCode)
        // A small delay might be necessary depending on the TV's polling rate,
        // but typically the OS handles consecutive sendReport calls fast enough.
        // If issues arise, a Coroutine delay can be added here.
        sendKeyRelease()
    }

    companion object {
        private const val TAG = "ReportSender"
    }
}
