package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog
import kotlin.math.abs

class CatalogCircuitCompiler(
    private val catalog: EngineeringCatalog,
) : CircuitCompiler {

    override fun compile(
        board: BoardSelection,
        components: ResolvedComponents,
        power: PowerPlan,
        pins: List<PinAssignment>,
    ): Result<CircuitGraph> = runCatching {
        val boardSpec = catalog.board(board.boardId)
            ?: error("Board spec not found: ${board.boardId}")

        val builder = ConnectionBuilder()

        pins.forEach { assignment ->
            val target = assignment.target
                ?: error("Pin assignment has no target: ${assignment.logicalRole}")
            builder.add(
                assignment.pin,
                target,
                assignment.netType,
                assignment.wireSemantic,
            )
        }

        val boardGnd = boardPin(boardSpec, BoardPinCapability.GND)

        components.components.forEach { instance ->
            val spec = catalog.component(instance.componentId)
                ?: error("Component spec not found: ${instance.componentId}")

            if (spec.supplyRole == SupplyRole.LOGIC) {
                val voltage = spec.preferredSupplyVoltageV
                    ?: error("Supply voltage missing: ${spec.componentId}")
                val powerPin = boardPin(
                    boardSpec,
                    if (abs(voltage - 3.3) < 0.05) {
                        BoardPinCapability.POWER_3V3
                    } else {
                        BoardPinCapability.POWER_5V
                    }
                )

                componentPin(instance, spec, ComponentPinRole.VCC)?.let { vcc ->
                    builder.add(
                        PinRef(board.boardId, powerPin.pinId),
                        vcc,
                        NetType.POWER,
                        WireSemantic.POWER_POSITIVE,
                        voltage,
                    )
                }

                componentPin(instance, spec, ComponentPinRole.GND)?.let { gnd ->
                    builder.add(
                        PinRef(board.boardId, boardGnd.pinId),
                        gnd,
                        NetType.GROUND,
                        WireSemantic.GROUND,
                        0.0,
                    )
                }
            }
        }

        val selectedSpecs = components.components.associateWith { instance ->
            catalog.component(instance.componentId)
                ?: error("Component spec not found: ${instance.componentId}")
        }

        selectedSpecs
            .filterValues { it.supplyRole == SupplyRole.LOAD }
            .forEach { (loadInstance, loadSpec) ->
                val voltage = loadSpec.preferredSupplyVoltageV
                    ?: error("Load voltage missing: ${loadSpec.componentId}")
                val source = externalSource(power, voltage)

                componentPin(loadInstance, loadSpec, ComponentPinRole.POSITIVE)?.let { positive ->
                    builder.add(
                        PinRef(source.id, "positive"),
                        positive,
                        NetType.POWER,
                        WireSemantic.POWER_POSITIVE,
                        voltage,
                    )
                }

                val requiredTag = loadSpec.requiredSupportTags.firstOrNull()
                    ?: return@forEach

                val driverEntry = selectedSpecs.entries.firstOrNull { (_, spec) ->
                    requiredTag in spec.tags
                } ?: error("Required load driver not selected: $requiredTag")

                val driverInstance = driverEntry.key
                val driverSpec = driverEntry.value

                val driverOutput = componentPin(
                    driverInstance,
                    driverSpec,
                    ComponentPinRole.LOAD_OUTPUT,
                ) ?: error("Driver output pin missing: ${driverSpec.componentId}")

                val loadNegative = componentPin(
                    loadInstance,
                    loadSpec,
                    ComponentPinRole.NEGATIVE,
                ) ?: error("Load negative pin missing: ${loadSpec.componentId}")

                builder.add(
                    driverOutput,
                    loadNegative,
                    NetType.LOAD,
                    WireSemantic.CONTROL,
                    voltage,
                )

                componentPin(driverInstance, driverSpec, ComponentPinRole.CLAMP_COMMON)?.let { common ->
                    builder.add(
                        PinRef(source.id, "positive"),
                        common,
                        NetType.POWER,
                        WireSemantic.POWER_POSITIVE,
                        voltage,
                    )
                }

                componentPin(driverInstance, driverSpec, ComponentPinRole.GND)?.let { driverGnd ->
                    builder.add(
                        PinRef(source.id, "negative"),
                        driverGnd,
                        NetType.GROUND,
                        WireSemantic.GROUND,
                        0.0,
                    )
                }

                builder.add(
                    PinRef(source.id, "negative"),
                    PinRef(board.boardId, boardGnd.pinId),
                    NetType.GROUND,
                    WireSemantic.GROUND,
                    0.0,
                )
            }

        CircuitGraph(
            board = board,
            components = components.components,
            power = power,
            connections = builder.connections,
        )
    }

    private fun boardPin(
        board: BoardSpec,
        capability: BoardPinCapability,
    ): BoardPinSpec =
        board.pins.firstOrNull { capability in it.capabilities }
            ?: error("Board pin missing: ${board.boardId} / $capability")

    private fun componentPin(
        instance: ComponentInstance,
        spec: ComponentSpec,
        role: ComponentPinRole,
    ): PinRef? =
        spec.pins.firstOrNull { it.role == role }
            ?.let { PinRef(instance.instanceId, it.pinId) }

    private fun externalSource(power: PowerPlan, voltage: Double): PowerSource =
        power.sources.firstOrNull {
            it.componentId != null && abs(it.nominalVoltageV - voltage) < 0.05
        } ?: error("External power source missing for ${voltage}V")

    private class ConnectionBuilder {
        private val seen = mutableSetOf<String>()
        val connections = mutableListOf<Connection>()

        fun add(
            from: PinRef,
            to: PinRef,
            netType: NetType,
            wireSemantic: WireSemantic,
            voltageV: Double? = null,
        ) {
            val endpointKey = listOf(
                "${from.entityId}:${from.pinId}",
                "${to.entityId}:${to.pinId}",
            ).sorted().joinToString("<->")

            if (!seen.add(endpointKey)) return

            connections += Connection(
                id = "conn_${(connections.size + 1).toString().padStart(3, '0')}",
                from = from,
                to = to,
                netType = netType,
                wireSemantic = wireSemantic,
                voltageV = voltageV,
            )
        }
    }
}
