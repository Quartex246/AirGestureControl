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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aperturelab.gesturecontrol.domain.GestureConfig

@Composable
fun TuningScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var draft by remember(state.config) { mutableStateOf(state.config) }

    fun commit(next: GestureConfig) {
        draft = next.clamped()
        viewModel.setConfig(draft)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("识别灵敏度", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "修改立即生效，下一次挥手就使用新的参数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("滑动")
        TuningSlider(
            title = "触发距离",
            hint = "手掌需要移动多大幅度才判定为一次滑动",
            value = draft.swipeThresholdRatio,
            range = GestureConfig.MIN_SWIPE_THRESHOLD..GestureConfig.MAX_SWIPE_THRESHOLD,
            display = "%.2f".format(draft.swipeThresholdRatio),
            onChange = { draft = draft.copy(swipeThresholdRatio = it) },
            onCommit = { commit(draft) },
        )
        TuningSlider(
            title = "轨迹一致性",
            hint = "越高越容易拒绝抖动和绕圈的手势",
            value = draft.consistencyRatio,
            range = GestureConfig.MIN_CONSISTENCY..GestureConfig.MAX_CONSISTENCY,
            display = "%.2f".format(draft.consistencyRatio),
            onChange = { draft = draft.copy(consistencyRatio = it) },
            onCommit = { commit(draft) },
        )
        TuningSlider(
            title = "冷却时间",
            hint = "两次滑动之间的最短间隔",
            value = draft.swipeCooldownMs.toFloat(),
            range = GestureConfig.MIN_COOLDOWN_MS.toFloat()..GestureConfig.MAX_COOLDOWN_MS.toFloat(),
            display = "${draft.swipeCooldownMs} ms",
            onChange = { draft = draft.copy(swipeCooldownMs = it.toLong()) },
            onCommit = { commit(draft) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("握拳回主页")
        TuningSlider(
            title = "握拳置信度",
            hint = "分类器需要多确信才算握拳",
            value = draft.fistConfidence,
            range = GestureConfig.MIN_FIST_CONFIDENCE..GestureConfig.MAX_FIST_CONFIDENCE,
            display = "%.2f".format(draft.fistConfidence),
            onChange = { draft = draft.copy(fistConfidence = it) },
            onCommit = { commit(draft) },
        )
        TuningSlider(
            title = "保持时长",
            hint = "握拳后需要静止多久才触发",
            value = draft.fistDwellMs.toFloat(),
            range = GestureConfig.MIN_DWELL_MS.toFloat()..GestureConfig.MAX_DWELL_MS.toFloat(),
            display = "${draft.fistDwellMs} ms",
            onChange = { draft = draft.copy(fistDwellMs = it.toLong()) },
            onCommit = { commit(draft) },
        )
        TuningSlider(
            title = "静止容差",
            hint = "保持期间允许的手掌漂移，越小越严格",
            value = draft.stillnessTolerance,
            range = GestureConfig.MIN_STILLNESS..GestureConfig.MAX_STILLNESS,
            display = "%.3f".format(draft.stillnessTolerance),
            onChange = { draft = draft.copy(stillnessTolerance = it) },
            onCommit = { commit(draft) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("方向")
        ToggleRow(
            title = "反转上下滑动",
            subtitle = "前置相机的镜像方向与习惯不一致时使用",
            checked = draft.invertVertical,
            onCheckedChange = { commit(draft.copy(invertVertical = it)) },
        )
        ToggleRow(
            title = "反转左右滑动",
            subtitle = "左右方向与实际手部动作相反时使用",
            checked = draft.invertHorizontal,
            onCheckedChange = { commit(draft.copy(invertHorizontal = it)) },
        )

        TextButton(onClick = { commit(GestureConfig()) }) { Text("恢复默认参数") }
    }
}

@Composable
private fun TuningSlider(
    title: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = range,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
