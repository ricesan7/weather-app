package com.aielectronics.builder

import com.aielectronics.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private fun componentResearchEndpoint(
    configured: String,
): String {
    val trimmed = configured.trim().trimEnd('/')
    return when {
        trimmed.endsWith("/v1/revision-chat") ->
            trimmed.removeSuffix(
                "/v1/revision-chat"
            ) +
                "/v1/component-research"

        trimmed.endsWith("/v1/component-research") ->
            trimmed

        else ->
            trimmed + "/v1/component-research"
    }
}

interface ComponentResearchClient {
    suspend fun research(
        request: ComponentResearchRequest,
    ): Result<ComponentResearchRecord>
}

class GatewayComponentResearchClient(
    revisionEndpoint: String,
    private val gatewayToken: String = "",
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 90_000,
) : ComponentResearchClient {

    private val endpoint: String =
        componentResearchEndpoint(revisionEndpoint)

    override suspend fun research(
        request: ComponentResearchRequest,
    ): Result<ComponentResearchRecord> = runCatching {
        require(
            endpoint.startsWith("https://") ||
                endpoint.startsWith("http://localhost")
        ) {
            "部品ResearchゲートウェイURLはHTTPSで指定してください。"
        }

        val body = JSONObject().apply {
            put(
                "requested_name",
                request.requested.rawName,
            )
            put(
                "category_hint",
                request.requested.categoryHint.orEmpty(),
            )
            put(
                "required_capabilities",
                JSONArray().apply {
                    request.requiredCapabilities.forEach {
                        put(it.value)
                    }
                },
            )
            put("project_goal", request.projectGoal)
        }

        val connection =
            (URL(endpoint).openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8",
                    )
                    setRequestProperty(
                        "Accept",
                        "application/json",
                    )
                    if (gatewayToken.isNotBlank()) {
                        setRequestProperty(
                            "X-AI-Gateway-Token",
                            gatewayToken,
                        )
                    }
                }

        try {
            connection.outputStream
                .bufferedWriter(Charsets.UTF_8)
                .use { writer ->
                    writer.write(body.toString())
                }

            val status = connection.responseCode
            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            val responseText =
                stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

            if (status !in 200..299) {
                error(
                    "部品Research APIがHTTP " +
                        status +
                        "を返しました。"
                )
            }

            ComponentResearchResponseValidator.parse(
                request = request,
                json = JSONObject(responseText),
            )
        } finally {
            connection.disconnect()
        }
    }

}

internal object ComponentResearchResponseValidator {
    fun parse(
        request: ComponentResearchRequest,
        json: JSONObject,
    ): ComponentResearchRecord {
        val manufacturer =
            json.optString("manufacturer").trim()
        val model = json.optString("model").trim()
        val displayName =
            json.optString("display_name").trim()
        val kind =
            runCatching {
                ComponentKind.valueOf(
                    json.getString("kind")
                )
            }.getOrDefault(ComponentKind.OTHER)
        val interfaceName =
            json.optString("primary_interface")
        val primaryInterface =
            if (interfaceName == "UNKNOWN") {
                null
            } else {
                runCatching {
                    ElectricalInterface.valueOf(interfaceName)
                }.getOrNull()
            }
    
        val sources =
            json.optJSONArray("sources")
                ?.objects()
                ?.mapNotNull { source ->
                    val url =
                        source.optString("url").trim()
                    if (!url.startsWith("https://")) {
                        return@mapNotNull null
                    }
                    ComponentResearchSource(
                        url = url,
                        title =
                            source.optString("title"),
                        authority =
                            runCatching {
                                ResearchSourceAuthority.valueOf(
                                    source.getString(
                                        "authority"
                                    )
                                )
                            }.getOrDefault(
                                ResearchSourceAuthority.OTHER
                            ),
                    )
                }
                .orEmpty()
    
        val hasOfficialEvidence =
            json.optBoolean(
                "evidence_complete",
                false,
            ) &&
                sources.any {
                    it.authority ==
                        ResearchSourceAuthority.MANUFACTURER_DATASHEET ||
                        it.authority ==
                        ResearchSourceAuthority.MANUFACTURER_PRODUCT_PAGE
                }
    
        val capabilities =
            json.optJSONArray("capabilities")
                ?.strings()
                ?.filter { it.isNotBlank() }
                ?.map(::CapabilityId)
                ?.toSet()
                .orEmpty()
    
        val rawPins =
            json.optJSONArray("pins")
                ?.objects()
                .orEmpty()
    
        val pins =
            rawPins.mapIndexedNotNull { index, pin ->
                val label =
                    pin.optString("label").trim()
                val role =
                    runCatching {
                        ComponentPinRole.valueOf(
                            pin.getString("role")
                        )
                    }.getOrNull()
                if (label.isBlank() || role == null) {
                    null
                } else {
                    ComponentPinSpec(
                        pinId =
                            "cp_" +
                                slug(
                                    model.ifBlank {
                                        request.requested.rawName
                                    }
                                ) +
                                "_" +
                                (index + 1),
                        label = label,
                        role = role,
                    )
                }
            }
    
        val minV = json.nullableDouble("voltage_min_v")
        val typicalV =
            json.nullableDouble("voltage_typical_v")
        val maxV = json.nullableDouble("voltage_max_v")
        val voltage =
            if (
                minV != null &&
                typicalV != null &&
                maxV != null &&
                minV <= typicalV &&
                typicalV <= maxV
            ) {
                VoltageRange(minV, typicalV, maxV)
            } else {
                null
            }
    
        val driverId =
            json.optString("driver_id")
                .trim()
                .takeIf { it.isNotBlank() }
    
        val i2cAddress =
            json.optString("i2c_address")
                .trim()
                .takeIf { it.isNotBlank() }
    
        val missing = linkedSetOf<String>()
        if (!hasOfficialEvidence) {
            missing += "manufacturer_evidence"
        }
        if (manufacturer.isBlank()) {
            missing += "manufacturer"
        }
        if (model.isBlank()) {
            missing += "model"
        }
        if (displayName.isBlank()) {
            missing += "display_name"
        }
        if (primaryInterface == null) {
            missing += "primary_interface"
        }
        if (voltage == null && kind != ComponentKind.OTHER) {
            missing += "voltage_range"
        }
        if (pins.isEmpty()) {
            missing += "pins"
        }
        if (capabilities.isEmpty()) {
            missing += "capabilities"
        }
        if (
            primaryInterface == ElectricalInterface.I2C &&
            i2cAddress == null
        ) {
            missing += "i2c_address"
        }
    
        val simpleGpioInput =
            primaryInterface == ElectricalInterface.GPIO &&
                kind in setOf(
                    ComponentKind.SENSOR,
                    ComponentKind.OTHER,
                ) &&
                pins.any {
                    it.role in setOf(
                        ComponentPinRole.SIGNAL_OUTPUT,
                        ComponentPinRole.DATA,
                    )
                }
    
        if (
            kind in setOf(
                ComponentKind.SENSOR,
                ComponentKind.DISPLAY,
            ) &&
            !simpleGpioInput &&
            driverId == null
        ) {
            missing += "runtime_driver"
        }
    
        val hazardous =
            (maxV ?: 0.0) > 60.0 ||
                request.projectGoal.containsHazardousEnergyTerm() ||
                request.requested.rawName
                    .containsHazardousEnergyTerm()
    
        val confidence =
            json.optDouble("confidence", 0.0)
                .coerceIn(0.0, 1.0)
    
        val status =
            when {
                hasOfficialEvidence &&
                    missing.isEmpty() &&
                    !hazardous &&
                    confidence >= 0.80 ->
                    ComponentVerificationStatus.DESIGN_READY
    
                hasOfficialEvidence &&
                    manufacturer.isNotBlank() &&
                    model.isNotBlank() ->
                    ComponentVerificationStatus.VERIFIED
    
                sources.isNotEmpty() ->
                    ComponentVerificationStatus.EXTRACTED
    
                else ->
                    ComponentVerificationStatus.DISCOVERED
            }
    
        val component =
            if (
                manufacturer.isNotBlank() &&
                model.isNotBlank() &&
                displayName.isNotBlank()
            ) {
                ComponentSpec(
                    componentId =
                        "research_" +
                            slug(manufacturer) +
                            "_" +
                            slug(model),
                    displayName = displayName,
                    kind = kind,
                    defaultRole =
                        roleFor(kind, model),
                    providesCapabilities =
                        capabilities +
                            request.requiredCapabilities,
                    primaryInterface = primaryInterface,
                    voltageRange = voltage,
                    preferredSupplyVoltageV =
                        json.nullableDouble(
                            "preferred_supply_v"
                        ),
                    supplyRole = when (kind) {
                        ComponentKind.ACTUATOR ->
                            SupplyRole.LOAD
                        ComponentKind.POWER_SUPPLY,
                        ComponentKind.OTHER ->
                            SupplyRole.NONE
                        else ->
                            SupplyRole.LOGIC
                    },
                    currentMaxMa =
                        json.nullableDouble(
                            "current_max_ma"
                        ),
                    requiresExternalPower =
                        json.optBoolean(
                            "requires_external_power",
                            false,
                        ),
                    tags =
                        buildSet {
                            add("researched")
                            if (hazardous) {
                                add("hazardous_energy")
                            }
                        },
                    driverId = driverId,
                    designReady =
                        status ==
                            ComponentVerificationStatus.DESIGN_READY,
                    engineeringPriority = 70,
                    pins = pins,
                    signalRequirements =
                        signalRequirements(
                            primaryInterface,
                            pins,
                        ),
                    i2cAddress = i2cAddress,
                    sourceIds =
                        sources.map { it.url }.toSet(),
                    aliases =
                        buildSet {
                            add(request.requested.rawName)
                            add(model)
                            json.optJSONArray("aliases")
                                ?.strings()
                                ?.forEach(::add)
                        },
                    verificationStatus = status,
                )
            } else {
                null
            }
    
        val notes =
            buildList {
                json.optJSONArray("notes")
                    ?.strings()
                    ?.let(::addAll)
                if (hazardous) {
                    add(
                        "危険電圧・産業制御系の可能性があるため" +
                            "自動DESIGN_READY昇格を禁止しました。"
                    )
                }
                if ("runtime_driver" in missing) {
                    add(
                        "電気仕様は確認できましたが、" +
                            "対応Runtime Driverが未登録です。"
                    )
                }
            }
    
        return ComponentResearchRecord(
            requestId = request.requestId,
            requestedName =
                request.requested.rawName,
            manufacturer =
                manufacturer.takeIf {
                    it.isNotBlank()
                },
            model =
                model.takeIf { it.isNotBlank() },
            component = component,
            status = status,
            sources = sources,
            missingFields = missing.toList(),
            notes = notes,
            researchedAtEpochMs =
                System.currentTimeMillis(),
        )
    }
    
    private fun signalRequirements(
        primaryInterface: ElectricalInterface?,
        pins: List<ComponentPinSpec>,
    ): List<SignalRequirement> =
        pins.mapNotNull { pin ->
            when (pin.role) {
                ComponentPinRole.I2C_SDA ->
                    SignalRequirement(
                        id = pin.pinId,
                        boardCapability =
                            BoardPinCapability.I2C_SDA,
                        componentPinRole = pin.role,
                        netType = NetType.I2C_SDA,
                        wireSemantic =
                            WireSemantic.SIGNAL,
                        shareable = true,
                    )
    
                ComponentPinRole.I2C_SCL ->
                    SignalRequirement(
                        id = pin.pinId,
                        boardCapability =
                            BoardPinCapability.I2C_SCL,
                        componentPinRole = pin.role,
                        netType = NetType.I2C_SCL,
                        wireSemantic =
                            WireSemantic.SIGNAL,
                        shareable = true,
                    )
    
                ComponentPinRole.DATA ->
                    SignalRequirement(
                        id = pin.pinId,
                        boardCapability =
                            if (
                                primaryInterface ==
                                    ElectricalInterface.ONE_WIRE
                            ) {
                                BoardPinCapability.DIGITAL_IO
                            } else {
                                BoardPinCapability.DIGITAL_IN
                            },
                        componentPinRole = pin.role,
                        netType = NetType.DIGITAL,
                        wireSemantic =
                            WireSemantic.SIGNAL,
                    )
    
                ComponentPinRole.SIGNAL_OUTPUT ->
                    SignalRequirement(
                        id = pin.pinId,
                        boardCapability =
                            BoardPinCapability.DIGITAL_IN,
                        componentPinRole = pin.role,
                        netType = NetType.DIGITAL,
                        wireSemantic =
                            WireSemantic.SIGNAL,
                    )
    
                ComponentPinRole.SIGNAL_INPUT,
                ComponentPinRole.CONTROL_INPUT ->
                    SignalRequirement(
                        id = pin.pinId,
                        boardCapability =
                            BoardPinCapability.DIGITAL_OUT,
                        componentPinRole = pin.role,
                        netType = NetType.CONTROL,
                        wireSemantic =
                            WireSemantic.CONTROL,
                    )
    
                else -> null
            }
        }
    
    private fun roleFor(
        kind: ComponentKind,
        model: String,
    ): String =
        when (kind) {
            ComponentKind.SENSOR -> "sensor_" + slug(model)
            ComponentKind.DISPLAY -> "display_" + slug(model)
            ComponentKind.ACTUATOR -> "actuator_" + slug(model)
            ComponentKind.DRIVER -> "driver_" + slug(model)
            ComponentKind.POWER_SUPPLY -> "power_" + slug(model)
            else -> "component_" + slug(model)
        }
    
    private fun componentResearchEndpoint(
        configured: String,
    ): String {
        val trimmed = configured.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/v1/revision-chat") ->
                trimmed.removeSuffix(
                    "/v1/revision-chat"
                ) +
                    "/v1/component-research"
    
            trimmed.endsWith("/v1/component-research") ->
                trimmed
    
            else ->
                trimmed + "/v1/component-research"
        }
    }
    
    private fun slug(value: String): String =
        value.lowercase(Locale.US)
            .replace(
                Regex("""[^a-z0-9]+"""),
                "_",
            )
            .trim('_')
            .ifBlank { "component" }
    
    private fun String.containsHazardousEnergyTerm(): Boolean {
        val normalized = lowercase()
        return listOf(
            "100v",
            "200v",
            "400v",
            "ac100",
            "ac200",
            "三相",
            "商用電源",
            "インバーター",
            "vfd",
            "contactor",
            "電磁接触器",
        ).any(normalized::contains)
    }
    
    private fun JSONArray.strings(): List<String> =
        buildList {
            for (index in 0 until length()) {
                add(getString(index))
            }
        }
    
    private fun JSONArray.objects(): List<JSONObject> =
        buildList {
            for (index in 0 until length()) {
                add(getJSONObject(index))
            }
        }
    
    private fun JSONObject.nullableDouble(
        key: String,
    ): Double? =
        if (!has(key) || isNull(key)) {
            null
        } else {
            getDouble(key)
        }
}
