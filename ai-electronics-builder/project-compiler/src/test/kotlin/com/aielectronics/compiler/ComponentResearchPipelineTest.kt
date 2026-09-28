package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog
import com.aielectronics.parts.GoldenEngineeringCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComponentResearchPipelineTest {

    @Test
    fun `finalized Base44 spec extracts explicit parts and quantities`() {
        val goal = """
            ESP32無線温度計システム

            ## マイコン・ボード
            - ESP32 (親機・子機各1台以上)

            ## 部品リスト
            - 温度センサー: DHT11
            - ディスプレイ: 0.96インチ OLED (I2C接続)
            - 操作: タクトスイッチ2個(表示切り替え・リセット用)
            - 電源回路: 電源自動切替モジュール

            ## 電源
            - 親機: USB給電
        """.trimIndent()

        val parts = RequestedComponentExtractor.extract(goal)

        assertTrue(parts.any { it.rawName == "DHT11" })
        assertTrue(
            parts.any {
                it.rawName.contains("OLED") &&
                    it.categoryHint == "display"
            }
        )
        assertTrue(
            parts.any {
                it.rawName == "タクトスイッチ" &&
                    it.quantity == 2
            }
        )
        assertTrue(
            parts.any {
                it.rawName == "電源自動切替モジュール"
            }
        )
        assertTrue(parts.none { it.rawName == "USB給電" })
        assertTrue(parts.none { it.rawName.equals("ESP32", true) })
    }

    @Test
    fun `unknown explicit component returns research request instead of substitution`() {
        val request = RequestedComponent(
            rawName = "DHT11",
            categoryHint = "sensor",
        )
        val result = CatalogComponentResolver(
            GoldenEngineeringCatalog
        ).resolve(
            capabilities = CapabilitySet(
                setOf(CapabilityId("measure_temperature"))
            ),
            requirements = ResolvedRequirements(
                goal = "DHT11で温度を測定する",
                slots = emptyMap(),
                requestedComponents = listOf(request),
            ),
        )

        val failure = result.exceptionOrNull()
        assertIs<ComponentResearchRequiredException>(failure)
        assertEquals(
            "DHT11",
            failure.requests.single().requested.rawName,
        )
        assertTrue(
            CapabilityId("measure_temperature") in
                failure.requests.single().requiredCapabilities
        )
    }

    @Test
    fun `electrically verified part can enter design while runtime driver is pending`() {
        val display =
            ComponentSpec(
                componentId = "research_display_ssd1306",
                displayName = "SSD1306 OLED",
                kind = ComponentKind.DISPLAY,
                defaultRole = "display_ssd1306",
                providesCapabilities =
                    setOf(CapabilityId("display_visual")),
                primaryInterface = ElectricalInterface.I2C,
                voltageRange =
                    VoltageRange(
                        minV = 3.0,
                        typicalV = 3.3,
                        maxV = 5.0,
                    ),
                preferredSupplyVoltageV = 3.3,
                supplyRole = SupplyRole.LOGIC,
                pins =
                    listOf(
                        ComponentPinSpec(
                            "vcc",
                            "VCC",
                            ComponentPinRole.VCC,
                        ),
                        ComponentPinSpec(
                            "gnd",
                            "GND",
                            ComponentPinRole.GND,
                        ),
                        ComponentPinSpec(
                            "sda",
                            "SDA",
                            ComponentPinRole.I2C_SDA,
                        ),
                        ComponentPinSpec(
                            "scl",
                            "SCL",
                            ComponentPinRole.I2C_SCL,
                        ),
                    ),
                signalRequirements =
                    listOf(
                        SignalRequirement(
                            id = "sda",
                            boardCapability =
                                BoardPinCapability.I2C_SDA,
                            componentPinRole =
                                ComponentPinRole.I2C_SDA,
                            netType = NetType.I2C_SDA,
                            wireSemantic =
                                WireSemantic.SIGNAL,
                            shareable = true,
                        ),
                        SignalRequirement(
                            id = "scl",
                            boardCapability =
                                BoardPinCapability.I2C_SCL,
                            componentPinRole =
                                ComponentPinRole.I2C_SCL,
                            netType = NetType.I2C_SCL,
                            wireSemantic =
                                WireSemantic.SIGNAL,
                            shareable = true,
                        ),
                    ),
                i2cAddress = "0x3C",
                aliases = setOf("0.96インチ OLED"),
                verificationStatus =
                    ComponentVerificationStatus.VERIFIED,
                designReady = false,
            )

        val catalog =
            object : EngineeringCatalog {
                override fun components() =
                    GoldenEngineeringCatalog.components() +
                        display

                override fun boards() =
                    GoldenEngineeringCatalog.boards()

                override fun powerSupplies() =
                    GoldenEngineeringCatalog.powerSupplies()
            }

        val result =
            CatalogComponentResolver(catalog)
                .resolve(
                    capabilities =
                        CapabilitySet(
                            setOf(
                                CapabilityId(
                                    "display_visual"
                                )
                            )
                        ),
                    requirements =
                        ResolvedRequirements(
                            goal =
                                "0.96インチ OLEDに表示する",
                            slots = emptyMap(),
                            requestedComponents =
                                listOf(
                                    RequestedComponent(
                                        rawName =
                                            "0.96インチ OLED",
                                        categoryHint =
                                            "display",
                                    )
                                ),
                        ),
                )

        assertTrue(result.isSuccess)
        val resolved =
            result.getOrThrow().components.single {
                it.componentId ==
                    "research_display_ssd1306"
            }
        assertEquals(
            "false",
            resolved.properties["runtime_ready"],
        )
        assertEquals(
            "true",
            resolved.properties["runtime_required"],
        )
    }

    @Test
    fun `missing display capability creates a generic research request`() {
        val result = CatalogComponentResolver(
            GoldenEngineeringCatalog
        ).resolve(
            capabilities = CapabilitySet(
                setOf(CapabilityId("display_visual"))
            ),
            requirements = ResolvedRequirements(
                goal = "本体OLEDに値を表示する",
                slots = emptyMap(),
            ),
        )

        val failure = result.exceptionOrNull()
        assertIs<ComponentResearchRequiredException>(failure)
        assertEquals(
            "ディスプレイモジュール",
            failure.requests.single().requested.rawName,
        )
    }
}
