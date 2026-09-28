package com.aielectronics.application

import com.aielectronics.compiler.CompileFailure
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.ValidationReport

object BeginnerErrorPresenter {

    fun validationBlocked(report: ValidationReport): String {
        val first = report.issues.firstOrNull()
        return when (first?.code) {
            "E_POWER_VOLTAGE_MISMATCH" ->
                "部品に合わない電圧になるため、この設計では安全に接続できません。"
            "E_EXTERNAL_SUPPLY_CURRENT_UNKNOWN" ->
                "負荷に必要な電流を確認できないため、安全な電源を決められません。"
            "E_EXTERNAL_SUPPLY_REQUIRED" ->
                "この負荷には別電源が必要です。対応する電源を含めて設計を見直してください。"
            "E_POWER_CAPACITY_INSUFFICIENT" ->
                "電源容量が不足するため、この構成では動かせません。"
            "E_MOTOR_DRIVER_REQUIRED", "E_GPIO_DIRECT_LOAD" ->
                "この負荷はマイコンへ直接つなげません。安全な駆動回路が必要です。"
            "E_DRIVER_CURRENT_EXCEEDED" ->
                "選択した駆動部品では負荷の電流を安全に扱えません。"
            "E_LOGIC_LEVEL_INCOMPATIBLE" ->
                "信号電圧の組み合わせが合わないため、この配線では動作を保証できません。"
            "E_I2C_ADDRESS_COLLISION" ->
                "同じ通信アドレスの部品が重なっているため、構成を変更する必要があります。"
            "E_MISSING_COMMON_GND" ->
                "電源とマイコンの基準線がつながっていません。GND配線を見直してください。"
            else ->
                "安全確認を通過できなかったため、この設計は装置へ設定できません。"
        }
    }

    fun compileFailure(failure: CompileFailure): String = when (failure.stage) {
        "component_resolve" ->
            "この用途に必要な検証済み部品が、現在の部品データにまだありません。"
        "board_select" ->
            "必要な機能を満たす検証済みマイコンを現在は選択できません。"
        "power_plan" ->
            "安全な電源構成を決められませんでした。"
        "pin_allocate" ->
            "必要な接続を安全に割り当てられませんでした。"
        "circuit_compile" ->
            "配線設計を完成できませんでした。"
        "behavior_compile" ->
            "指定された動作条件を安全な制御ルールに変換できませんでした。"
        else ->
            "設計を完成できませんでした。条件を少し変えてもう一度試してください。"
    }

    fun connectionFailure(): String =
        "装置を見つけられませんでした。装置の電源とBluetoothを確認して、もう一度試してください。"

    fun deploymentFailure(): String =
        "装置への設定を完了できませんでした。接続と配線を確認して、もう一度試してください。"
}

object DesignExplanationBuilder {

    fun explain(bundle: ReleaseBundle): List<String> = buildList {
        add("マイコンは、必要な通信・入出力を満たす検証済み候補から自動選択しています。")
        add("センサーや駆動部品は、要求機能と電気的な互換性を満たす検証済み部品から選択しています。")

        if (bundle.designIr.power.sources.any { it.componentId != null }) {
            add("負荷はマイコンから直接駆動せず、必要な外部電源と駆動段を含む構成にしています。")
        }

        add("表示している配線は、安全検証を通った接続データと同じCircuitGraphから生成しています。")

        if (bundle.designIr.settings.any { it.mutableAtRuntime }) {
            add("しきい値や動作モードは装置を書き直さず、完成後もスマホから変更できます。")
        }
    }
}

data class BeginnerJourneyContract(
    val userRoleCategories: Set<String> = setOf(
        "目的を伝える",
        "部品を準備する",
        "画像どおりに組み立てる",
        "安全確認をする",
    ),
    val workflowAppSwitches: Int = 0,
    val manualProgrammingActions: Int = 0,
    val manualBuildConfigActions: Int = 0,
)
