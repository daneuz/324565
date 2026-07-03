package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.network.BleScannerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BleViewModel(private val scannerManager: BleScannerManager) : ViewModel() {
    
    val scannedDevices = scannerManager.scannedDevices.map { 
        it.values.toList().sortedByDescending { device -> device.rssi }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun startScanning(myNodeId: String, myName: String) {
        scannerManager.startScanning(myNodeId, myName)
        // Auto stop after 30 seconds to save battery
        viewModelScope.launch {
            delay(30000)
            scannerManager.stopScanning()
        }
    }

    fun stopScanning() {
        scannerManager.stopScanning()
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.stopScanning()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BleViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return BleViewModel(BleScannerManager(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
