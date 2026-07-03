package com.example.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.StandardCharsets
import java.util.UUID

data class BleDevice(val address: String, val name: String?, val nodeId: String?, val rssi: Int, val timestamp: Long = System.currentTimeMillis())

@SuppressLint("MissingPermission")
class BleScannerManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner
    private val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

    private val _scannedDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val scannedDevices: StateFlow<Map<String, BleDevice>> = _scannedDevices.asStateFlow()

    private val RADAR_SERVICE_UUID = ParcelUuid(UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))
    private var isAdvertising = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                var deviceName = device.name
                var nodeId: String? = null
                
                result.scanRecord?.serviceData?.get(RADAR_SERVICE_UUID)?.let { bytes ->
                    val payload = String(bytes, StandardCharsets.UTF_8)
                    val parts = payload.split("|", limit = 2)
                    if (parts.size == 2) {
                        nodeId = parts[0]
                        deviceName = parts[1]
                    } else {
                        deviceName = payload
                    }
                }

                _scannedDevices.update { current ->
                    val newMap = current.toMutableMap()
                    newMap[device.address] = BleDevice(
                        address = device.address,
                        name = deviceName ?: "Unknown Device",
                        nodeId = nodeId,
                        rssi = result.rssi,
                        timestamp = System.currentTimeMillis()
                    )
                    newMap
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScanner", "Scan failed: $errorCode")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
        }
        override fun onStartFailure(errorCode: Int) {}
    }

    fun startScanning(myNodeId: String, myName: String) {
        if (bluetoothAdapter?.isEnabled == true && scanner != null) {
            try {
                _scannedDevices.value = emptyMap()
                scanner.startScan(listOf(), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
                
                val payload = "$myNodeId|${myName.take(10)}"
                val dataBytes = payload.toByteArray(StandardCharsets.UTF_8)
                
                val advertiseSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build()

                val advertiseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(RADAR_SERVICE_UUID)
                    .addServiceData(RADAR_SERVICE_UUID, dataBytes)
                    .build()

                advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            } catch (e: Exception) {
                Log.e("BleScanner", "Start scan failed", e)
            }
        }
    }

    fun stopScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            try {
                scanner?.stopScan(scanCallback)
                if (isAdvertising) {
                    advertiser?.stopAdvertising(advertiseCallback)
                    isAdvertising = false
                }
            } catch (e: Exception) {
                Log.e("BleScanner", "Stop scan failed", e)
            }
        }
    }
}
