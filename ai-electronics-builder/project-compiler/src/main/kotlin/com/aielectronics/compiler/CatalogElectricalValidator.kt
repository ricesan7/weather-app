package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog
import kotlin.math.abs

class CatalogElectricalValidator(
    private val catalog: EngineeringCatalog,
    private val currentMarginMultiplier: Double = 1.25,
) : ElectricalValidator {

    override fun validate(graph: CircuitGraph): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        val boardSpec = catalog.board(graph.board.boardId)

        if (boardSpec == null) {
            issues += critical(
                "E_BOARD_SPEC_MISSING",
                "Board engineering specification is missing.",
                setOf(graph.board.boardId),
            )
            return ValidationReport(ValidationState.BLOCKED, issues)
        }

        val specsByInstance = graph.components.associateWith { instance ->
            catalog.component(instance.componentId)
        }

        graph.power.domains.forEach { domain ->
            domain.memberIds.forEach { instanceId ->
                val entry = specsByInstance.entries.firstOrNull {
                    it.key.instanceId == instanceId
                } ?: return@forEach

                val spec = entry.value ?: return@forEach
                val range = spec.voltageRange ?: return@forEach

                if (domain.nominalVoltageV < range.minV - 0.01 ||
                    domain.nominalVoltageV > range.maxV + 0.01
                ) {
                    issues += critical(
                        "E_POWER_VOLTAGE_MISMATCH",
                        "${spec.displayName} is outside its verified supply-voltage range.",
                        setOf(instanceId),
                        spec.sourceIds,
                    )
                }
            }

            val loadMembers = domain.memberIds.filter { instanceId ->
                val spec = specsByInstance.entries
                    .firstOrNull { it.key.instanceId == instanceId }
                    ?.value
                spec?.supplyRole == SupplyRole.LOAD
            }

            if (loadMembers.isNotEmpty()) {
                if (domain.unknownCurrentMemberIds.isNotEmpty()) {
                    issues += critical(
                        "E_EXTERNAL_SUPPLY_CURRENT_UNKNOWN",
                        "External-load current is unknown; supply sizing cannot be verified.",
                        domain.unknownCurrentMemberIds,
                    )
                }

                val source = graph.power.sources.firstOrNull {
                    it.componentId != null &&
                        abs(it.nominalVoltageV - domain.nominalVoltageV) < 0.05
                }

                if (source == null) {
                    issues += critical(
                        "E_EXTERNAL_SUPPLY_REQUIRED",
                        "A verified external supply is required for the load domain.",
                        loadMembers.toSet(),
                    )
                } else {
                    val required = domain.maxRequiredCurrentMa * currentMarginMultiplier
                    val capacity = source.maxCurrentMa
                    if (capacity == null || capacity < required) {
                        issues += critical(
                            "E_POWER_CAPACITY_INSUFFICIENT",
                            "Power-source current capacity is below the required design margin.",
                            loadMembers.toSet(),
                        )
                    }
                }
            }
        }

        specsByInstance.forEach { (instance, specOrNull) ->
            val spec = specOrNull ?: return@forEach

            if (spec.requiredSupportTags.isNotEmpty()) {
                spec.requiredSupportTags.forEach { tag ->
                    val drivers = specsByInstance
                        .filterValues { it != null && tag in it.tags }
                        .mapNotNull { (driverInstance, driverSpec) ->
                            driverSpec?.let { driverInstance to it }
                        }

                    if (drivers.isEmpty()) {
                        issues += critical(
                            "E_MOTOR_DRIVER_REQUIRED",
                            "Load requires a verified driver stage.",
                            setOf(instance.instanceId),
                            spec.sourceIds,
                        )
                    } else {
                        spec.currentMaxMa?.let { loadCurrent ->
                            val capable = drivers.any { (_, driverSpec) ->
                                driverSpec.maxLoadCurrentMa?.let { it >= loadCurrent } == true
                            }
                            if (!capable) {
                                issues += critical(
                                    "E_DRIVER_CURRENT_EXCEEDED",
                                    "Selected driver current rating is insufficient for the load.",
                                    setOf(instance.instanceId) +
                                        drivers.map { it.first.instanceId },
                                    spec.sourceIds + drivers.flatMap { it.second.sourceIds },
                                )
                            }
                        }

                        drivers.forEach { (driverInstance, driverSpec) ->
                            driverSpec.minInputHighVoltageV?.let { minimum ->
                                if (boardSpec.logicVoltageV < minimum) {
                                    issues += critical(
                                        "E_LOGIC_LEVEL_INCOMPATIBLE",
                                        "Board logic voltage cannot guarantee the driver input HIGH level.",
                                        setOf(driverInstance.instanceId, graph.board.boardId),
                                        driverSpec.sourceIds + boardSpec.sourceIds,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        validateI2cAddresses(specsByInstance, issues)
        validateNoDirectDrivenLoad(graph, boardSpec, specsByInstance, issues)
        validateCommonGround(graph, boardSpec, specsByInstance, issues)

        val state = when {
            issues.any { it.severity == Severity.CRITICAL } -> ValidationState.BLOCKED
            issues.any { it.severity == Severity.WARNING } -> ValidationState.PASS_WITH_WARNING
            else -> ValidationState.PASS
        }

        return ValidationReport(state, issues)
    }

    private fun validateI2cAddresses(
        specs: Map<ComponentInstance, ComponentSpec?>,
        issues: MutableList<ValidationIssue>,
    ) {
        specs.entries
            .filter { it.value?.primaryInterface == ElectricalInterface.I2C }
            .filter { it.value?.i2cAddress != null }
            .groupBy { it.value!!.i2cAddress!! }
            .filterValues { it.size > 1 }
            .forEach { (address, entries) ->
                issues += critical(
                    "E_I2C_ADDRESS_COLLISION",
                    "Multiple I2C devices use address $address.",
                    entries.map { it.key.instanceId }.toSet(),
                    entries.flatMap { it.value!!.sourceIds }.toSet(),
                )
            }
    }

    private fun validateNoDirectDrivenLoad(
        graph: CircuitGraph,
        board: BoardSpec,
        specs: Map<ComponentInstance, ComponentSpec?>,
        issues: MutableList<ValidationIssue>,
    ) {
        val protectedLoads = specs
            .filterValues { it?.requiredSupportTags?.isNotEmpty() == true }
            .keys
            .map { it.instanceId }
            .toSet()

        graph.connections.forEach { connection ->
            val boardEndpoint = when {
                connection.from.entityId == graph.board.boardId -> connection.from
                connection.to.entityId == graph.board.boardId -> connection.to
                else -> null
            } ?: return@forEach

            val otherEntity = if (connection.from == boardEndpoint) {
                connection.to.entityId
            } else {
                connection.from.entityId
            }

            if (otherEntity !in protectedLoads) return@forEach

            val pin = board.pins.firstOrNull { it.pinId == boardEndpoint.pinId }
                ?: return@forEach

            if (
                BoardPinCapability.DIGITAL_OUT in pin.capabilities ||
                BoardPinCapability.PWM in pin.capabilities
            ) {
                issues += critical(
                    "E_GPIO_DIRECT_LOAD",
                    "High-current load must not be driven directly from a GPIO.",
                    setOf(otherEntity, graph.board.boardId),
                )
            }
        }
    }

    private fun validateCommonGround(
        graph: CircuitGraph,
        board: BoardSpec,
        specs: Map<ComponentInstance, ComponentSpec?>,
        issues: MutableList<ValidationIssue>,
    ) {
        val hasExternalLoad = specs.values.any {
            it?.supplyRole == SupplyRole.LOAD && it.requiresExternalPower
        }
        if (!hasExternalLoad) return

        val boardGnd = board.pins.firstOrNull {
            BoardPinCapability.GND in it.capabilities
        } ?: return

        val externalSources = graph.power.sources.filter { it.componentId != null }
        val grounded = externalSources.any { source ->
            graph.connections.any { connection ->
                connection.connects(
                    PinRef(source.id, "negative"),
                    PinRef(graph.board.boardId, boardGnd.pinId),
                )
            }
        }

        if (!grounded) {
            issues += critical(
                "E_MISSING_COMMON_GND",
                "External supply and MCU ground must share a common reference.",
                setOf(graph.board.boardId) + externalSources.map { it.id },
                board.sourceIds,
            )
        }
    }

    private fun Connection.connects(a: PinRef, b: PinRef): Boolean =
        (from == a && to == b) || (from == b && to == a)

    private fun critical(
        code: String,
        message: String,
        entities: Set<String>,
        sources: Set<String> = emptySet(),
    ) = ValidationIssue(
        code = code,
        severity = Severity.CRITICAL,
        message = message,
        entityIds = entities,
        sourceIds = sources,
    )
}
