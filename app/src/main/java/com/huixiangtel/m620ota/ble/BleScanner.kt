package com.huixiangtel.m620ota.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** M620 设备状态。 */
enum class DeviceMode {
    /** 固件运行正常，广播名 M620_Vx.xX_xxxx */
    NORMAL,

    /** 固件异常，已进入 DFU 模式，广播名 DFU_xxxx */
    DFU
}

/** 扫描到的 M620 设备。 */
data class M620Device(
    val address: String,
    val name: String,
    val rssi: Int,
    val mode: DeviceMode,
    /** 从广播名中解析出的固件版本号，如 "0.6A"；无则 null */
    val broadcastVersion: String?,
    /** 设备唯一标识后缀，如 "7CDC" */
    val deviceTag: String?,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * 扫描 M620 BLE 设备。
 *
 * 广播名规则：
 * - 正常运行：`M620_V0.6A_7CDC`（含固件版本号 + 设备标识）
 * - DFU 异常：`DFU_xxxx`
 *
 * 注意：M620 仅在充电瞬间开启广播，持续约 5 分钟，
 * 超时后需重新插拔充电才能再次广播。
 */
@SuppressLint("MissingPermission")
class BleScanner(context: Context) {

    companion object {
        const val PREFIX_NORMAL = "M620_"
        const val PREFIX_DFU = "DFU_"

        /** M620_V0.6A_7CDC → Triple(版本 0.6A, 标识 7CDC) */
        private val NORMAL_PATTERN =
            Regex("^M620_V?([0-9]+(?:\\.[0-9]+)*[A-Z]?)_(.+)$", RegexOption.IGNORE_CASE)

        /** M620_XXXX（无版本号的旧格式） */
        private val LEGACY_PATTERN = Regex("^M620_(.+)$", RegexOption.IGNORE_CASE)

        private val DFU_PATTERN = Regex("^DFU_(.+)$", RegexOption.IGNORE_CASE)

        /**
         * 解析广播名。
         * @return Pair(模式, Pair(版本号?, 设备标识?))，非 M620 设备返回 null
         */
        fun parseName(name: String?): Pair<DeviceMode, Pair<String?, String?>>? {
            if (name == null) return null
            NORMAL_PATTERN.matchEntire(name)?.let { m ->
                return DeviceMode.NORMAL to (m.groupValues[1].uppercase() to m.groupValues[2])
            }
            DFU_PATTERN.matchEntire(name)?.let { m ->
                return DeviceMode.DFU to (null to m.groupValues[1])
            }
            LEGACY_PATTERN.matchEntire(name)?.let { m ->
                return DeviceMode.NORMAL to (null to m.groupValues[1])
            }
            return null
        }
    }

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _devices = MutableStateFlow<Map<String, M620Device>>(emptyMap())
    val devices: StateFlow<Map<String, M620Device>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            val (mode, extra) = parseName(name) ?: return
            val (version, tag) = extra
            val device = M620Device(
                address = result.device.address,
                name = name,
                rssi = result.rssi,
                mode = mode,
                broadcastVersion = version,
                deviceTag = tag
            )
            _devices.value = _devices.value + (device.address to device)
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        _devices.value = emptyMap()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)
        _scanning.value = true
    }

    fun stopScan() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
        _scanning.value = false
    }
}
