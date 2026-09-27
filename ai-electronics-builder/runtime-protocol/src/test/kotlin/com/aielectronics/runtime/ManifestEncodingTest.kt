package com.aielectronics.runtime

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

class ManifestEncodingTest {

    @Test
    fun `canonical manifest includes behavior failsafe and mutable settings`() {
        val manifest = ProjectManifest(
            version = "1.0",
            projectId = "golden",
            boardId = "xiao_esp32s3",
            drivers = listOf("drv_gpio_sink"),
            buses = emptyList(),
            gpio = emptyList(),
            devices = emptyList(),
            rules = listOf(
                BehaviorRule(
                    id = "auto_on",
                    condition = Expression.Raw("mode == AUTO && temperature >= temp_on"),
                    actions = listOf(Action.SetOutput("fan", "ON")),
                    priority = 50,
                )
            ),
            settings = listOf(
                ProjectSetting(
                    id = "temp_on",
                    type = SettingType.NUMBER,
                    defaultValue = "30.0",
                    mutableAtRuntime = true,
                    constraints = SettingConstraints(min = 10.0, max = 50.0, step = 0.5),
                )
            ),
            interlocks = emptyList(),
            failsafe = listOf(
                FailsafeSpec(
                    id = "sensor_timeout",
                    condition = Expression.Raw("required_sensor_invalid_for >= 5s"),
                    actions = listOf(Action.SetOutput("fan", "OFF")),
                )
            ),
            telemetryIds = listOf("temperature"),
            tests = emptyList(),
            minimumRuntimeVersion = "0.1.0",
        )

        val text = CanonicalManifestEncoder().encode(manifest)

        assertTrue(
            text.contains(
                "autonomy\tAUTONOMOUS_MCU\ttrue\ttrue\ttrue\t"
            )
        )
        assertTrue(text.contains("rule\tauto_on\t50"))
        assertTrue(text.contains("setting\ttemp_on\tNUMBER\t30.0\ttrue"))
        assertTrue(text.contains("failsafe\tsensor_timeout"))
        assertTrue(text.contains("set:fan:OFF"))
    }
}
