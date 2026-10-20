package com.aperturelab.gesturecontrol.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aperturelab.gesturecontrol.domain.GestureAction
import com.aperturelab.gesturecontrol.domain.GestureEvent
import com.aperturelab.gesturecontrol.domain.GestureMapping
import com.aperturelab.gesturecontrol.domain.label
import com.aperturelab.gesturecontrol.domain.shortLabel

@Composable
fun MappingScreen(
    packageName: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isGlobal = packageName == null
    val title = if (isGlobal) "全局手势映射" else state.labelOf(packageName!!)
    val overrides = packageName?.let { state.overrides[it]?.actions.orEmpty() }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }

        Text(
            text = if (isGlobal) {
                "应用于所有没有单独覆盖的应用。"
            } else {
                "未覆盖的手势沿用全局默认；已覆盖的只在当前应用生效。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(if (isGlobal) "手势动作" else "覆盖与继承")

        GestureEvent.entries.forEach { event ->
            val effective = overrides[event]
                ?: state.defaultMapping[event]
                ?: GestureAction.NONE
            GestureRow(
                event = event,
                action = effective,
                inherited = !isGlobal && overrides[event] == null,
                onSelect = { action ->
                    if (isGlobal) viewModel.setDefaultAction(event, action)
                    else viewModel.setOverrideAction(packageName!!, event, action)
                },
                onReset = if (!isGlobal && overrides[event] != null) {
                    { viewModel.clearOverrides(packageName!!) }
                } else {
                    null
                },
            )
        }

        HorizontalDivider()

        SectionTitle(if (isGlobal) "恢复默认全局映射" else "应用预设")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isGlobal) {
                AssistChip(onClick = { PRESETS_DEFAULT.forEach { (e, a) -> viewModel.setDefaultAction(e, a) } }, label = { Text("恢复默认") })
            } else {
                PRESETS.forEach { (name, mapping) ->
                    AssistChip(
                        onClick = { viewModel.applyPreset(packageName!!, mapping) },
                        label = { Text(name) },
                    )
                }
            }
        }
        if (!isGlobal) {
            TextButton(onClick = { viewModel.clearOverrides(packageName!!) }) {
                Text("清除该应用的覆盖，全部继承全局")
            }
        }
    }
}

@Composable
private fun GestureRow(
    event: GestureEvent,
    action: GestureAction,
    inherited: Boolean,
    onSelect: (GestureAction) -> Unit,
    onReset: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.label(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (inherited) "继承全局 · ${action.label()}" else action.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                TextButton(onClick = { expanded = true }) { Text(action.shortLabel()) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    GestureAction.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label()) },
                            onClick = {
                                expanded = false
                                onSelect(candidate)
                            },
                        )
                    }
                }
            }
            if (onReset != null) {
                TextButton(onClick = onReset) { Text("重置") }
            }
        }
    }
}

private val PRESETS_DEFAULT: Map<GestureEvent, GestureAction> = mapOf(
    GestureEvent.SWIPE_UP to GestureAction.SCROLL_FORWARD,
    GestureEvent.SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
    GestureEvent.SWIPE_LEFT to GestureAction.BACK,
    GestureEvent.SWIPE_RIGHT to GestureAction.BACK,
    GestureEvent.FIST_HOLD to GestureAction.HOME,
)

private val PRESETS: List<Pair<String, GestureMapping>> = listOf(
    "默认" to GestureMapping(PRESETS_DEFAULT),
    "横向翻页" to GestureMapping(
        mapOf(
            GestureEvent.SWIPE_UP to GestureAction.SCROLL_FORWARD,
            GestureEvent.SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
            GestureEvent.SWIPE_LEFT to GestureAction.PAGE_BACKWARD,
            GestureEvent.SWIPE_RIGHT to GestureAction.PAGE_FORWARD,
            GestureEvent.FIST_HOLD to GestureAction.HOME,
        ),
    ),
    "仅滚动" to GestureMapping(
        mapOf(
            GestureEvent.SWIPE_UP to GestureAction.SCROLL_FORWARD,
            GestureEvent.SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
            GestureEvent.SWIPE_LEFT to GestureAction.NONE,
            GestureEvent.SWIPE_RIGHT to GestureAction.NONE,
            GestureEvent.FIST_HOLD to GestureAction.NONE,
        ),
    ),
    "全部关闭" to GestureMapping(
        GestureEvent.entries.associateWith { GestureAction.NONE },
    ),
)
