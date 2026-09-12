package com.huixiangtel.m620ota

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huixiangtel.m620ota.ui.Actions
import com.huixiangtel.m620ota.ui.M620App
import com.huixiangtel.m620ota.ui.M620Theme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            M620Theme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // 本地升级包选择器
                val filePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri -> uri?.let(viewModel::onLocalFirmwareSelected) }

                // 运行时权限
                var permissionsGranted by remember { mutableStateOf(hasBlePermissions()) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    permissionsGranted = result.values.all { it }
                    if (permissionsGranted) viewModel.startScan()
                }

                LaunchedEffect(Unit) {
                    if (permissionsGranted) {
                        viewModel.startScan()
                    } else {
                        permissionLauncher.launch(requiredPermissions())
                    }
                }

                M620App(
                    state = state,
                    actions = Actions(
                        onStartScan = {
                            if (permissionsGranted) viewModel.startScan()
                            else permissionLauncher.launch(requiredPermissions())
                        },
                        onSelectDevice = viewModel::connectTo,
                        onConfirmUpgrade = viewModel::confirmUpgrade,
                        onPickLocalFile = { filePicker.launch("application/zip") },
                        onRetry = viewModel::retry,
                        onRestart = viewModel::restart
                    )
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 退出界面时停止扫描，DFU 服务仍在后台继续
        viewModel.stopScan()
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    private fun hasBlePermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
