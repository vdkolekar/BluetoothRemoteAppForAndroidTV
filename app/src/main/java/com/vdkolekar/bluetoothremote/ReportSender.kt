package com.vdkolekar.bluetoothremote

import android.util.Log

class ReportSender(private val bluetoothManager: BluetoothManager) {

    // Keyboard Usage Page (0x07) Key Codes
    object KeyboardKeyCodes {
        const val UP = 0x52
        const val DOWN = 0x51
        const val LEFT = 0x50
        const val RIGHT = 0x4F
        const val ENTER = 0x28 // OK/Select
    }

    // Consumer Page (0x0C) Bit Positions for Report ID 2, Byte 0
    object ConsumerBitPositions {
        const val PLAY_PAUSE = 3
        const val MUTE = 4
        const val VOLUME_UP = 5
        const val VOLUME_DOWN = 6
        const val POWER = 7
    }

    // Consumer Page (0x0C) Bit Positions for Report ID 2, Byte 1
    object ConsumerBitPositionsByte1 {
        const val HOME = 0
        const val BACK = 1
        const val NETFLIX = 2
        const val YOUTUBE = 3
        const val PRIME = 4
        const val HOTSTAR = 5
    }

    fun sendKeyboardClick(keyCode: Int) {
        Log.d(TAG, "Sending keyboard click: $keyCode")
        val downReport = ByteArray(8)
        downReport[2] = keyCode.toByte()
        bluetoothManager.sendReport(1, downReport)
        
        val upReport = ByteArray(8)
        bluetoothManager.sendReport(1, upReport)
    }

    fun sendConsumerClick(byteIndex: Int, bitPosition: Int) {
        Log.d(TAG, "Sending consumer click: byte $byteIndex, bit $bitPosition")
        val downReport = ByteArray(2)
        downReport[byteIndex] = (1 shl bitPosition).toByte()
        bluetoothManager.sendReport(2, downReport)
        
        val upReport = ByteArray(2)
        bluetoothManager.sendReport(2, upReport)
    }

    companion object {
        private const val TAG = "ReportSender"
    }
}
