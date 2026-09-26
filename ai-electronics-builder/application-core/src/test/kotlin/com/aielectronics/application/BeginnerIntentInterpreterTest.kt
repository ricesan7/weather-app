package com.aielectronics.application

import kotlin.test.Test
import kotlin.test.assertEquals

class BeginnerIntentInterpreterTest {

    private val interpreter = BeginnerIntentInterpreter()

    @Test
    fun `latest additional requirement overrides earlier temperature thresholds`() {
        val draft = interpreter.interpret(
            """
            温度が30℃以上になったらファンを回し、28℃以下で停止したい。

            【追加要望】
            動作条件を32℃以上で開始、29℃以下で停止に変更したい。
            """.trimIndent()
        )

        assertEquals("32.0", draft.extractedFacts["temp_on"])
        assertEquals("29.0", draft.extractedFacts["temp_off"])
    }

    @Test
    fun `latest additional requirement overrides earlier humidity threshold`() {
        val draft = interpreter.interpret(
            """
            湿度70%以上で換気したい。

            【追加要望】
            湿度75%以上に変更したい。
            """.trimIndent()
        )

        assertEquals("75.0", draft.extractedFacts["rh_on"])
        assertEquals("70.0", draft.extractedFacts["rh_off"])
    }
}
