package com.huixiangtel.m620ota.ota

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.dfu.DfuProgressListener
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper

/** DFU 升级状态。 */
sealed class DfuState {
    object Idle : DfuState()
    object Connecting : DfuState()
    object EnablingDfuMode : DfuState()
    object Validating : DfuState()
    data class Transferring(val percent: Int, val speed: Float) : DfuState()
    object Completed : DfuState()
    data class Error(val message: String) : DfuState()
    object Aborted : DfuState()
}

/**
 * 封装 Nordic DFU Library，向 ViewModel 暴露 StateFlow 形式的升级状态。
 */
class DfuManager(private val context: Context) {

    private val _state = MutableStateFlow<DfuState>(DfuState.Idle)
    val state: StateFlow<DfuState> = _state.asStateFlow()

    private var controller: DfuServiceController? = null

    private val progressListener: DfuProgressListener = object : DfuProgressListenerAdapter() {
        override fun onDeviceConnecting(deviceAddress: String) {
            _state.value = DfuState.Connecting
        }

        override fun onEnablingDfuMode(deviceAddress: String) {
            _state.value = DfuState.EnablingDfuMode
        }

        override fun onFirmwareValidating(deviceAddress: String) {
            _state.value = DfuState.Validating
        }

        override fun onProgressChanged(
            deviceAddress: String,
            percent: Int,
            speed: Float,
            avgSpeed: Float,
            currentPart: Int,
            partsTotal: Int
        ) {
            _state.value = DfuState.Transferring(percent, avgSpeed)
        }

        override fun onDfuCompleted(deviceAddress: String) {
            _state.value = DfuState.Completed
        }

        override fun onDfuAborted(deviceAddress: String) {
            _state.value = DfuState.Aborted
        }

        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String) {
            _state.value = DfuState.Error(message)
        }
    }

    fun register() {
        DfuServiceListenerHelper.registerProgressListener(context, progressListener)
    }

    fun unregister() {
        DfuServiceListenerHelper.unregisterProgressListener(context, progressListener)
    }

    /** 启动 DFU 升级。filePath 为 Nordic DFU 标准 zip 包路径。 */
    fun startDfu(deviceAddress: String, deviceName: String?, filePath: String) {
        _state.value = DfuState.Connecting
        DfuServiceInitiator.createDfuNotificationChannel(context)
        controller = DfuServiceInitiator(deviceAddress)
            .setDeviceName(deviceName)
            .setZip(filePath)
            .setKeepBond(false)
            .setForeground(true)
            .setDisableNotification(false)
            .setNumberOfRetries(3)
            .setRebootTime(3000L)
            .start(context, M620DfuService::class.java)
    }

    fun abort() {
        controller?.abort()
    }

    fun reset() {
        _state.value = DfuState.Idle
    }
}
