package com.aielectronics.core.model

data class ValidationReport(
    val state: ValidationState,
    val issues: List<ValidationIssue>,
) {
    val isBlocked: Boolean get() = state == ValidationState.BLOCKED
}

data class CapabilitySet(val values: Set<CapabilityId>)
data class ResolvedComponents(val components: List<ComponentInstance>)
data class PinAssignment(val logicalRole: String, val pin: PinRef)

data class CircuitGraph(
    val board: BoardSelection,
    val components: List<ComponentInstance>,
    val power: PowerPlan,
    val connections: List<Connection>,
)

data class DiagramSpec(val views: List<DiagramView>)

data class DiagramView(
    val id: String,
    val kind: DiagramViewKind,
    val highlightedConnectionIds: List<String> = emptyList(),
)

enum class DiagramViewKind { SYSTEM_OVERVIEW, PHYSICAL_WIRING, SOLDER_STEP, SCHEMATIC, POWER_CHECK }

data class ProjectManifest(
    val version: String,
    val projectId: String,
    val boardId: String,
    val drivers: List<String>,
    val buses: List<ManifestBus>,
    val gpio: List<ManifestGpio>,
    val devices: List<ManifestDevice>,
    val rules: List<BehaviorRule>,
    val settings: List<ProjectSetting>,
    val interlocks: List<InterlockSpec>,
    val failsafe: List<FailsafeSpec>,
    val telemetryIds: List<String>,
    val tests: List<TestSpec>,
    val minimumRuntimeVersion: String,
)

data class ManifestBus(val id: String, val kind: String, val pins: Map<String, String>)
data class ManifestGpio(val pinId: String, val mode: String, val safeValue: String?)
data class ManifestDevice(val instanceId: String, val driverId: String, val config: Map<String, String>)

data class TestPlan(val tests: List<TestSpec>)

data class ReleaseBundle(
    val designIr: DesignIr,
    val validation: ValidationReport,
    val circuitGraph: CircuitGraph,
    val diagramSpec: DiagramSpec,
    val manifest: ProjectManifest?,
    val uiSpec: UiSpec,
    val testPlan: TestPlan,
)
