package com.aperturelab.gesturecontrol.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.aperturelab.gesturecontrol.launcher.LaunchedApp
import com.aperturelab.gesturecontrol.runtime.ActivationState
import com.aperturelab.gesturecontrol.runtime.PermissionItem
import com.aperturelab.gesturecontrol.runtime.PermissionStatus

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun AppRow(
    app: LaunchedApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(app)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (onClick != null) {
            androidx.compose.material3.TextButton(onClick = onClick) {
                Text("映射")
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AppIcon(app: LaunchedApp, modifier: Modifier = Modifier) {
    val bitmap = remember(app.packageName) {
        runCatching { app.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (bitmap == null) {
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    }
}

@Composable
fun StatusDot(state: ActivationState, paused: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        paused -> Color(0xFFFFC66D)
        state == ActivationState.ACTIVE -> Color(0xFF4CAF50)
        state == ActivationState.WARMING -> Color(0xFF7FB8FF)
        state == ActivationState.UNAVAILABLE -> Color(0xFFE57373)
        else -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatusCard(
    state: ActivationState,
    paused: Boolean,
    armed: Boolean,
    enabledCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(state = state, paused = paused)
                Text(
                    text = if (paused) "已暂停" else stateTitle(state),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stateDescription(state, armed, enabledCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun stateTitle(state: ActivationState): String = when (state) {
    ActivationState.ACTIVE -> "手势就绪"
    ActivationState.WARMING -> "正在准备相机"
    ActivationState.PAUSED -> "已暂停"
    ActivationState.UNAVAILABLE -> "相机不可用"
    ActivationState.INACTIVE_NOT_ENABLED -> "当前应用未启用"
}

private fun stateDescription(state: ActivationState, armed: Boolean, enabledCount: Int): String {
    if (!armed) return "服务未启动。开启下方开关后才会识别手势。"
    if (enabledCount == 0) return "还没有启用任何应用。从下方列表中选择需要手势操作的应用。"
    return when (state) {
        ActivationState.ACTIVE -> "识别进行中，手势会在已启用的应用中生效。"
        ActivationState.WARMING -> "相机已挂载，等待第一帧识别结果。"
        ActivationState.PAUSED -> "识别已暂停，点按悬浮徽章可恢复。"
        ActivationState.UNAVAILABLE -> "相机被其它应用占用或权限缺失，正在自动重试。"
        ActivationState.INACTIVE_NOT_ENABLED -> "切换到已启用的应用后会自动开始识别。"
    }
}

@Composable
fun PermissionSummary(status: PermissionStatus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (status.ready) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "还缺少 ${status.missing.size} 项权限",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = status.missing.joinToString("、") { permissionName(it) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            androidx.compose.material3.TextButton(onClick = onOpen) { Text("去设置") }
        }
    }
}

fun permissionName(item: PermissionItem): String = when (item) {
    PermissionItem.CAMERA -> "相机"
    PermissionItem.ACCESSIBILITY -> "无障碍服务"
    PermissionItem.OVERLAY -> "悬浮窗"
    PermissionItem.NOTIFICATIONS -> "通知"
}
