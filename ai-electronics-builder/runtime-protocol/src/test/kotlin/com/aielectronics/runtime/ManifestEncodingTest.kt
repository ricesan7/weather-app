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
        @Test
    fun `canonical manifest includes verified driver profiles`() {
        val profile =
            RuntimeDriverProfile(
                driverId =
                    "profile_dht_pulse_sensor_aosong_dht11",
                family =
                    RuntimeDriverFamily.DHT_PULSE_SENSOR,
                interfaceType =
                    ElectricalInterface.ONE_WIRE,
                sampleIntervalMs = 2000,
                parameters =
                    mapOf(
                        "variant" to "DHT11",
                        "start_low_us" to "18000",
                        "zero_high_max_us" to "40",
                        "one_high_min_us" to "60",
                    ),
                telemetry =
                    listOf(
                        RuntimeDriverTelemetrySpec(
                            id = "temperature",
                            unit = "C",
                            source =
                                "DHT_TEMPERATURE",
                        ),
                        RuntimeDriverTelemetrySpec(
                            id = "humidity",
                            unit = "%",
                            source =
                                "DHT_HUMIDITY",
                        ),
                    ),
                status =
                    RuntimeDriverProfileStatus.RUNTIME_READY,
            )

        val manifest =
            ProjectManifest(
                version = "1.1",
                projectId = "dht",
                boardId = "xiao_esp32s3",
                drivers = listOf(profile.driverId),
                buses = emptyList(),
                gpio = emptyList(),
                devices =
                    listOf(
                        ManifestDevice(
                            instanceId = "sensor_dht11",
                            driverId = profile.driverId,
                            config =
                                mapOf(
                                    "board_pin" to
                                        "pin_xiao_d3_gpio4",
                                    "gpio" to "4",
                                ),
                        )
                    ),
                rules = emptyList(),
                settings = emptyList(),
                interlocks = emptyList(),
                failsafe = emptyList(),
                telemetryIds =
                    listOf(
                        "temperature",
                        "humidity",
                    ),
                tests = emptyList(),
                minimumRuntimeVersion = "0.2.0",
                driverProfiles = listOf(profile),
            )

        val text =
            CanonicalManifestEncoder().encode(manifest)

        assertTrue(
            text.contains(
                "driver_profile\t" +
                    profile.driverId +
                    "\tDHT_PULSE_SENSOR\tONE_WIRE\t2000"
            )
        )
        assertTrue(
            text.contains(
                "variant:DHT11"
            )
        )
        assertTrue(
            text.contains(
                "temperature,C,DHT_TEMPERATURE,1.0,0.0"
            )
        )
        assertTrue(
            text.contains(
                "device\tsensor_dht11\t" +
                    profile.driverId +
                    "\tboard_pin:pin_xiao_d3_gpio4,gpio:4"
            )
        )
    }
}
}
