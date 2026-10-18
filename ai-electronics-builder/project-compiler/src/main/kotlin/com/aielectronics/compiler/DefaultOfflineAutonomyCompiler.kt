package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultOfflineAutonomyCompiler : OfflineAutonomyCompiler {

    override fun compile(
        requirements: ResolvedRequirements,
        capabilities: CapabilitySet,
        behavior: BehaviorCompilation,
    ): Result<OfflineAutonomySpec> = runCatching {
        val reason = externalInputReason(requirements.goal)

        OfflineAutonomySpec(
            coreOperationMode =
                if (reason == null) {
                    CoreOperationMode.AUTONOMOUS_MCU
                } else {
                    CoreOperationMode.EXTERNAL_INPUT_REQUIRED
                },
            localBehaviorExecutionRequired = reason == null,
            localSafetyExecutionRequired = true,
            persistRuntimeSettings = true,
            externalInputReason = reason,
        )
    }

    private fun externalInputReason(goal: String): String? {
        val normalized = goal.lowercase()

        val dependencies = listOf(
            listOf("スマホのカメラ", "スマホカメラ", "phone camera") to
                "スマホのカメラ入力が中核機能として必要です。",
            listOf("スマホのgps", "スマホgps", "phone gps", "スマホの位置情報") to
                "スマホの位置情報が中核機能として必要です。",
            listOf("スマホのマイク", "スマホマイク", "phone microphone") to
                "スマホのマイク入力が中核機能として必要です。",
            listOf("スマホの加速度", "スマホ加速度", "phone accelerometer") to
                "スマホのモーションセンサー入力が中核機能として必要です。",
            listOf("クラウドaiが必須", "クラウドai判定が必須", "cloud ai required") to
                "クラウドAIの判定結果が中核機能として必要です。",
        )

        return dependencies.firstOrNull { (terms, _) ->
            terms.any(normalized::contains)
        }?.second
    }
}
