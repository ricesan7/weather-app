package com.aielectronics.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aielectronics.core.model.TestSpec
import com.aielectronics.core.model.UiPage
import com.aielectronics.core.model.UiSpec
import com.aielectronics.core.model.UiWidget
import com.aielectronics.runtime.RuntimeTransport
import java.util.Locale
import kotlin.math.round

@Composable
fun RuntimeControlDashboard(
    transport: RuntimeTransport,
    uiSpec: UiSpec,
    tests: List<TestSpec>,
    modifier: Modifier = Modifier,
) {
    val factory = remember(transport, uiSpec, tests) {
        RuntimeDashboardViewModel.Factory(
            transport = transport,
            uiSpec = uiSpec,
            tests = tests,
        )
    }
    val dashboardViewModel: RuntimeDashboardViewModel = viewModel(
        key = "runtime-dashboard-" + uiSpec.hashCode(),
        factory = factory,
    )
    val state by dashboardViewModel.state.collectAsState()

    DynamicControlDashboard(
        uiSpec = uiSpec,
        tests = tests,
        state = state,
        onRefresh = dashboardViewModel::refresh,
        onSetSetting = dashboardViewModel::setSetting,
        onRunTest = dashboardViewModel::runTest,
        onDismissError = dashboardViewModel::clearError,
        modifier = modifier,
    )
}

@Composable
fun DynamicControlDashboard(
    uiSpec: UiSpec,
    tests: List<TestSpec>,
    state: ControlDashboardState,
    onRefresh: () -> Unit,
    onSetSetting: (String, String) -> Unit,
    onRunTest: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = remember(uiSpec, tests) {
        DashboardProjection.pages(uiSpec, tests)
    }
    var selectedIndex by remember(pages) { mutableIntStateOf(0) }
    val selectedPage = pages.getOrNull(selectedIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionHeader(
            state = state,
            onRefresh = onRefresh,
        )

        state.lastError?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = onDismissError,
            )
        }

        if (pages.isNotEmpty()) {
            PageSelector(
                pages = pages,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
            )
        }

        selectedPage?.let { page ->
            PageContent(
                page = page,
                state = state,
                onSetSetting = onSetSetting,
                onRunTest = onRunTest,
            )
        }
    }
}

@Composable
private fun ConnectionHeader(
    state: ControlDashboardState,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "装置",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = when (state.connection) {
                        DeviceConnectionState.CONNECTED -> "接続中"
                        DeviceConnectionState.DISCONNECTED -> "未接続"
                        DeviceConnectionState.ERROR -> "通信エラー"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.refreshing,
            ) {
                Text(if (state.refreshing) "更新中…" else "更新")
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    }
}

@Composable
private fun PageSelector(
    pages: List<UiPage>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pages.forEachIndexed { index, page ->
            if (selectedIndex == index) {
                Button(onClick = { onSelect(index) }) {
                    Text(page.title)
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }) {
                    Text(page.title)
                }
            }
        }
    }
}

@Composable
private fun PageContent(
    page: UiPage,
    state: ControlDashboardState,
    onSetSetting: (String, String) -> Unit,
    onRunTest: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = page.widgets,
            key = { it.id },
        ) { widget ->
            WidgetRenderer(
                widget = widget,
                state = state,
                onSetSetting = onSetSetting,
                onRunTest = onRunTest,
            )
        }
    }
}

@Composable
private fun WidgetRenderer(
    widget: UiWidget,
    state: ControlDashboardState,
    onSetSetting: (String, String) -> Unit,
    onRunTest: (String) -> Unit,
) {
    when (widget) {
        is UiWidget.ValueCard -> ValueCardWidget(widget, state)
        is UiWidget.Gauge -> GaugeWidget(widget, state)
        is UiWidget.LineChart -> LineChartWidget(widget, state)
        is UiWidget.Toggle -> ToggleWidget(widget, state, onSetSetting)
        is UiWidget.Slider -> SliderWidget(widget, state, onSetSetting)
        is UiWidget.Select -> SelectWidget(widget, state, onSetSetting)
        is UiWidget.Button -> ActionButtonWidget(widget, state, onRunTest)
        is UiWidget.Status -> StatusWidget(widget, state)
        is UiWidget.Alarm -> AlarmWidget(widget, state)
    }
}

@Composable
private fun ValueCardWidget(
    widget: UiWidget.ValueCard,
    state: ControlDashboardState,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                WidgetText.label(widget),
                style = MaterialTheme.typography.labelMedium,
            )
            val value = state.value(widget.binding) ?: "—"
            Text(
                text = value + (widget.unit?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun GaugeWidget(
    widget: UiWidget.Gauge,
    state: ControlDashboardState,
) {
    val value = state.value(widget.binding)?.toDoubleOrNull()
    val fraction = value
        ?.let { ((it - widget.min) / (widget.max - widget.min)).coerceIn(0.0, 1.0) }
        ?.toFloat()
        ?: 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(WidgetText.label(widget))
            Text(
                value?.let(::formatNumber) ?: "—",
                style = MaterialTheme.typography.titleLarge,
            )
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LineChartWidget(
    widget: UiWidget.LineChart,
    state: ControlDashboardState,
) {
    val samples = state.history(widget.binding)
    val lineColor = MaterialTheme.colorScheme.primary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                WidgetText.label(widget) + "履歴",
                style = MaterialTheme.typography.titleMedium,
            )

            if (samples.size < 2) {
                Text("データ収集中")
            } else {
                val min = samples.minOf { it.value }
                val max = samples.maxOf { it.value }
                val range = (max - min).takeIf { it > 0.0 } ?: 1.0

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                ) {
                    val denominator = (samples.size - 1).coerceAtLeast(1)
                    samples.zipWithNext().forEachIndexed { index, pair ->
                        val first = pair.first
                        val second = pair.second

                        val x1 = size.width * index / denominator
                        val x2 = size.width * (index + 1) / denominator
                        val y1 = size.height * (1f - ((first.value - min) / range).toFloat())
                        val y2 = size.height * (1f - ((second.value - min) / range).toFloat())

                        drawLine(
                            color = lineColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 4f,
                        )
                    }
                }
                Text(
                    text = formatNumber(samples.last().value),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ToggleWidget(
    widget: UiWidget.Toggle,
    state: ControlDashboardState,
    onSetSetting: (String, String) -> Unit,
) {
    val settingId = settingId(widget.binding)
    val raw = state.value(widget.binding)
    val checked = raw.equals("true", ignoreCase = true) ||
        raw.equals("on", ignoreCase = true) ||
        raw == "1"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(WidgetText.label(widget))
            Switch(
                checked = checked,
                enabled = settingId !in state.pendingSettingIds,
                onCheckedChange = { enabled ->
                    onSetSetting(settingId, enabled.toString())
                },
            )
        }
    }
}

@Composable
private fun SliderWidget(
    widget: UiWidget.Slider,
    state: ControlDashboardState,
    onSetSetting: (String, String) -> Unit,
) {
    val settingId = settingId(widget.binding)
    val runtimeValue = state.value(widget.binding)
        ?.toDoubleOrNull()
        ?.coerceIn(widget.min, widget.max)
        ?: widget.min

    var localValue by remember(widget.id, runtimeValue) {
        mutableDoubleStateOf(runtimeValue)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(WidgetText.label(widget))
                Text(formatNumber(localValue))
            }
            Slider(
                value = localValue.toFloat(),
                onValueChange = { raw ->
                    localValue = quantize(
                        value = raw.toDouble(),
                        min = widget.min,
                        max = widget.max,
                        step = widget.step,
                    )
                },
                onValueChangeFinished = {
                    onSetSetting(
                        settingId,
                        formatNumber(localValue),
                    )
                },
                valueRange = widget.min.toFloat()..widget.max.toFloat(),
                enabled = settingId !in state.pendingSettingIds,
            )
        }
    }
}

@Composable
private fun SelectWidget(
    widget: UiWidget.Select,
    state: ControlDashboardState,
    onSetSetting: (String, String) -> Unit,
) {
    val settingId = settingId(widget.binding)
    var expanded by remember(widget.id) { mutableStateOf(false) }
    val selected = state.value(widget.binding)
        ?: widget.options.firstOrNull()
        ?: "—"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(WidgetText.label(widget))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled =
                        widget.options.isNotEmpty() &&
                            settingId !in state.pendingSettingIds,
                ) {
                    Text(selected)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    widget.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                expanded = false
                                onSetSetting(settingId, option)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtonWidget(
    widget: UiWidget.Button,
    state: ControlDashboardState,
    onRunTest: (String) -> Unit,
) {
    val testId = widget.binding
        .takeIf { it.startsWith("tests.") }
        ?.removePrefix("tests.")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (testId != null) {
                        onRunTest(testId)
                    }
                },
                enabled =
                    testId != null &&
                        state.testResults[testId] != TestRunState.RUNNING,
            ) {
                Text(widget.label)
            }

            testId?.let { id ->
                HorizontalDivider()
                Text(
                    when (state.testResults[id] ?: TestRunState.IDLE) {
                        TestRunState.IDLE -> "未実行"
                        TestRunState.RUNNING -> "確認中…"
                        TestRunState.PASSED -> "正常"
                        TestRunState.FAILED -> "要確認"
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusWidget(
    widget: UiWidget.Status,
    state: ControlDashboardState,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(WidgetText.label(widget))
            Text(
                state.value(widget.binding) ?: "—",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AlarmWidget(
    widget: UiWidget.Alarm,
    state: ControlDashboardState,
) {
    val value = state.value(widget.binding)
    if (
        value.isNullOrBlank() ||
        value == "false" ||
        value == "0" ||
        value.equals("none", ignoreCase = true)
    ) {
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                WidgetText.label(widget),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(value)
        }
    }
}

private fun settingId(binding: String): String =
    binding
        .removePrefix("settings.")
        .removePrefix("controls.")

private fun quantize(
    value: Double,
    min: Double,
    max: Double,
    step: Double,
): Double {
    if (step <= 0.0) return value.coerceIn(min, max)
    val steps = round((value - min) / step)
    return (min + steps * step).coerceIn(min, max)
}

private fun formatNumber(value: Double): String {
    val formatted = String.format(Locale.US, "%.3f", value)
    return formatted
        .trimEnd('0')
        .trimEnd('.')
}
