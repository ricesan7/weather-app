package com.aielectronics.storage.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aielectronics.core.model.*
import com.aielectronics.parts.ComponentResearchStore
import org.json.JSONArray
import org.json.JSONObject

class SqliteComponentResearchStore(
    context: Context,
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
), ComponentResearchStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE researched_components (
                request_id TEXT PRIMARY KEY NOT NULL,
                requested_name TEXT NOT NULL,
                status TEXT NOT NULL,
                record_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX researched_components_status_idx " +
                "ON researched_components(status, updated_at DESC)"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 1) onCreate(db)
    }

    @Synchronized
    override fun researchRecords(): List<ComponentResearchRecord> {
        val result = mutableListOf<ComponentResearchRecord>()
        readableDatabase.query(
            "researched_components",
            arrayOf("record_json"),
            null,
            null,
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            val jsonIndex =
                cursor.getColumnIndexOrThrow("record_json")
            while (cursor.moveToNext()) {
                runCatching {
                    decodeRecord(cursor.getString(jsonIndex))
                }.getOrNull()?.let(result::add)
            }
        }
        return result
    }

    @Synchronized
    override fun researchedComponents(): List<ComponentSpec> =
        researchRecords()
            .asSequence()
            .filter {
                it.status ==
                    ComponentVerificationStatus.DESIGN_READY
            }
            .mapNotNull { it.component }
            .filter { it.designReady }
            .toList()

    @Synchronized
    override fun saveResearchRecord(
        record: ComponentResearchRecord,
    ) {
        val values = ContentValues().apply {
            put("request_id", record.requestId)
            put("requested_name", record.requestedName)
            put("status", record.status.name)
            put("record_json", encodeRecord(record))
            put(
                "updated_at",
                if (record.researchedAtEpochMs > 0L) {
                    record.researchedAtEpochMs
                } else {
                    System.currentTimeMillis()
                },
            )
        }

        writableDatabase.insertWithOnConflict(
            "researched_components",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun encodeRecord(
        record: ComponentResearchRecord,
    ): String =
        JSONObject().apply {
            put("requestId", record.requestId)
            put("requestedName", record.requestedName)
            put("manufacturer", record.manufacturer)
            put("model", record.model)
            put("status", record.status.name)
            put(
                "sources",
                JSONArray().apply {
                    record.sources.forEach { source ->
                        put(
                            JSONObject().apply {
                                put("url", source.url)
                                put("title", source.title)
                                put(
                                    "authority",
                                    source.authority.name,
                                )
                            }
                        )
                    }
                }
            )
            put("missingFields", stringArray(record.missingFields))
            put("notes", stringArray(record.notes))
            put("researchedAtEpochMs", record.researchedAtEpochMs)
            record.component?.let {
                put("component", encodeComponent(it))
            }
        }.toString()

    private fun decodeRecord(
        raw: String,
    ): ComponentResearchRecord {
        val json = JSONObject(raw)
        return ComponentResearchRecord(
            requestId = json.getString("requestId"),
            requestedName = json.getString("requestedName"),
            manufacturer =
                json.optString("manufacturer")
                    .takeIf { it.isNotBlank() },
            model =
                json.optString("model")
                    .takeIf { it.isNotBlank() },
            component =
                json.optJSONObject("component")
                    ?.let(::decodeComponent),
            status =
                ComponentVerificationStatus.valueOf(
                    json.getString("status")
                ),
            sources =
                json.optJSONArray("sources")
                    ?.objects()
                    ?.map { source ->
                        ComponentResearchSource(
                            url = source.getString("url"),
                            title =
                                source.optString("title"),
                            authority =
                                ResearchSourceAuthority.valueOf(
                                    source.getString("authority")
                                ),
                        )
                    }
                    .orEmpty(),
            missingFields =
                json.optJSONArray("missingFields")
                    ?.strings()
                    .orEmpty(),
            notes =
                json.optJSONArray("notes")
                    ?.strings()
                    .orEmpty(),
            researchedAtEpochMs =
                json.optLong("researchedAtEpochMs", 0L),
        )
    }

    private fun encodeComponent(
        spec: ComponentSpec,
    ): JSONObject =
        JSONObject().apply {
            put("componentId", spec.componentId)
            put("displayName", spec.displayName)
            put("kind", spec.kind.name)
            put("defaultRole", spec.defaultRole)
            put(
                "providesCapabilities",
                stringArray(
                    spec.providesCapabilities.map { it.value }
                ),
            )
            put("primaryInterface", spec.primaryInterface?.name)
            spec.voltageRange?.let { range ->
                put(
                    "voltageRange",
                    JSONObject().apply {
                        put("minV", range.minV)
                        put("typicalV", range.typicalV)
                        put("maxV", range.maxV)
                    }
                )
            }
            put(
                "preferredSupplyVoltageV",
                spec.preferredSupplyVoltageV,
            )
            put("supplyRole", spec.supplyRole.name)
            put("currentMaxMa", spec.currentMaxMa)
            put(
                "requiresExternalPower",
                spec.requiresExternalPower,
            )
            put(
                "requiredSupportTags",
                stringArray(spec.requiredSupportTags),
            )
            put("tags", stringArray(spec.tags))
            put("driverId", spec.driverId)
            put("designReady", spec.designReady)
            put(
                "engineeringPriority",
                spec.engineeringPriority,
            )
            put(
                "pins",
                JSONArray().apply {
                    spec.pins.forEach { pin ->
                        put(
                            JSONObject().apply {
                                put("pinId", pin.pinId)
                                put("label", pin.label)
                                put("role", pin.role.name)
                            }
                        )
                    }
                },
            )
            put(
                "signalRequirements",
                JSONArray().apply {
                    spec.signalRequirements.forEach { req ->
                        put(
                            JSONObject().apply {
                                put("id", req.id)
                                put(
                                    "boardCapability",
                                    req.boardCapability.name,
                                )
                                put(
                                    "componentPinRole",
                                    req.componentPinRole.name,
                                )
                                put("netType", req.netType.name)
                                put(
                                    "wireSemantic",
                                    req.wireSemantic.name,
                                )
                                put("shareable", req.shareable)
                            }
                        )
                    }
                },
            )
            put("i2cAddress", spec.i2cAddress)
            put(
                "maxLoadCurrentMa",
                spec.maxLoadCurrentMa,
            )
            put(
                "minInputHighVoltageV",
                spec.minInputHighVoltageV,
            )
            put("sourceIds", stringArray(spec.sourceIds))
            put("aliases", stringArray(spec.aliases))
            put(
                "verificationStatus",
                spec.verificationStatus.name,
            )
        }

    private fun decodeComponent(
        json: JSONObject,
    ): ComponentSpec {
        val voltage =
            json.optJSONObject("voltageRange")?.let {
                VoltageRange(
                    minV = it.getDouble("minV"),
                    typicalV = it.getDouble("typicalV"),
                    maxV = it.getDouble("maxV"),
                )
            }

        return ComponentSpec(
            componentId = json.getString("componentId"),
            displayName = json.getString("displayName"),
            kind =
                ComponentKind.valueOf(
                    json.getString("kind")
                ),
            defaultRole = json.getString("defaultRole"),
            providesCapabilities =
                json.getJSONArray("providesCapabilities")
                    .strings()
                    .map(::CapabilityId)
                    .toSet(),
            primaryInterface =
                json.optString("primaryInterface")
                    .takeIf { it.isNotBlank() }
                    ?.let(ElectricalInterface::valueOf),
            voltageRange = voltage,
            preferredSupplyVoltageV =
                json.nullableDouble(
                    "preferredSupplyVoltageV"
                ),
            supplyRole =
                SupplyRole.valueOf(
                    json.getString("supplyRole")
                ),
            currentMaxMa =
                json.nullableDouble("currentMaxMa"),
            requiresExternalPower =
                json.optBoolean(
                    "requiresExternalPower",
                    false,
                ),
            requiredSupportTags =
                json.getJSONArray("requiredSupportTags")
                    .strings()
                    .toSet(),
            tags =
                json.getJSONArray("tags")
                    .strings()
                    .toSet(),
            driverId =
                json.optString("driverId")
                    .takeIf { it.isNotBlank() },
            designReady =
                json.optBoolean("designReady", false),
            engineeringPriority =
                json.optInt("engineeringPriority", 0),
            pins =
                json.getJSONArray("pins")
                    .objects()
                    .map { pin ->
                        ComponentPinSpec(
                            pinId = pin.getString("pinId"),
                            label = pin.getString("label"),
                            role =
                                ComponentPinRole.valueOf(
                                    pin.getString("role")
                                ),
                        )
                    },
            signalRequirements =
                json.getJSONArray("signalRequirements")
                    .objects()
                    .map { req ->
                        SignalRequirement(
                            id = req.getString("id"),
                            boardCapability =
                                BoardPinCapability.valueOf(
                                    req.getString(
                                        "boardCapability"
                                    )
                                ),
                            componentPinRole =
                                ComponentPinRole.valueOf(
                                    req.getString(
                                        "componentPinRole"
                                    )
                                ),
                            netType =
                                NetType.valueOf(
                                    req.getString("netType")
                                ),
                            wireSemantic =
                                WireSemantic.valueOf(
                                    req.getString(
                                        "wireSemantic"
                                    )
                                ),
                            shareable =
                                req.optBoolean(
                                    "shareable",
                                    false,
                                ),
                        )
                    },
            i2cAddress =
                json.optString("i2cAddress")
                    .takeIf { it.isNotBlank() },
            maxLoadCurrentMa =
                json.nullableDouble("maxLoadCurrentMa"),
            minInputHighVoltageV =
                json.nullableDouble(
                    "minInputHighVoltageV"
                ),
            sourceIds =
                json.getJSONArray("sourceIds")
                    .strings()
                    .toSet(),
            aliases =
                json.getJSONArray("aliases")
                    .strings()
                    .toSet(),
            verificationStatus =
                ComponentVerificationStatus.valueOf(
                    json.getString("verificationStatus")
                ),
        )
    }

    private fun stringArray(
        values: Iterable<String>,
    ): JSONArray =
        JSONArray().apply {
            values.forEach(::put)
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
        if (!has(key) || isNull(key)) null else getDouble(key)

    private companion object {
        const val DATABASE_NAME =
            "ai_electronics_component_catalog.db"
        const val DATABASE_VERSION = 1
    }
}
