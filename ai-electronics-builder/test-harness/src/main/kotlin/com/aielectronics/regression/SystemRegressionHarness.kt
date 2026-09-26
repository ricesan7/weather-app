package com.aielectronics.regression

import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.ReleaseBundle

data class RegressionFixture(
    val id: String,
    val name: String,
    val userRequest: String,
    val requiredFeatures: Set<String>,
    val requiredHardware: Set<String>,
)

sealed interface RegressionOutcome {
    val fixture: RegressionFixture

    data class Compiled(
        override val fixture: RegressionFixture,
        val bundle: ReleaseBundle,
    ) : RegressionOutcome

    data class Unsupported(
        override val fixture: RegressionFixture,
        val missingFeatures: Set<String>,
        val missingHardware: Set<String>,
    ) : RegressionOutcome {
        val reason: String
            get() = buildString {
                append("unsupported:")
                if (missingFeatures.isNotEmpty()) {
                    append(" features=")
                    append(missingFeatures.sorted().joinToString(","))
                }
                if (missingHardware.isNotEmpty()) {
                    append(" hardware=")
                    append(missingHardware.sorted().joinToString(","))
                }
            }
    }

    data class NeedsUserInput(
        override val fixture: RegressionFixture,
        val slots: Set<String>,
    ) : RegressionOutcome

    data class Failed(
        override val fixture: RegressionFixture,
        val stage: String,
        val reason: String,
    ) : RegressionOutcome
}

data class RegressionSupportProfile(
    val features: Set<String>,
    val hardware: Set<String>,
)

object CurrentRegressionSupport {
    val profile = RegressionSupportProfile(
        features = setOf(
            "multi_rule",
            "logging",
            "generated_ui",
            "persistent_settings",
            "manual_override",
            "self_test",
            "failsafe",
            "multi_component",
            "derived_values",
        ),
        hardware = setOf(
            "esp32s3",
            "temperature_humidity_sensor",
            "fan_driver",
            "fan",
            "light_sensor",
            "barometer",
        ),
    )
}

object SystemRegressionFixtures {
    val all: List<RegressionFixture> = listOf(
        RegressionFixture(
            id = "reg_env_01",
            name = "環境監視＋自動換気",
            userRequest = "温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。",
            requiredFeatures = setOf(
                "multi_rule",
                "logging",
                "generated_ui",
                "persistent_settings",
                "manual_override",
                "self_test",
            ),
            requiredHardware = setOf(
                "esp32s3",
                "temperature_humidity_sensor",
                "fan_driver",
                "fan",
            ),
        ),
        RegressionFixture(
            id = "reg_irrigation_01",
            name = "自動散水",
            userRequest = "土が乾いたらポンプを動かすが、水位が低ければ絶対に動かさない。朝だけ有効。",
            requiredFeatures = setOf(
                "multi_rule",
                "schedule",
                "interlock",
                "logging",
                "notifications",
            ),
            requiredHardware = setOf(
                "soil_sensor",
                "water_level_sensor",
                "pump_driver",
                "pump",
            ),
        ),
        RegressionFixture(
            id = "reg_alarm_01",
            name = "ドア監視警報",
            userRequest = "夜間だけドア開放を記録して警報。スマホで警戒ON/OFF。",
            requiredFeatures = setOf(
                "state_machine",
                "schedule",
                "logging",
                "generated_ui",
                "notifications",
            ),
            requiredHardware = setOf(
                "reed_switch",
                "buzzer",
            ),
        ),
        RegressionFixture(
            id = "reg_motor_01",
            name = "位置決めモーター",
            userRequest = "ボタンで動かし、端のリミットSWで必ず停止。スマホからも操作。",
            requiredFeatures = setOf(
                "interlock",
                "manual_override",
                "state_machine",
                "self_test",
            ),
            requiredHardware = setOf(
                "motor_driver",
                "limit_switch",
                "motor",
            ),
        ),
        RegressionFixture(
            id = "reg_logger_01",
            name = "多センサーロガー",
            userRequest = "温湿度・照度・気圧を1分ごとに保存してグラフ表示。",
            requiredFeatures = setOf(
                "multi_component",
                "logging",
                "generated_ui",
                "derived_values",
            ),
            requiredHardware = setOf(
                "temperature_humidity_sensor",
                "light_sensor",
                "barometer",
            ),
        ),
        RegressionFixture(
            id = "reg_remote_01",
            name = "多出力リモコン",
            userRequest = "スマホからサーボ2個、LED、ブザーを操作し、位置プリセットを保存。",
            requiredFeatures = setOf(
                "multi_component",
                "generated_ui",
                "profiles",
                "persistent_settings",
            ),
            requiredHardware = setOf(
                "servo",
                "led",
                "buzzer",
            ),
        ),
        RegressionFixture(
            id = "reg_fail_01",
            name = "センサー故障時安全停止",
            userRequest = "温度センサーが読めなければヒーター系出力を必ずOFFにしたい。",
            requiredFeatures = setOf(
                "failsafe",
                "self_test",
                "notifications",
            ),
            requiredHardware = setOf(
                "temperature_sensor",
                "heater_driver",
                "heater",
            ),
        ),
        RegressionFixture(
            id = "reg_adv_01",
            name = "初心者→上級者移行",
            userRequest = "コードなしで作った装置を、後からコードを編集して独自処理を追加したい。",
            requiredFeatures = setOf(
                "advanced_code",
                "generated_ui",
            ),
            requiredHardware = setOf(
                "validated_project",
            ),
        ),
    )
}

class SystemRegressionHarness(
    private val support: RegressionSupportProfile = CurrentRegressionSupport.profile,
    private val interpreter: BeginnerIntentInterpreter = BeginnerIntentInterpreter(),
    private val engine: ApplicationProjectEngine = ApplicationProjectEngine(),
) {

    fun runAll(): List<RegressionOutcome> =
        SystemRegressionFixtures.all.map(::run)

    fun run(fixture: RegressionFixture): RegressionOutcome {
        val missingFeatures = fixture.requiredFeatures - support.features
        val missingHardware = fixture.requiredHardware - support.hardware

        if (missingFeatures.isNotEmpty() || missingHardware.isNotEmpty()) {
            return RegressionOutcome.Unsupported(
                fixture = fixture,
                missingFeatures = missingFeatures,
                missingHardware = missingHardware,
            )
        }

        val intent = interpreter.interpret(fixture.userRequest)

        return when (val resolution = engine.resolve(intent)) {
            is RequirementResolution.NeedUserInput ->
                RegressionOutcome.NeedsUserInput(
                    fixture = fixture,
                    slots = resolution.missing.map { it.slotId }.toSet(),
                )

            is RequirementResolution.Ready ->
                when (val compile = engine.compile(resolution.requirements)) {
                    is CompileResult.Success ->
                        RegressionOutcome.Compiled(fixture, compile.bundle)

                    is CompileResult.NeedUserInput ->
                        RegressionOutcome.NeedsUserInput(
                            fixture,
                            compile.questions.map { it.slotId }.toSet(),
                        )

                    is CompileResult.Blocked ->
                        RegressionOutcome.Failed(
                            fixture = fixture,
                            stage = "electrical_validate",
                            reason = compile.report.issues.joinToString(";") {
                                it.code + ":" + it.message
                            },
                        )

                    is CompileResult.Failed ->
                        RegressionOutcome.Failed(
                            fixture = fixture,
                            stage = compile.error.stage,
                            reason = compile.error.message,
                        )
                }
        }
    }
}
