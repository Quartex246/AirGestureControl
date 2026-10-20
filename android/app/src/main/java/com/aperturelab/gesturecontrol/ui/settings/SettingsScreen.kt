package com.aperturelab.gesturecontrol.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aperturelab.gesturecontrol.domain.label
import com.aperturelab.gesturecontrol.launcher.LaunchedApp

@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit,
    onOpenMapping: (String?) -> Unit,
    onOpenTuning: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("手势控制", style = MaterialTheme.typography.headlineSmall)
        StatusCard(
            state = state.activation,
            paused = state.paused,
            armed = state.armed,
            enabledCount = state.enabledPackages.size,
        )
        PermissionSummary(state.permissions, onOpen = onOpenPermissions)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("启动手势服务", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (state.armed) "服务运行中，切换应用时自动开关相机" else "关闭后不识别任何手势",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.armed,
                onCheckedChange = { enabled -> if (enabled) viewModel.start() else viewModel.stop() },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("暂停识别", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "暂停状态在切换应用后仍然保持",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.paused,
                onCheckedChange = viewModel::setPaused,
            )
        }

        HorizontalDivider()

        SectionTitle("已启用的应用")
        val enabled = state.enabledApps()
        if (enabled.isEmpty()) {
            Text(
                text = "还没有启用应用。桌面默认不启用，需要手势操作时再逐个开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            enabled.forEach { app ->
                AppRow(
                    app = app,
                    checked = true,
                    onCheckedChange = { viewModel.setEnabled(app.packageName, it) },
                    subtitle = mappingSummary(state, app),
                    onClick = { onOpenMapping(app.packageName) },
                )
            }
        }

        HorizontalDivider()

        SectionTitle("添加应用")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索应用") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        )
        val candidates = state.apps.filter {
            it.packageName !in state.enabledPackages &&
                (query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true))
        }
        candidates.take(MAX_CANDIDATES).forEach { app ->
            AppRow(
                app = app,
                checked = false,
                onCheckedChange = { viewModel.setEnabled(app.packageName, it) },
            )
        }
        if (candidates.isEmpty()) {
            Text(
                text = "没有匹配的应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        HorizontalDivider()

        SectionTitle("手势映射与调参")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { onOpenMapping(null) }) { Text("全局手势映射") }
            TextButton(onClick = onOpenTuning) { Text("识别灵敏度") }
        }
        Text(
            text = "每个应用都可以单独覆盖手势；未覆盖的沿用全局默认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

private fun mappingSummary(state: SettingsUiState, app: LaunchedApp): String {
    val overrides = state.overrides[app.packageName]?.actions.orEmpty()
    if (overrides.isEmpty()) return "使用全局默认"
    if (overrides.size == 1) {
        val (event, action) = overrides.entries.first()
        return "覆盖 1 项 · ${event.label()}→${action.label()}"
    }
    return "覆盖 ${overrides.size} 项"
}

private const val MAX_CANDIDATES = 60
