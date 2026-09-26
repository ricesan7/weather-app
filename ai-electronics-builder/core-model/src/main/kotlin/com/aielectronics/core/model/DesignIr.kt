package com.aielectronics.core.model

data class DesignIr(
    val schemaVersion: String,
    val project: ProjectInfo,
    val assumptions: List<Assumption> = emptyList(),
    val unresolved: List<UnresolvedRequirement> = emptyList(),
    val capabilities: Set<CapabilityId>,
    val board: BoardSelection,
    val power: PowerPlan,
    val components: List<ComponentInstance>,
    val connections: List<Connection>,
    val behavior: BehaviorGraph,
    val settings: List<ProjectSetting> = emptyList(),
    val logging: LoggingSpec? = null,
    val events: List<EventSpec> = emptyList(),
    val ui: UiSpec,
    val tests: List<TestSpec>,
    val diagnostics: List<DiagnosticSpec>,
    val assembly: AssemblySpec,
    val deployment: DeploymentSpec,
    val safety: SafetySummary,
)

data class ProjectInfo(val id: String, val name: String, val goal: String)

data class Assumption(
    val id: String,
    val text: String,
    val userConfirmable: Boolean = true,
)

data class UnresolvedRequirement(
    val id: String,
    val reason: String,
    val blocking: Boolean,
)

@JvmInline
value class CapabilityId(val value: String)

data class BoardSelection(
    val boardId: String,
    val transports: Set<TransportKind>,
)

enum class TransportKind { BLE, USB, WIFI }

data class PowerPlan(
    val domains: List<PowerDomain>,
    val sources: List<PowerSource>,
)

data class PowerDomain(
    val id: String,
    val nominalVoltageV: Double,
    val maxRequiredCurrentMa: Double,
    val memberIds: Set<String>,
    val unknownCurrentMemberIds: Set<String> = emptySet(),
)

data class PowerSource(
    val id: String,
    val nominalVoltageV: Double,
    val maxCurrentMa: Double?,
    val polarity: Polarity = Polarity.UNKNOWN,
    val componentId: String? = null,
    val verified: Boolean = false,
)

enum class Polarity { CENTER_POSITIVE, CENTER_NEGATIVE, NOT_APPLICABLE, UNKNOWN }

data class ComponentInstance(
    val instanceId: String,
    val componentId: String,
    val role: String,
    val properties: Map<String, String> = emptyMap(),
)

data class PinRef(val entityId: String, val pinId: String)

data class Connection(
    val id: String,
    val from: PinRef,
    val to: PinRef,
    val netType: NetType,
    val wireSemantic: WireSemantic,
    val voltageV: Double? = null,
)

enum class NetType { POWER, GROUND, DIGITAL, ANALOG, I2C_SDA, I2C_SCL, SPI, UART, CONTROL, OTHER }
enum class WireSemantic { POWER_POSITIVE, GROUND, SIGNAL, CONTROL, ADDRESS, OTHER }

data class BehaviorGraph(
    val rules: List<BehaviorRule> = emptyList(),
    val states: List<StateSpec> = emptyList(),
    val schedules: List<ScheduleSpec> = emptyList(),
    val interlocks: List<InterlockSpec> = emptyList(),
    val failsafe: List<FailsafeSpec>,
)

data class BehaviorRule(
    val id: String,
    val condition: Expression,
    val actions: List<Action>,
    val priority: Int = 0,
)

data class StateSpec(
    val id: String,
    val transitions: List<StateTransition> = emptyList(),
)

data class StateTransition(
    val toStateId: String,
    val whenCondition: Expression,
)

data class ScheduleSpec(val id: String, val expression: String)

data class InterlockSpec(
    val id: String,
    val condition: Expression,
    val blockedActions: Set<String>,
    val mandatory: Boolean = true,
)

data class FailsafeSpec(
    val id: String,
    val condition: Expression,
    val actions: List<Action>,
)

sealed interface Expression {
    data class Raw(val expression: String) : Expression
}

sealed interface Action {
    data class SetOutput(val outputId: String, val value: String) : Action
    data class RaiseEvent(val eventId: String) : Action
    data class SetState(val stateId: String) : Action
}

data class ProjectSetting(
    val id: String,
    val type: SettingType,
    val defaultValue: String,
    val mutableAtRuntime: Boolean,
    val constraints: SettingConstraints = SettingConstraints(),
)

enum class SettingType { BOOLEAN, NUMBER, ENUM, TEXT, DURATION, SCHEDULE }

data class SettingConstraints(
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val allowedValues: List<String> = emptyList(),
    val relationalRule: String? = null,
)

data class LoggingSpec(
    val channelIds: List<String>,
    val intervalSeconds: Int,
    val retentionDays: Int,
    val primaryStorage: StorageTarget,
)

enum class StorageTarget { PHONE, MCU, SD_CARD, CLOUD }

data class EventSpec(
    val id: String,
    val condition: Expression,
    val severity: Severity,
)

enum class Severity { INFO, WARNING, CRITICAL }

data class UiSpec(val pages: List<UiPage>)

data class UiPage(
    val id: String,
    val title: String,
    val widgets: List<UiWidget>,
)

sealed interface UiWidget {
    val id: String
    val binding: String

    data class ValueCard(override val id: String, override val binding: String, val unit: String?) : UiWidget
    data class Gauge(override val id: String, override val binding: String, val min: Double, val max: Double) : UiWidget
    data class LineChart(override val id: String, override val binding: String) : UiWidget
    data class Toggle(override val id: String, override val binding: String) : UiWidget
    data class Slider(override val id: String, override val binding: String, val min: Double, val max: Double, val step: Double) : UiWidget
    data class Select(override val id: String, override val binding: String, val options: List<String>) : UiWidget
    data class Button(override val id: String, override val binding: String, val label: String) : UiWidget
    data class Status(override val id: String, override val binding: String) : UiWidget
    data class Alarm(override val id: String, override val binding: String) : UiWidget
}

data class TestSpec(
    val id: String,
    val name: String,
    val command: String,
    val required: Boolean,
)

data class DiagnosticSpec(
    val id: String,
    val trigger: String,
    val likelyCauses: List<String>,
    val buildStepIds: List<String>,
)

data class AssemblySpec(
    val mode: AssemblyMode,
    val stepIds: List<String>,
)

enum class AssemblyMode { GUIDED_SOLDER, BREADBOARD_PROTOTYPE, ADVANCED_FREEFORM }

data class DeploymentSpec(
    val firmwareProfileId: String,
    val manifestVersion: String,
)

data class SafetySummary(
    val state: ValidationState,
    val issues: List<ValidationIssue>,
)

enum class ValidationState { PASS, PASS_WITH_WARNING, REQUIRES_CONFIRMATION, BLOCKED }

data class ValidationIssue(
    val code: String,
    val severity: Severity,
    val message: String,
    val entityIds: Set<String> = emptySet(),
    val sourceIds: Set<String> = emptySet(),
)
