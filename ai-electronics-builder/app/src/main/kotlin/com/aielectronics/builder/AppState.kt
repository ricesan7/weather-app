package com.aielectronics.builder

import com.aielectronics.application.SavedProjectSummary
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
    val projectId: String? = null,
    val projectTitle: String? = null,
    val projectCreatedAtEpochMs: Long? = null,
    val completedConnectionIds: Set<String> = emptySet(),
    val currentBuildStepIndex: Int = 0,
    val savedProjects: List<SavedProjectSummary> = emptyList(),
    val lastSavedAtEpochMs: Long? = null,
)
