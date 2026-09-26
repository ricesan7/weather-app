package com.aielectronics.builder

import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppProjectEngineTest {

    private val interpreter = BeginnerIntentInterpreter()
    private val engine = AppProjectEngine()

    @Test
    fun `golden beginner request compiles end to end from natural language`() {
        val intent = interpreter.interpret(
            "温度が30℃以上になったらファンを自動で回したい。履歴もスマホで見たい。"
        )

        val resolution = assertIs<RequirementResolution.Ready>(
            engine.resolve(intent)
        )
        val success = assertIs<CompileResult.Success>(
            engine.compile(resolution.requirements)
        )

        assertEquals(
            "xiao_esp32s3",
            success.bundle.designIr.board.boardId,
        )
        assertEquals(10, success.bundle.circuitGraph.connections.size)
        assertTrue(success.bundle.diagramSpec.buildPlan?.steps?.size == 10)
        assertTrue(success.bundle.manifest != null)
        assertTrue(success.bundle.uiSpec.pages.any { it.id == "dashboard" })
        assertTrue(success.bundle.uiSpec.pages.any { it.id == "settings" })
        assertTrue(success.bundle.uiSpec.pages.any { it.id == "history" })
        assertTrue(success.bundle.testPlan.tests.isNotEmpty())
    }

    @Test
    fun `temperature threshold is extracted without asking GPIO or library questions`() {
        val intent = interpreter.interpret(
            "温度が32℃以上になったらファンを回したい"
        )

        val resolution = assertIs<RequirementResolution.Ready>(
            engine.resolve(intent)
        )

        assertEquals(
            "32.0",
            resolution.requirements.slots["temp_on"]?.value,
        )
        assertEquals(
            "30.0",
            resolution.requirements.slots["temp_off"]?.value,
        )

        val allSlots = resolution.requirements.slots.keys
        assertTrue(allSlots.none { it.contains("gpio", ignoreCase = true) })
        assertTrue(allSlots.none { it.contains("library", ignoreCase = true) })
    }
}
