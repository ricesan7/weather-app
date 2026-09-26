package com.aielectronics.builder

import com.aielectronics.ble.android.AndroidBleRuntimeConnection
import com.aielectronics.core.model.MissingRequirement
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.ResolvedRequirements

enum class AppScreen {
    HOME,
    DESIGN,
    PARTS,
    WIRING,
    BUILD,
    CONNECT,
    CONTROL,
    EDITOR,
    BENCH,
}

data class BuilderAppState(
    val screen: AppScreen = AppScreen.HOME,
    val goalText: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val pendingQuestions: List<MissingRequirement> = emptyList(),
    val clarificationValues: Map<String, String> = emptyMap(),
    val requirements: ResolvedRequirements? = null,
    val bundle: ReleaseBundle? = null,
    val connection: AndroidBleRuntimeConnection? = null,
    val deployProgress: Int = 0,
    val deployMessage: String = "",
    val deployed: Boolean = false,
    val buildTargetStepOrder: Int? = null,
)
