package com.aielectronics.compiler

import com.aielectronics.core.model.*
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
