package com.aielectronics.control

import com.aielectronics.core.model.TestSpec
import com.aielectronics.core.model.UiPage
import com.aielectronics.core.model.UiSpec
import com.aielectronics.core.model.UiWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardProjectionTest {

    @Test
    fun `diagnostics page is generated from runtime tests`() {
        val ui = UiSpec(
            pages = listOf(
                UiPage(
                    id = "dashboard",
                    title = "状態",
                    widgets = listOf(
                        UiWidget.ValueCard(
                            id = "temperature",
                            binding = "telemetry.temperature",
                            unit = "°C",
                        )
                    ),
                ),
                UiPage(
                    id = "settings",
                    title = "設定",
                    widgets = listOf(
                        UiWidget.Slider(
                            id = "temp_on",
                            binding = "settings.temp_on",
                            min = 20.0,
                            max = 45.0,
                            step = 0.5,
                        )
                    ),
                ),
                UiPage(
                    id = "history",
                    title = "履歴",
                    widgets = listOf(
                        UiWidget.LineChart(
                            id = "history_temperature",
                            binding = "logging.temperature",
                        )
                    ),
                ),
            )
        )
        val tests = listOf(
            TestSpec(
                id = "sensor_probe",
                name = "センサー確認",
                command = "probe_required_sensors",
                required = true,
            ),
            TestSpec(
                id = "fan_output_test",
                name = "ファン確認",
                command = "fan_on_1s_then_off",
                required = true,
            ),
        )

        val pages = DashboardProjection.pages(ui, tests)

        assertEquals(
            listOf("dashboard", "settings", "history", "diagnostics"),
            pages.map { it.id },
        )
        val diagnosticBindings = pages
            .single { it.id == "diagnostics" }
            .widgets
            .map { it.binding }

        assertEquals(
            listOf("tests.sensor_probe", "tests.fan_output_test"),
            diagnosticBindings,
        )
    }

    @Test
    fun `only explicit setting bindings become mutable runtime settings`() {
        val ui = UiSpec(
            pages = listOf(
                UiPage(
                    id = "dashboard",
                    title = "状態",
                    widgets = listOf(
                        UiWidget.ValueCard(
                            id = "temperature",
                            binding = "telemetry.temperature",
                            unit = "°C",
                        ),
                        UiWidget.Select(
                            id = "mode",
                            binding = "settings.mode",
                            options = listOf("AUTO", "MANUAL"),
                        ),
                        UiWidget.Toggle(
                            id = "manual_fan",
                            binding = "settings.manual_fan",
                        ),
                    ),
                )
            )
        )

        val settingIds = DashboardProjection.mutableSettingIds(ui)

        assertEquals(
            setOf("mode", "manual_fan"),
            settingIds,
        )
        assertTrue("temperature" !in settingIds)
    }
}
