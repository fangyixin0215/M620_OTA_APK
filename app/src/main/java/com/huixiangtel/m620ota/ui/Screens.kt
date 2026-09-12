package com.huixiangtel.m620ota.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huixiangtel.m620ota.Stage
import com.huixiangtel.m620ota.UiState
import com.huixiangtel.m620ota.ble.DeviceMode
import com.huixiangtel.m620ota.ble.M620Device
import com.huixiangtel.m620ota.ota.DfuState

/** 界面回调集合。 */
class Actions(
    val onStartScan: () -> Unit,
    val onSelectDevice: (M620Device) -> Unit,
    val onConfirmUpgrade: () -> Unit,
    val onPickLocalFile: () -> Unit,
    val onRetry: () -> Unit,
    val onRestart: () -> Unit
)

@Composable
fun M620App(state: UiState, actions: Actions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Header()
        Box(modifier = Modifier.weight(1f)) {
            when (state.stage) {
                Stage.SCANNING -> ScanScreen(state, actions)
                Stage.CONNECTING -> ConnectingScreen(state)
                Stage.CONNECTED -> ConnectedScreen(state, actions)
                Stage.DOWNLOADING -> DownloadScreen(state)
                Stage.UPGRADING -> UpgradingScreen(state)
                Stage.DONE -> DoneScreen(actions)
                Stage.ERROR -> ErrorScreen(state, actions)
            }
        }
    }
}

// ---------------- 顶部品牌区 ----------------

@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(M620Colors.HeaderBrush)
            .padding(top = 44.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "M620 固件升级",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "Nordic DFU · 蓝牙 OTA 升级工具",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ---------------- 扫描界面 ----------------

@Composable
private fun ScanScreen(state: UiState, actions: Actions) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 扫描状态卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingBluetoothIcon()
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (state.scanning) "正在扫描设备…" else "扫描已停止",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "已扫描 ${state.scanElapsedSec} 秒 · 发现 ${state.devices.size} 台设备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // 设备列表
        if (state.devices.size > 1) {
            Text(
                "发现多台设备，请选择要连接的设备",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.devices) { device ->
                DeviceCard(device) { actions.onSelectDevice(device) }
            }
        }

        // 操作引导
        GuideCard(
            icon = Icons.Default.BluetoothSearching,
            title = "如何触发设备广播？",
            lines = listOf(
                "1. 将 M620 连接充电器，充电瞬间设备自动开启蓝牙广播",
                "2. 广播仅持续约 5 分钟，请尽快完成连接",
                "3. 若超时未搜到设备，请重新插拔充电后再次扫描"
            ),
            tint = M620Colors.Blue600
        )

        if (!state.scanning) {
            Button(
                onClick = actions.onStartScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重新扫描", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PulsingBluetoothIcon() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .scale(scale)
                .alpha(alpha)
                .clip(CircleShape)
                .background(M620Colors.Blue600)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(M620Colors.HeaderBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun DeviceCard(device: M620Device, onClick: () -> Unit) {
    val isDfu = device.mode == DeviceMode.DFU
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDfu) M620Colors.Red500.copy(alpha = 0.12f)
                        else M620Colors.Green500.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isDfu) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isDfu) M620Colors.Red500 else M620Colors.Green500
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(device.address)
                        device.broadcastVersion?.let { append(" · V$it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            StatusChip(
                text = if (isDfu) "待升级" else "正常",
                color = if (isDfu) M620Colors.Red500 else M620Colors.Green500
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- 连接中 ----------------

@Composable
private fun ConnectingScreen(state: UiState) {
    CenteredColumn {
        CircularProgressIndicator(color = M620Colors.Blue600)
        Spacer(Modifier.height(20.dp))
        Text("正在连接 ${state.selectedDevice?.name ?: ""}…",
            style = MaterialTheme.typography.titleMedium)
        Text(
            "连接后将自动同步手机时间并核对固件版本",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

// ---------------- 已连接 / 版本核对 ----------------

@Composable
private fun ConnectedScreen(state: UiState, actions: Actions) {
    val device = state.selectedDevice
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("已连接设备", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        InfoRow("设备名称", device?.name ?: "-")
                        InfoRow("MAC 地址", device?.address ?: "-")
                        InfoRow(
                            "当前固件版本",
                            if (device?.mode == DeviceMode.DFU) "固件异常（DFU 模式）"
                            else state.deviceVersion?.let { "V$it" } ?: "读取失败"
                        )
                        InfoRow(
                            "时间同步",
                            if (device?.mode == DeviceMode.DFU) "已跳过"
                            else if (state.timeSynced) "已同步手机时间 ✓"
                            else "设备未响应（不影响升级）"
                        )
                        InfoRow(
                            "最新固件版本",
                            state.latestFirmware?.let { "V${it.version}" }
                                ?: if (state.serverReachable) "查询中…" else "服务器不可达"
                        )
                    }
                }
            }

            item {
                when (state.hasUpdate) {
                    false -> GuideCard(
                        icon = Icons.Default.CheckCircle,
                        title = "目前版本已是最新",
                        lines = listOf("当前设备固件已为最新版本，无需升级。"),
                        tint = M620Colors.Green500
                    )
                    true -> GuideCard(
                        icon = Icons.Default.SystemUpdateAlt,
                        title = "发现可升级版本 V${state.latestFirmware?.version ?: ""}",
                        lines = listOfNotNull(
                            state.latestFirmware?.notes?.takeIf { it.isNotBlank() },
                            "升级过程约需 1-2 分钟，期间请保持设备充电与蓝牙连接。"
                        ),
                        tint = M620Colors.Amber500
                    )
                    null -> GuideCard(
                        icon = Icons.Default.ErrorOutline,
                        title = "无法核对版本",
                        lines = listOf("固件服务器暂不可达，可选择本地升级包进行升级。"),
                        tint = M620Colors.Red500
                    )
                }
            }
        }

        // 底部操作区
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.hasUpdate == true && state.latestFirmware != null) {
                Button(
                    onClick = actions.onConfirmUpgrade,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = M620Colors.Blue600)
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("立即升级", style = MaterialTheme.typography.labelLarge)
                }
            }
            OutlinedButton(
                onClick = actions.onPickLocalFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择本地升级包 (.zip)")
            }
            Text(
                "取消并重新扫描",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions.onRestart)
                    .padding(8.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------- 下载中 ----------------

@Composable
private fun DownloadScreen(state: UiState) {
    CenteredColumn {
        Text("正在下载固件…", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            state.latestFirmware?.fileName ?: "本地升级包",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(24.dp))
        if (state.downloadPercent >= 0) {
            LinearProgressIndicator(
                progress = { state.downloadPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = M620Colors.Blue600,
            )
            Spacer(Modifier.height(10.dp))
            Text("${state.downloadPercent}%", fontWeight = FontWeight.Bold)
        } else {
            CircularProgressIndicator(color = M620Colors.Blue600)
            Spacer(Modifier.height(10.dp))
            Text("正在读取升级包…")
        }
    }
}

// ---------------- 升级中 ----------------

@Composable
private fun UpgradingScreen(state: UiState) {
    val dfu = state.dfuState
    val percent = (dfu as? DfuState.Transferring)?.percent ?: 0

    CenteredColumn {
        Text("固件升级中", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.size(180.dp),
                strokeWidth = 12.dp,
                color = M620Colors.Blue600,
                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$percent%",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = M620Colors.Blue600
                )
                if (dfu is DfuState.Transferring) {
                    Text(
                        "%.1f KB/s".format(dfu.speed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            when (dfu) {
                is DfuState.Connecting -> "正在连接设备…"
                is DfuState.EnablingDfuMode -> "正在切换至 DFU 模式…"
                is DfuState.Validating -> "正在校验固件…"
                is DfuState.Transferring -> "正在传输固件，请勿断开连接…"
                else -> "准备升级…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))
        GuideCard(
            icon = Icons.Default.ErrorOutline,
            title = "升级注意事项",
            lines = listOf(
                "请保持设备处于充电状态",
                "请勿远离设备或关闭 APP",
                "升级完成后设备将自动重启"
            ),
            tint = M620Colors.Amber500
        )
    }
}

// ---------------- 完成 / 失败 ----------------

@Composable
private fun DoneScreen(actions: Actions) {
    CenteredColumn {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = M620Colors.Green500,
            modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("升级完成！", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "设备已重启并运行最新固件",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = actions.onRestart,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Text("完成", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ErrorScreen(state: UiState, actions: Actions) {
    CenteredColumn {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = M620Colors.Red500,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("操作未完成", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            state.errorMessage ?: "发生未知错误",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "提示：蓝牙中断时请重新插拔充电触发广播后重试",
            style = MaterialTheme.typography.bodyMedium,
            color = M620Colors.Amber500,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = actions.onRetry,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = M620Colors.Blue600)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重试")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = actions.onRestart,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("重新扫描")
        }
    }
}

// ---------------- 通用组件 ----------------

@Composable
private fun GuideCard(
    icon: ImageVector,
    title: String,
    lines: List<String>,
    tint: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.07f))
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
                Spacer(Modifier.height(6.dp))
                lines.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}
