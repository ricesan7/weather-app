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
    private val KNOWN_BUILT_IN_DRIVER_IDS = setOf(
        "drv_sht31",
        "drv_gpio_sink",
        "drv_binary_output",
    )

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
    
        val proposedDriverId =
            json.optString("driver_id")
                .trim()
                .takeIf {
                    it in KNOWN_BUILT_IN_DRIVER_IDS
                }

        val driverProfile =
            parseDriverProfile(
                json = json.optJSONObject("driver_profile"),
                manufacturer = manufacturer,
                model = model,
                kind = kind,
                primaryInterface = primaryInterface,
                sources = sources,
            )
        val driverId =
            proposedDriverId
                ?: driverProfile
                    ?.takeIf { it.runtimeReady }
                    ?.driverId
    
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
    
        if (
            kind in setOf(
                ComponentKind.SENSOR,
                ComponentKind.DISPLAY,
            ) &&
            driverId == null
        ) {
            missing += "runtime_driver"
        }

        val requestedProfileFamily =
            json.optJSONObject("driver_profile")
                ?.optString("family")
                .orEmpty()
        if (
            requestedProfileFamily.isNotBlank() &&
            requestedProfileFamily != "NONE" &&
            driverProfile == null
        ) {
            missing += "runtime_driver_profile"
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
                    runtimeDriverProfile = driverProfile,
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
                if ("runtime_driver_profile" in missing) {
                    add(
                        "Driver Profile候補は返されましたが、" +
                            "Runtimeの安全検証条件を満たしていません。"
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
    
    private fun parseDriverProfile(
        json: JSONObject?,
        manufacturer: String,
        model: String,
        kind: ComponentKind,
        primaryInterface: ElectricalInterface?,
        sources: List<ComponentResearchSource>,
    ): RuntimeDriverProfile? {
        if (json == null) return null

        val familyName = json.optString("family").trim()
        if (familyName.isBlank() || familyName == "NONE") {
            return null
        }

        val family =
            runCatching {
                RuntimeDriverFamily.valueOf(familyName)
            }.getOrNull() ?: return null

        val sampleIntervalMs =
            json.optInt("sample_interval_ms", 0)
        if (sampleIntervalMs !in 20..60_000) {
            return null
        }

        val parameters = linkedMapOf<String, String>()
        json.optJSONArray("parameters")
            ?.objects()
            ?.take(24)
            ?.forEach { item ->
                val key =
                    item.optString("key")
                        .trim()
                        .lowercase(Locale.US)
                val value =
                    item.optString("value")
                        .trim()
                if (
                    key.matches(
                        Regex("""[a-z0-9_]{1,48}""")
                    ) &&
                    value.length <= 120
                ) {
                    parameters[key] = value
                }
            }

        val telemetry =
            json.optJSONArray("telemetry")
                ?.objects()
                ?.take(12)
                ?.mapNotNull { item ->
                    val id =
                        item.optString("id").trim()
                    val source =
                        item.optString("source").trim()
                    val unit =
                        item.optString("unit").trim()
                    val scale =
                        item.optDouble("scale", 1.0)
                    val offset =
                        item.optDouble("offset", 0.0)

                    if (
                        !id.matches(
                            Regex("""[a-z][a-z0-9_]{0,47}""")
                        ) ||
                        source.isBlank() ||
                        !scale.isFinite() ||
                        !offset.isFinite()
                    ) {
                        null
                    } else {
                        RuntimeDriverTelemetrySpec(
                            id = id,
                            unit = unit.take(24),
                            source = source,
                            scale = scale,
                            offset = offset,
                        )
                    }
                }
                .orEmpty()

        val runtimeReady =
            when (family) {
                RuntimeDriverFamily.DHT_PULSE_SENSOR ->
                    validateDhtProfile(
                        kind = kind,
                        primaryInterface =
                            primaryInterface,
                        sampleIntervalMs =
                            sampleIntervalMs,
                        parameters = parameters,
                        telemetry = telemetry,
                    )

                RuntimeDriverFamily.GPIO_DIGITAL_INPUT ->
                    primaryInterface ==
                        ElectricalInterface.GPIO &&
                        kind in setOf(
                            ComponentKind.SENSOR,
                            ComponentKind.OTHER,
                        ) &&
                        telemetry.any {
                            it.source == "DIGITAL_STATE"
                        }

                RuntimeDriverFamily.GPIO_DIGITAL_OUTPUT ->
                    primaryInterface ==
                        ElectricalInterface.GPIO &&
                        kind in setOf(
                            ComponentKind.ACTUATOR,
                            ComponentKind.DRIVER,
                            ComponentKind.OTHER,
                        )

                RuntimeDriverFamily.I2C_REGISTER_SENSOR ->
                    false
            }

        return RuntimeDriverProfile(
            driverId =
                "profile_" +
                    family.name.lowercase(Locale.US) +
                    "_" +
                    slug(
                        listOf(
                            manufacturer,
                            model,
                        )
                            .filter { it.isNotBlank() }
                            .joinToString("_")
                            .ifBlank { "researched" }
                    ),
            family = family,
            interfaceType =
                primaryInterface ?: return null,
            sampleIntervalMs = sampleIntervalMs,
            parameters = parameters,
            telemetry = telemetry,
            sourceIds =
                sources.map { it.url }.toSet(),
            status =
                if (runtimeReady) {
                    RuntimeDriverProfileStatus.RUNTIME_READY
                } else {
                    RuntimeDriverProfileStatus.VALIDATED
                },
        )
    }

    private fun validateDhtProfile(
        kind: ComponentKind,
        primaryInterface: ElectricalInterface?,
        sampleIntervalMs: Int,
        parameters: Map<String, String>,
        telemetry: List<RuntimeDriverTelemetrySpec>,
    ): Boolean {
        if (
            kind != ComponentKind.SENSOR ||
            primaryInterface !=
                ElectricalInterface.ONE_WIRE ||
            sampleIntervalMs < 1000
        ) {
            return false
        }

        val variant =
            parameters["variant"]
                ?.uppercase(Locale.US)
        if (variant !in setOf("DHT11", "DHT22")) {
            return false
        }

        val startLowUs =
            parameters["start_low_us"]
                ?.toIntOrNull()
                ?: return false
        val zeroHighMaxUs =
            parameters["zero_high_max_us"]
                ?.toIntOrNull()
                ?: return false
        val oneHighMinUs =
            parameters["one_high_min_us"]
                ?.toIntOrNull()
                ?: return false

        if (
            startLowUs !in 800..25_000 ||
            zeroHighMaxUs !in 20..55 ||
            oneHighMinUs !in 45..90 ||
            oneHighMinUs <= zeroHighMaxUs
        ) {
            return false
        }

        val telemetrySources =
            telemetry.map { it.source }.toSet()
        return (
            "DHT_TEMPERATURE" in telemetrySources &&
                "DHT_HUMIDITY" in telemetrySources
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
