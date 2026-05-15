package com.vdkolekar.bluetoothremote

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*

class RemoteViewModel(private val bluetoothManager: BluetoothManager) : ViewModel() {

    private val reportSender = ReportSender(bluetoothManager)

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _filterOnlyTVs = MutableStateFlow(true)
    val filterOnlyTVs: StateFlow<Boolean> = _filterOnlyTVs.asStateFlow()

    fun toggleFilter() {
        _filterOnlyTVs.value = !_filterOnlyTVs.value
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    val connectionState: StateFlow<Int> = bluetoothManager.connectionState
    val connectedDeviceName: StateFlow<String?> = bluetoothManager.connectedDeviceName
    
    val foundDevices: StateFlow<Set<BluetoothDevice>> = combine(
        bluetoothManager.foundDevices,
        _filterOnlyTVs
    ) { devices, filter ->
        if (filter) {
            devices.filter { bluetoothManager.isLikelyAndroidTV(it) }.toSet()
        } else {
            devices
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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
        reportSender.sendKeyboardClick(ReportSender.KeyboardKeyCodes.UP)
    }

    fun onDpadDownClicked() {
        reportSender.sendKeyboardClick(ReportSender.KeyboardKeyCodes.DOWN)
    }

    fun onDpadLeftClicked() {
        reportSender.sendKeyboardClick(ReportSender.KeyboardKeyCodes.LEFT)
    }

    fun onDpadRightClicked() {
        reportSender.sendKeyboardClick(ReportSender.KeyboardKeyCodes.RIGHT)
    }

    fun onOkClicked() {
        reportSender.sendKeyboardClick(ReportSender.KeyboardKeyCodes.ENTER)
    }

    fun onBackClicked() {
        reportSender.sendConsumerClick(1, ReportSender.ConsumerBitPositionsByte1.BACK)
    }

    fun onHomeClicked() {
        reportSender.sendConsumerClick(1, ReportSender.ConsumerBitPositionsByte1.HOME)
    }

    fun onPowerClicked() {
        reportSender.sendConsumerClick(0, ReportSender.ConsumerBitPositions.POWER)
    }

    fun onVolumeUpClicked() {
        reportSender.sendConsumerClick(0, ReportSender.ConsumerBitPositions.VOLUME_UP)
    }

    fun onVolumeDownClicked() {
        reportSender.sendConsumerClick(0, ReportSender.ConsumerBitPositions.VOLUME_DOWN)
    }

    fun onMuteClicked() {
        reportSender.sendConsumerClick(0, ReportSender.ConsumerBitPositions.MUTE)
    }

    fun onNetflixClicked() {
        reportSender.sendConsumerClick(1, ReportSender.ConsumerBitPositionsByte1.NETFLIX)
    }

    fun onYouTubeClicked() {
        reportSender.sendConsumerClick(1, ReportSender.ConsumerBitPositionsByte1.YOUTUBE)
    }

    fun onPrimeClicked() {
        reportSender.sendConsumerClick(1, ReportSender.ConsumerBitPositionsByte1.PRIME)
    }

    fun onHotstarClicked() {
        reportSender.sendConsumerClick(1, ReportSender.ConsumerBitPositionsByte1.HOTSTAR)
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
