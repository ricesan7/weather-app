package com.aielectronics.compiler

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultOfflineAutonomyCompilerTest {

    private val compiler = DefaultOfflineAutonomyCompiler()
    private val capabilities = CapabilitySet(
        setOf(
            CapabilityId("generated_ui"),
            CapabilityId("failsafe"),
        )
    )
    private val behavior = BehaviorCompilation(
        graph = BehaviorGraph(failsafe = emptyList()),
        settings = emptyList(),
        logging = null,
        events = emptyList(),
    )

    @Test
    fun `ordinary smartphone display and settings remain MCU autonomous`() {
        val result = compiler.compile(
            requirements = ResolvedRequirements(
                goal = "センサー値をスマホに表示して設定値を変更したい",
            ),
            capabilities = capabilities,
            behavior = behavior,
        ).getOrThrow()

        assertEquals(
            CoreOperationMode.AUTONOMOUS_MCU,
            result.coreOperationMode,
        )
        assertTrue(result.localBehaviorExecutionRequired)
        assertTrue(result.localSafetyExecutionRequired)
        assertTrue(result.persistRuntimeSettings)
        assertNull(result.externalInputReason)
    }

    @Test
    fun `phone camera as core input is explicitly external dependent`() {
        val result = compiler.compile(
            requirements = ResolvedRequirements(
                goal = "スマホのカメラで判定した結果を使って装置を動かす",
            ),
            capabilities = capabilities,
            behavior = behavior,
        ).getOrThrow()

        assertEquals(
            CoreOperationMode.EXTERNAL_INPUT_REQUIRED,
            result.coreOperationMode,
        )
        assertTrue(result.localSafetyExecutionRequired)
        assertTrue(result.persistRuntimeSettings)
        assertTrue(result.externalInputReason?.contains("カメラ") == true)
    }
}
