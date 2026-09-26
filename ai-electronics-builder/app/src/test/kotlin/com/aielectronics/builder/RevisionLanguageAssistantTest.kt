package com.aielectronics.builder

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevisionLanguageAssistantTest {

    @Test
    fun `local fallback preserves current goal and appends the new turn`() = runBlocking {
        val result = LocalRevisionLanguageAssistant()
            .refine(
                RevisionLanguageRequest(
                    currentGoal = "温度が30℃以上でファンを回す。",
                    userMessage = "湿度も記録したい。",
                    history = emptyList(),
                )
            )
            .getOrThrow()

        assertTrue(result.updatedGoal.contains("温度が30℃以上"))
        assertTrue(result.updatedGoal.contains("湿度も記録したい"))
        assertFalse(result.needsClarification)
    }
}
