package com.aielectronics.builder

import com.aielectronics.core.model.ComponentResearchRequest
import com.aielectronics.core.model.ComponentVerificationStatus
import com.aielectronics.core.model.RequestedComponent
import com.aielectronics.core.model.RuntimeDriverFamily
import com.aielectronics.core.model.RuntimeDriverProfileStatus
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComponentResearchDriverProfileTest {

    @Test
    fun `valid DHT profile becomes runtime ready`() {
        val record =
            ComponentResearchResponseValidator.parse(
                request = request(),
                json = dhtResponse(startLowUs = "18000"),
            )

        assertEquals(
            ComponentVerificationStatus.DESIGN_READY,
            record.status,
        )
        val component = assertNotNull(record.component)
        val profile =
            assertNotNull(component.runtimeDriverProfile)
        assertEquals(
            RuntimeDriverFamily.DHT_PULSE_SENSOR,
            profile.family,
        )
        assertEquals(
            RuntimeDriverProfileStatus.RUNTIME_READY,
            profile.status,
        )
        assertEquals(profile.driverId, component.driverId)
        assertTrue(
            profile.telemetry.any {
                it.id == "temperature" &&
                    it.source == "DHT_TEMPERATURE"
            }
        )
        assertTrue(
            profile.telemetry.any {
                it.id == "humidity" &&
                    it.source == "DHT_HUMIDITY"
            }
        )
    }

    @Test
    fun `invalid DHT timing never becomes design ready`() {
        val record =
            ComponentResearchResponseValidator.parse(
                request = request(),
                json = dhtResponse(startLowUs = "100"),
            )

        assertEquals(
            ComponentVerificationStatus.VERIFIED,
            record.status,
        )
        assertTrue("runtime_driver" in record.missingFields)
        val component = assertNotNull(record.component)
        assertEquals(null, component.driverId)
        assertEquals(
            RuntimeDriverProfileStatus.VALIDATED,
            component.runtimeDriverProfile?.status,
        )
    }

    private fun request() =
        ComponentResearchRequest(
            requestId = "research_dht11",
            requested =
                RequestedComponent(
                    rawName = "DHT11",
                    categoryHint = "sensor",
                ),
            projectGoal = "DHT11で温湿度を測定する",
        )

    private fun dhtResponse(
        startLowUs: String,
    ): JSONObject =
        JSONObject().apply {
            put("requested_name", "DHT11")
            put("manufacturer", "Aosong")
            put("model", "DHT11")
            put("display_name", "DHT11")
            put("kind", "SENSOR")
            put("primary_interface", "ONE_WIRE")
            put("voltage_min_v", 3.0)
            put("voltage_typical_v", 5.0)
            put("voltage_max_v", 5.5)
            put("preferred_supply_v", 3.3)
            put("current_max_ma", 2.5)
            put("i2c_address", "")
            put("requires_external_power", false)
            put("driver_id", "")
            put(
                "driver_profile",
                JSONObject().apply {
                    put("family", "DHT_PULSE_SENSOR")
                    put("sample_interval_ms", 2000)
                    put(
                        "parameters",
                        JSONArray().apply {
                            put(parameter("variant", "DHT11"))
                            put(
                                parameter(
                                    "start_low_us",
                                    startLowUs,
                                )
                            )
                            put(
                                parameter(
                                    "zero_high_max_us",
                                    "40",
                                )
                            )
                            put(
                                parameter(
                                    "one_high_min_us",
                                    "60",
                                )
                            )
                        },
                    )
                    put(
                        "telemetry",
                        JSONArray().apply {
                            put(
                                telemetry(
                                    id = "temperature",
                                    unit = "C",
                                    source =
                                        "DHT_TEMPERATURE",
                                )
                            )
                            put(
                                telemetry(
                                    id = "humidity",
                                    unit = "%",
                                    source =
                                        "DHT_HUMIDITY",
                                )
                            )
                        },
                    )
                },
            )
            put(
                "capabilities",
                JSONArray(
                    listOf(
                        "measure_temperature",
                        "measure_humidity",
                    )
                ),
            )
            put("aliases", JSONArray(listOf("DHT-11")))
            put(
                "pins",
                JSONArray().apply {
                    put(pin("VCC", "VCC"))
                    put(pin("DATA", "DATA"))
                    put(pin("GND", "GND"))
                },
            )
            put(
                "sources",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put(
                                "url",
                                "https://www.aosong.com/userfiles/files/media/DHT11.pdf",
                            )
                            put("title", "DHT11 datasheet")
                            put(
                                "authority",
                                "MANUFACTURER_DATASHEET",
                            )
                        }
                    )
                },
            )
            put("evidence_complete", true)
            put("confidence", 0.95)
            put("notes", JSONArray())
        }

    private fun parameter(
        key: String,
        value: String,
    ) =
        JSONObject().apply {
            put("key", key)
            put("value", value)
        }

    private fun telemetry(
        id: String,
        unit: String,
        source: String,
    ) =
        JSONObject().apply {
            put("id", id)
            put("unit", unit)
            put("source", source)
            put("scale", 1.0)
            put("offset", 0.0)
        }

    private fun pin(
        label: String,
        role: String,
    ) =
        JSONObject().apply {
            put("label", label)
            put("role", role)
        }
}
