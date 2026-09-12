package com.huixiangtel.m620ota

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huixiangtel.m620ota.ble.BleScanner
import com.huixiangtel.m620ota.ble.DeviceConnection
import com.huixiangtel.m620ota.ble.DeviceMode
import com.huixiangtel.m620ota.ble.M620Device
import com.huixiangtel.m620ota.ota.DfuManager
import com.huixiangtel.m620ota.ota.DfuState
import com.huixiangtel.m620ota.ota.DownloadResult
import com.huixiangtel.m620ota.ota.FirmwareInfo
import com.huixiangtel.m620ota.ota.FirmwareRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** 界面阶段。 */
enum class Stage {
    /** 扫描设备中 */
    SCANNING,

    /** 连接 / 读取信息中 */
    CONNECTING,

    /** 已连接，版本核对完成，等待用户决定 */
    CONNECTED,

    /** 固件下载中 */
    DOWNLOADING,

    /** DFU 升级中 */
    UPGRADING,

    /** 升级完成 */
    DONE,

    /** 出错（可重试） */
    ERROR
}

data class UiState(
    val stage: Stage = Stage.SCANNING,
    val scanning: Boolean = false,
    val scanElapsedSec: Int = 0,
    val devices: List<M620Device> = emptyList(),
    val selectedDevice: M620Device? = null,
    val deviceVersion: String? = null,
    val timeSynced: Boolean = false,
    val latestFirmware: FirmwareInfo? = null,
    /** null = 尚未核对；true = 有新版本；false = 已是最新 */
    val hasUpdate: Boolean? = null,
    val serverReachable: Boolean = true,
    val downloadPercent: Int = 0,
    val dfuState: DfuState = DfuState.Idle,
    val firmwareFile: File? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = BleScanner(application)
    private val connection = DeviceConnection(application)
    private val repository = FirmwareRepository(application)
    private val dfuManager = DfuManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var elapsedJob: Job? = null
    private var autoConnected = false

    init {
        dfuManager.register()
        // 汇总扫描结果
        viewModelScope.launch {
            scanner.devices.collect { map ->
                _uiState.update { it.copy(devices = map.values.sortedByDescending { d -> d.rssi }) }
                // 仅发现一台设备时自动连接
                if (!autoConnected && _uiState.value.stage == Stage.SCANNING && map.size == 1) {
                    autoConnected = true
                    map.values.first().let { device -> connectTo(device) }
                }
            }
        }
        viewModelScope.launch {
            scanner.scanning.collect { s -> _uiState.update { it.copy(scanning = s) } }
        }
        // 汇总 DFU 状态
        viewModelScope.launch {
            dfuManager.state.collect { dfu ->
                _uiState.update { it.copy(dfuState = dfu) }
                when (dfu) {
                    is DfuState.Completed -> _uiState.update { it.copy(stage = Stage.DONE) }
                    is DfuState.Error -> _uiState.update {
                        it.copy(stage = Stage.ERROR, errorMessage = "升级失败：${dfu.message}")
                    }
                    is DfuState.Aborted -> _uiState.update {
                        it.copy(stage = Stage.ERROR, errorMessage = "升级已中止")
                    }
                    else -> Unit
                }
            }
        }
    }

    // ---------- 扫描 ----------

    fun startScan() {
        autoConnected = false
        scanner.startScan()
        _uiState.update { UiState() }
        elapsedJob?.cancel()
        elapsedJob = viewModelScope.launch {
            var seconds = 0
            while (true) {
                delay(1000)
                seconds++
                _uiState.update { it.copy(scanElapsedSec = seconds) }
            }
        }
        // 5 分钟广播窗口结束自动停止扫描
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(5 * 60 * 1000)
            scanner.stopScan()
        }
    }

    fun stopScan() {
        scanner.stopScan()
        scanJob?.cancel()
        elapsedJob?.cancel()
    }

    // ---------- 连接与版本核对 ----------

    fun connectTo(device: M620Device) {
        autoConnected = true
        stopScan()
        _uiState.update {
            it.copy(stage = Stage.CONNECTING, selectedDevice = device, errorMessage = null)
        }
        viewModelScope.launch {
            if (device.mode == DeviceMode.DFU) {
                // 设备已在 DFU 模式：无需 GATT 连接，直接准备升级
                val lookup = repository.lookupLatestFirmware(null)
                _uiState.update {
                    it.copy(
                        stage = Stage.CONNECTED,
                        deviceVersion = null,
                        latestFirmware = lookup.latest,
                        serverReachable = lookup.serverReachable,
                        hasUpdate = true // 固件异常，必须升级
                    )
                }
                return@launch
            }

            runCatching { connection.connectAndInspect(device.address) }
                .onSuccess { info ->
                    // 优先使用广播名中的版本号，GATT 读取作为兜底
                    val deviceVersion = device.broadcastVersion ?: info.firmwareVersion
                    val lookup = repository.lookupLatestFirmware(deviceVersion)
                    val needsUpdate = when {
                        lookup.latest != null ->
                            repository.isNewer(lookup.latest.version, deviceVersion)
                        else -> null // 服务器不可达，无法核对
                    }
                    _uiState.update {
                        it.copy(
                            stage = Stage.CONNECTED,
                            deviceVersion = deviceVersion,
                            timeSynced = info.timeSynced,
                            latestFirmware = lookup.latest,
                            serverReachable = lookup.serverReachable,
                            hasUpdate = needsUpdate
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            stage = Stage.ERROR,
                            errorMessage = "连接失败：${e.message ?: "设备无响应"}，请重新触发广播后重试"
                        )
                    }
                }
        }
    }

    // ---------- 下载与升级 ----------

    /** 用户确认升级：先下载固件，成功后自动进入 DFU。 */
    fun confirmUpgrade() {
        val state = _uiState.value
        val info = state.latestFirmware ?: return
        val device = state.selectedDevice ?: return
        _uiState.update { it.copy(stage = Stage.DOWNLOADING, downloadPercent = 0) }
        viewModelScope.launch {
            when (val result = repository.download(info) { percent ->
                _uiState.update { it.copy(downloadPercent = percent) }
            }) {
                is DownloadResult.Success -> {
                    _uiState.update { it.copy(firmwareFile = result.file) }
                    startDfu(device, result.file)
                }
                is DownloadResult.Failure -> _uiState.update {
                    it.copy(stage = Stage.ERROR, errorMessage = result.reason)
                }
            }
        }
    }

    /** 用户选择本地 zip 升级包（服务器不可达时的备选方案）。 */
    fun onLocalFirmwareSelected(uri: Uri) {
        val context = getApplication<Application>()
        val device = _uiState.value.selectedDevice ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(stage = Stage.DOWNLOADING, downloadPercent = -1) }
            val target = File(context.filesDir, "firmware/local_${System.currentTimeMillis()}.zip")
            runCatching {
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取文件")
            }.onSuccess {
                _uiState.update { it.copy(firmwareFile = target) }
                startDfu(device, target)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(stage = Stage.ERROR, errorMessage = "读取升级包失败：${e.message}")
                }
            }
        }
    }

    private fun startDfu(device: M620Device, file: File) {
        _uiState.update { it.copy(stage = Stage.UPGRADING) }
        dfuManager.startDfu(device.address, device.name, file.absolutePath)
    }

    /** 失败重试：已有固件包则直接重试 DFU，否则回到已连接界面重新下载。 */
    fun retry() {
        val state = _uiState.value
        val file = state.firmwareFile
        val device = state.selectedDevice
        dfuManager.reset()
        if (file != null && file.exists() && device != null) {
            startDfu(device, file)
        } else if (device != null) {
            _uiState.update { it.copy(stage = Stage.CONNECTED, errorMessage = null) }
        } else {
            restart()
        }
    }

    /** 重新开始（回到扫描界面）。 */
    fun restart() {
        dfuManager.reset()
        startScan()
    }

    override fun onCleared() {
        dfuManager.unregister()
        scanner.stopScan()
        super.onCleared()
    }
}
