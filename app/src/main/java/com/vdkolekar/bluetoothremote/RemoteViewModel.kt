package com.vdkolekar.bluetoothremote

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow

class RemoteViewModel(private val bluetoothManager: BluetoothManager) : ViewModel() {

    private val reportSender = ReportSender(bluetoothManager)

    val connectionState: StateFlow<Int> = bluetoothManager.connectionState
    val foundDevices: StateFlow<Set<BluetoothDevice>> = bluetoothManager.foundDevices
    val isScanning: StateFlow<Boolean> = bluetoothManager.isScanning

    fun onConnectClicked() {
        android.util.Log.d("RemoteViewModel", "onConnectClicked called")
        bluetoothManager.initialize()
        bluetoothManager.startScanning()
    }

    fun onDeviceSelected(device: BluetoothDevice) {
        bluetoothManager.connectToDevice(device)
    }

    fun onDisconnectClicked() {
        bluetoothManager.release()
    }

    fun onDpadUpClicked() {
        reportSender.sendClick(ReportSender.KeyCodes.UP)
    }

    fun onDpadDownClicked() {
        reportSender.sendClick(ReportSender.KeyCodes.DOWN)
    }

    fun onDpadLeftClicked() {
        reportSender.sendClick(ReportSender.KeyCodes.LEFT)
    }

    fun onDpadRightClicked() {
        reportSender.sendClick(ReportSender.KeyCodes.RIGHT)
    }

    fun onOkClicked() {
        reportSender.sendClick(ReportSender.KeyCodes.ENTER)
    }

    fun onBackClicked() {
        reportSender.sendClick(ReportSender.KeyCodes.ESCAPE)
    }

    fun onHomeClicked() {
        reportSender.sendClick(ReportSender.KeyCodes.HOME)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.release()
    }

    class Factory(private val bluetoothManager: BluetoothManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RemoteViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RemoteViewModel(bluetoothManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
