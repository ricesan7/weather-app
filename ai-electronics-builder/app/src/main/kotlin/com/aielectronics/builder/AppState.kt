package com.aielectronics.builder

import com.aielectronics.application.SavedProjectSummary
import com.aielectronics.ble.android.AndroidBleRuntimeConnection
import com.aielectronics.core.model.ComponentResearchRecord
import com.aielectronics.core.model.ComponentResearchRequest
import com.aielectronics.core.model.MissingRequirement
import com.aielectronics.application.SavedGraphNodePosition
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.ResolvedRequirements

enum class AppScreen {
    HOME,
    DESIGN,
    GRAPH,
    PARTS,
    WIRING,
    BUILD,
    CONNECT,
    CONTROL,
    REVISION,
    EDITOR,
    BENCH,
}

data class BuilderAppState(
    val screen: AppScreen = AppScreen.HOME,
    val goalText: String = "",
    val additionalRequestText: String = "",
    val revisionMessages: List<RevisionChatMessage> = emptyList(),
    val revisionCandidateGoalText: String = "",
    val revisionClarificationValues: Map<String, String> = emptyMap(),
    val revisionPendingSlotId: String? = null,
    val revisionAssistantLabel: String = "",
    val revisionStatusMessage: String = "",
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
    val projectId: String? = null,
    val projectTitle: String? = null,
    val projectCreatedAtEpochMs: Long? = null,
    val completedConnectionIds: Set<String> = emptySet(),
    val currentBuildStepIndex: Int = 0,
    val savedProjects: List<SavedProjectSummary> = emptyList(),
    val lastSavedAtEpochMs: Long? = null,
    val bridgePairingCode: String = "",
    val base44BridgeStatus: String = "",
    val base44BridgeOnline: Boolean = false,
    val bridgeLastSyncAtEpochMs: Long? = null,
    val base44HandoffRevision: Int? = null,
    val base44HandoffStatus: String = "",
    val base44HandoffMessage: String = "",
    val componentResearchActive: Boolean = false,
    val componentResearchMessage: String = "",
    val componentResearchRecords: List<ComponentResearchRecord> = emptyList(),
    val pendingComponentResearchRequests:
        List<ComponentResearchRequest> = emptyList(),
    val selectedGraphNodeId: String? = null,
    val graphNodePositions: Map<String, SavedGraphNodePosition> = emptyMap(),
    val graphLayoutUndoStack:
        List<Map<String, SavedGraphNodePosition>> = emptyList(),
    val graphLayoutRedoStack:
        List<Map<String, SavedGraphNodePosition>> = emptyList(),
    val revisionReturnScreen: AppScreen? = null,
)
