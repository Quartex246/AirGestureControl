package com.aperturelab.gesturecontrol.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aperturelab.gesturecontrol.runtime.PermissionChecks
import com.aperturelab.gesturecontrol.runtime.PermissionItem
import com.aperturelab.gesturecontrol.runtime.PermissionStatus

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("权限设置", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = if (state.permissions.ready) {
                "全部权限已就绪。"
            } else {
                "还需要授权 ${state.permissions.missing.size} 项，授权后自动刷新。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            title = "相机",
            description = "前置摄像头识别手势所必需。",
            granted = state.permissions.camera,
            onGrant = { requestCamera.launch(android.Manifest.permission.CAMERA) },
            onOpenSettings = { context.openSettings(PermissionItem.CAMERA) },
        )
        PermissionCard(
            title = "无障碍服务",
            description = "用于判断当前应用并执行滚动、返回和回主页。",
            granted = state.permissions.accessibility,
            onGrant = { context.openSettings(PermissionItem.ACCESSIBILITY) },
            onOpenSettings = null,
        )
        PermissionCard(
            title = "悬浮窗",
            description = "显示识别状态徽章，点按可暂停。",
            granted = state.permissions.overlay,
            onGrant = { context.openSettings(PermissionItem.OVERLAY) },
            onOpenSettings = null,
        )
        PermissionCard(
            title = "通知",
            description = "前台服务保持运行所需的常驻通知。",
            granted = state.permissions.notifications,
            onGrant = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.openSettings(PermissionItem.NOTIFICATIONS)
                }
            },
            onOpenSettings = { context.openSettings(PermissionItem.NOTIFICATIONS) },
        )

        if (state.permissions.ready) {
            Text(
                text = "无障碍权限被系统收回后，这里的检查会自动变成未授权。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusDot(
                state = when {
                    granted -> com.aperturelab.gesturecontrol.runtime.ActivationState.ACTIVE
                    else -> com.aperturelab.gesturecontrol.runtime.ActivationState.UNAVAILABLE
                },
                paused = false,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (granted) "已授权" else description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                if (onOpenSettings != null) {
                    TextButton(onClick = onOpenSettings) { Text("管理") }
                }
            } else {
                Button(onClick = onGrant) { Text("授权") }
            }
        }
    }
}

private fun Context.openSettings(item: PermissionItem) {
    val intent = PermissionChecks.settingsIntent(this, item) ?: return
    runCatching { startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
