package com.aielectronics.parts

import com.aielectronics.core.model.BoardPinCapability
import com.aielectronics.core.model.CapabilityId
import com.aielectronics.core.model.ComponentKind
import com.aielectronics.core.model.ComponentPinRole
import com.aielectronics.core.model.ComponentPinSpec
import com.aielectronics.core.model.ComponentSpec
import com.aielectronics.core.model.ElectricalInterface
import com.aielectronics.core.model.NetType
import com.aielectronics.core.model.SignalRequirement
import com.aielectronics.core.model.SupplyRole
import com.aielectronics.core.model.VoltageRange
import com.aielectronics.core.model.WireSemantic

enum class SourceAuthority {
    MANUFACTURER_DATASHEET,
    MANUFACTURER_PRODUCT_PAGE,
    AUTHORIZED_DISTRIBUTOR,
    OTHER,
}

data class ComponentSourceRecord(
    val url: String,
    val title: String = "",
    val authority: SourceAuthority = SourceAuthority.OTHER,
)

enum class RuntimeDriverFamily {
    GPIO_DIGITAL_INPUT,
    GPIO_DIGITAL_OUTPUT,
    DHT_PULSE_SENSOR,
    I2C_REGISTER_SENSOR,
    NONE,
}

data class RuntimeTelemetryMapping(
    val id: String,
    val unit: String? = null,
    val source: String,
    val scale: Double? = null,
    val offset: Double? = null,
)

data class RuntimeDriverProfile(
    val family: RuntimeDriverFamily,
    val sampleIntervalMs: Long? = null,
    val parameters: Map<String, String> = emptyMap(),
    val telemetry: List<RuntimeTelemetryMapping> = emptyList(),
)

data class RuntimeSupportRegistry(
    val supportedDriverIds: Set<String> = emptySet(),
    val supportedProfileFamilies: Set<RuntimeDriverFamily> = emptySet(),
) {
    fun supports(candidate: ComponentResearchCandidate): Boolean {
        val driverId = candidate.driverId?.trim().orEmpty()
        if (driverId.isNotEmpty()) {
            return driverId in supportedDriverIds
        }

        val profile = candidate.driverProfile ?: return !candidate.requiresRuntimeDriver()
        return when (profile.family) {
            RuntimeDriverFamily.NONE -> !candidate.requiresRuntimeDriver()
            else -> profile.family in supportedProfileFamilies
        }
    }
}

data class ComponentResearchCandidate(
    val requestedName: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val displayName: String,
    val kind: ComponentKind,
    val primaryInterface: ElectricalInterface? = null,
    val voltageMinV: Double? = null,
    val voltageTypicalV: Double? = null,
    val voltageMaxV: Double? = null,
    val preferredSupplyV: Double? = null,
    val currentMaxMa: Double? = null,
    val i2cAddress: String? = null,
    val requiresExternalPower: Boolean = false,
    val driverId: String? = null,
    val driverProfile: RuntimeDriverProfile? = null,
    val capabilities: Set<CapabilityId> = emptySet(),
    val aliases: Set<String> = emptySet(),
    val pins: List<ComponentPinSpec> = emptyList(),
    val sources: List<ComponentSourceRecord> = emptyList(),
    val confidence: Double = 0.0,
    val notes: List<String> = emptyList(),
    val requiredSupportTags: Set<String> = emptySet(),
    val directGpioDriveAllowed: Boolean? = null,
    val engineeringPriority: Int = 50,
) {
    fun canonicalId(): String {
        val preferred = listOfNotNull(
            manufacturer?.takeIf { it.isNotBlank() },
            model?.takeIf { it.isNotBlank() },
        ).joinToString("_").ifBlank { displayName.ifBlank { requestedName } }

        return preferred
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "researched_component" }
    }

    fun requiresRuntimeDriver(): Boolean =
        capabilities.any {
            it.value.startsWith("measure_") ||
                it.value.startsWith("sense_") ||
                it.value.startsWith("actuate_") ||
                it.value.startsWith("display_")
        } && kind !in setOf(ComponentKind.POWER_SUPPLY, ComponentKind.LEVEL_SHIFTER)

    fun toComponentSpec(designReady: Boolean): ComponentSpec {
        val voltageRange =
            if (voltageMinV != null && voltageMaxV != null) {
                VoltageRange(
                    minV = voltageMinV,
                    typicalV = voltageTypicalV ?: preferredSupplyV ?: voltageMinV,
                    maxV = voltageMaxV,
                )
            } else {
                null
            }

        return ComponentSpec(
            componentId = canonicalId(),
            displayName = displayName.ifBlank { requestedName },
            kind = kind,
            defaultRole = defaultRole(),
            providesCapabilities = capabilities,
            primaryInterface = primaryInterface,
            voltageRange = voltageRange,
            preferredSupplyVoltageV = preferredSupplyV ?: voltageTypicalV,
            supplyRole = supplyRole(),
            currentMaxMa = currentMaxMa,
            requiresExternalPower = requiresExternalPower,
            requiredSupportTags = requiredSupportTags,
            driverId = driverId,
            designReady = designReady,
            engineeringPriority = engineeringPriority,
            pins = pins,
            signalRequirements = signalRequirements(),
            i2cAddress = i2cAddress,
            sourceIds = sources.map { it.url }.filter { it.isNotBlank() }.toSet(),
        )
    }

    private fun defaultRole(): String = when (kind) {
        ComponentKind.SENSOR -> "sensor"
        ComponentKind.ACTUATOR -> "actuator"
        ComponentKind.DRIVER -> "load_driver"
        ComponentKind.DISPLAY -> "display"
        ComponentKind.POWER_SUPPLY -> "power_supply"
        ComponentKind.LEVEL_SHIFTER -> "level_shifter"
        ComponentKind.STORAGE -> "storage"
        ComponentKind.OTHER -> "component"
    }

    private fun supplyRole(): SupplyRole = when {
        kind == ComponentKind.ACTUATOR && requiresExternalPower -> SupplyRole.LOAD
        kind in setOf(ComponentKind.SENSOR, ComponentKind.DISPLAY, ComponentKind.STORAGE) ->
            SupplyRole.LOGIC
        else -> SupplyRole.NONE
    }

    private fun signalRequirements(): List<SignalRequirement> = buildList {
        if (primaryInterface == ElectricalInterface.I2C) {
            if (pins.any { it.role == ComponentPinRole.I2C_SDA }) {
                add(
                    SignalRequirement(
                        id = "sda",
                        boardCapability = BoardPinCapability.I2C_SDA,
                        componentPinRole = ComponentPinRole.I2C_SDA,
                        netType = NetType.I2C_SDA,
                        wireSemantic = WireSemantic.SIGNAL,
                        shareable = true,
                    )
                )
            }
            if (pins.any { it.role == ComponentPinRole.I2C_SCL }) {
                add(
                    SignalRequirement(
                        id = "scl",
                        boardCapability = BoardPinCapability.I2C_SCL,
                        componentPinRole = ComponentPinRole.I2C_SCL,
                        netType = NetType.I2C_SCL,
                        wireSemantic = WireSemantic.SIGNAL,
                        shareable = true,
                    )
                )
            }
        }

        if (
            primaryInterface in setOf(ElectricalInterface.GPIO, ElectricalInterface.PWM) &&
            pins.any { it.role == ComponentPinRole.CONTROL_INPUT }
        ) {
            add(
                SignalRequirement(
                    id = "control",
                    boardCapability =
                        if (primaryInterface == ElectricalInterface.PWM) {
                            BoardPinCapability.PWM
                        } else {
                            BoardPinCapability.DIGITAL_OUT
                        },
                    componentPinRole = ComponentPinRole.CONTROL_INPUT,
                    netType = NetType.CONTROL,
                    wireSemantic = WireSemantic.CONTROL,
                )
            )
        }
    }
}

enum class ComponentUsabilityStatus {
    READY,
    NEEDS_INFO,
    UNSUPPORTED,
}

data class ComponentUsabilityReport(
    val status: ComponentUsabilityStatus,
    val missingFields: Set<String> = emptySet(),
    val blockingReasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val acceptedSourceIds: Set<String> = emptySet(),
)

data class ResearchedComponentRecord(
    val canonicalId: String,
    val requestedName: String,
    val spec: ComponentSpec,
    val aliases: Set<String> = emptySet(),
    val sourceRecords: List<ComponentSourceRecord> = emptyList(),
    val confidence: Double = 0.0,
    val driverProfile: RuntimeDriverProfile? = null,
    val evaluation: ComponentUsabilityReport,
    val researchedAtEpochMs: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

fun interface ComponentUsabilityEvaluator {
    fun evaluate(
        candidate: ComponentResearchCandidate,
        requiredCapabilities: Set<CapabilityId>,
    ): ComponentUsabilityReport
}

class DefaultComponentUsabilityEvaluator(
    private val runtimeSupport: RuntimeSupportRegistry = RuntimeSupportRegistry(
        supportedDriverIds = setOf(
            "drv_sht31",
            "drv_sensirion_i2c_sht3x_arduino",
            "drv_gpio_sink",
            "drv_binary_output",
        ),
    ),
) : ComponentUsabilityEvaluator {

    override fun evaluate(
        candidate: ComponentResearchCandidate,
        requiredCapabilities: Set<CapabilityId>,
    ): ComponentUsabilityReport {
        val authoritativeSources = candidate.sources
            .filter {
                it.authority in setOf(
                    SourceAuthority.MANUFACTURER_DATASHEET,
                    SourceAuthority.MANUFACTURER_PRODUCT_PAGE,
                    SourceAuthority.AUTHORIZED_DISTRIBUTOR,
                )
            }
        val acceptedSourceIds = authoritativeSources
            .map { it.url }
            .filter { it.isNotBlank() }
            .toSet()

        if (isHazardousEnergy(candidate)) {
            return ComponentUsabilityReport(
                status = ComponentUsabilityStatus.UNSUPPORTED,
                blockingReasons = listOf(
                    "high-energy equipment requires the dedicated high-energy safety path"
                ),
                acceptedSourceIds = acceptedSourceIds,
            )
        }

        val missing = linkedSetOf<String>()
        val warnings = mutableListOf<String>()

        if (candidate.displayName.isBlank() && candidate.requestedName.isBlank()) {
            missing += "component_identity"
        }

        if (requiredCapabilities.isNotEmpty()) {
            val uncovered = requiredCapabilities - candidate.capabilities
            uncovered.sortedBy { it.value }.forEach {
                missing += "capability:${it.value}"
            }
        }

        validateVoltage(candidate, missing)
        validatePinsAndProtocol(candidate, missing)
        validateLoad(candidate, missing)

        if (authoritativeSources.isEmpty() && requiresCriticalElectricalEvidence(candidate)) {
            missing += "authoritative_source"
        }

        if (missing.isNotEmpty()) {
            return ComponentUsabilityReport(
                status = ComponentUsabilityStatus.NEEDS_INFO,
                missingFields = missing,
                warnings = warnings,
                acceptedSourceIds = acceptedSourceIds,
            )
        }

        if (!runtimeSupport.supports(candidate)) {
            return ComponentUsabilityReport(
                status = ComponentUsabilityStatus.UNSUPPORTED,
                blockingReasons = listOf(
                    "runtime does not support the required driver or driver profile"
                ),
                warnings = warnings,
                acceptedSourceIds = acceptedSourceIds,
            )
        }

        return ComponentUsabilityReport(
            status = ComponentUsabilityStatus.READY,
            warnings = warnings,
            acceptedSourceIds = acceptedSourceIds,
        )
    }

    private fun validateVoltage(
        candidate: ComponentResearchCandidate,
        missing: MutableSet<String>,
    ) {
        if (!requiresCriticalElectricalEvidence(candidate)) return

        val min = candidate.voltageMinV
        val max = candidate.voltageMaxV
        if (min == null) missing += "voltage_min_v"
        if (max == null) missing += "voltage_max_v"

        if (min != null && max != null && (min <= 0.0 || max <= 0.0 || min > max)) {
            missing += "valid_voltage_range"
        }

        val typical = candidate.voltageTypicalV
        if (
            min != null && max != null && typical != null &&
            (typical < min || typical > max)
        ) {
            missing += "valid_voltage_typical_v"
        }

        val preferred = candidate.preferredSupplyV
        if (
            min != null && max != null && preferred != null &&
            (preferred < min || preferred > max)
        ) {
            missing += "valid_preferred_supply_v"
        }
    }

    private fun validatePinsAndProtocol(
        candidate: ComponentResearchCandidate,
        missing: MutableSet<String>,
    ) {
        val roles = candidate.pins.map { it.role }.toSet()

        when (candidate.primaryInterface) {
            ElectricalInterface.I2C -> {
                requireRoles(
                    roles,
                    setOf(
                        ComponentPinRole.VCC,
                        ComponentPinRole.GND,
                        ComponentPinRole.I2C_SDA,
                        ComponentPinRole.I2C_SCL,
                    ),
                    missing,
                )
                if (candidate.i2cAddress.isNullOrBlank()) {
                    missing += "i2c_address"
                }
            }

            ElectricalInterface.ONE_WIRE -> {
                requireRoles(
                    roles,
                    setOf(
                        ComponentPinRole.VCC,
                        ComponentPinRole.GND,
                        ComponentPinRole.DATA,
                    ),
                    missing,
                )
                if (candidate.driverProfile?.family == RuntimeDriverFamily.DHT_PULSE_SENSOR) {
                    val params = candidate.driverProfile.parameters
                    listOf(
                        "variant",
                        "start_low_us",
                        "zero_high_max_us",
                        "one_high_min_us",
                    ).forEach { key ->
                        if (params[key].isNullOrBlank()) {
                            missing += "driver_profile.parameters.$key"
                        }
                    }
                    if ((candidate.driverProfile.sampleIntervalMs ?: 0L) <= 0L) {
                        missing += "driver_profile.sample_interval_ms"
                    }
                }
            }

            ElectricalInterface.GPIO,
            ElectricalInterface.PWM -> {
                if (
                    candidate.kind == ComponentKind.SENSOR &&
                    roles.none {
                        it == ComponentPinRole.SIGNAL_INPUT ||
                            it == ComponentPinRole.DATA
                    }
                ) {
                    missing += "signal_input_pin"
                }
            }

            ElectricalInterface.SPI,
            ElectricalInterface.UART,
            ElectricalInterface.ADC,
            ElectricalInterface.USB,
            ElectricalInterface.RS485,
            null -> Unit
        }
    }

    private fun validateLoad(
        candidate: ComponentResearchCandidate,
        missing: MutableSet<String>,
    ) {
        if (candidate.kind != ComponentKind.ACTUATOR) return

        if (candidate.currentMaxMa == null || candidate.currentMaxMa <= 0.0) {
            missing += "current_max_ma"
        }

        if (candidate.requiresExternalPower) {
            val roles = candidate.pins.map { it.role }.toSet()
            if (ComponentPinRole.POSITIVE !in roles) missing += "positive_pin"
            if (ComponentPinRole.NEGATIVE !in roles) missing += "negative_pin"

            if (
                candidate.directGpioDriveAllowed != true &&
                candidate.requiredSupportTags.isEmpty() &&
                candidate.driverId.isNullOrBlank()
            ) {
                missing += "drive_method"
            }
        }
    }

    private fun requireRoles(
        roles: Set<ComponentPinRole>,
        required: Set<ComponentPinRole>,
        missing: MutableSet<String>,
    ) {
        (required - roles).forEach {
            missing += "pin_role:${it.name}"
        }
    }

    private fun requiresCriticalElectricalEvidence(
        candidate: ComponentResearchCandidate,
    ): Boolean =
        candidate.kind != ComponentKind.OTHER ||
            candidate.primaryInterface != null ||
            candidate.capabilities.isNotEmpty()

    private fun isHazardousEnergy(candidate: ComponentResearchCandidate): Boolean {
        val text = buildString {
            append(candidate.requestedName)
            append(' ')
            append(candidate.displayName)
            append(' ')
            append(candidate.model.orEmpty())
            append(' ')
            append(candidate.notes.joinToString(" "))
        }.lowercase()

        val hazardousTerms = listOf(
            "100v",
            "200v",
            "230v",
            "240v",
            "mains",
            "three-phase",
            "three phase",
            "3-phase",
            "三相",
            "インバーター",
            "vfd",
            "contactor",
            "接触器",
            "solid state relay",
            "ssr",
        )
        if (hazardousTerms.any(text::contains)) return true

        return (candidate.voltageMaxV ?: 0.0) > 60.0
    }
}
