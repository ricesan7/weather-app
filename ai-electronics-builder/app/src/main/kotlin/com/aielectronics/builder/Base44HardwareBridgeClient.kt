package com.aielectronics.builder

import com.aielectronics.core.model.AppBridgeChannel
import com.aielectronics.core.model.AppBridgePage
import com.aielectronics.core.model.AppBridgeWidget
import com.aielectronics.core.model.AppHardwareIntegrationContract
import com.aielectronics.core.model.BridgeValueType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Base44BridgeCredentials(
    val projectId: String,
    val sessionId: String,
    val deviceId: String,
    val deviceToken: String,
)

data class Base44BridgeCommand(
    val commandId: String,
    val binding: String,
    val value: String,
)

data class Base44BridgeAck(
    val commandId: String,
    val ok: Boolean,
    val error: String? = null,
)

data class Base44BridgeSyncResult(
    val commands: List<Base44BridgeCommand>,
)

class Base44HardwareBridgeClient(
    private val endpoint: String,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {

    fun pair(
        pairingCode: String,
        deviceId: String,
    ): Base44BridgeCredentials {
        val json = post(
            JSONObject().apply {
                put("action", "pair")
                put("pairing_code", pairingCode)
                put("device_id", deviceId)
            }
        )

        require(json.optBoolean("ok", false)) {
            json.optString("error", "Base44 pairing failed")
        }

        return Base44BridgeCredentials(
            projectId = json.getString("project_id"),
            sessionId = json.getString("session_id"),
            deviceId = deviceId,
            deviceToken = json.getString("device_token"),
        )
    }

    fun sync(
        credentials: Base44BridgeCredentials,
        telemetry: Map<String, String>,
        settings: Map<String, String>,
        contract: AppHardwareIntegrationContract?,
        acknowledgements: List<Base44BridgeAck> = emptyList(),
    ): Base44BridgeSyncResult {
        val json = post(
            JSONObject().apply {
                put("action", "sync")
                put("device_id", credentials.deviceId)
                put("device_token", credentials.deviceToken)
                put(
                    "telemetry",
                    JSONObject().apply {
                        telemetry.forEach { (key, value) -> put(key, value) }
                    },
                )
                put(
                    "settings",
                    JSONObject().apply {
                        settings.forEach { (key, value) -> put(key, value) }
                    },
                )
                put(
                    "acknowledgements",
                    JSONArray().apply {
                        acknowledgements.forEach { ack ->
                            put(
                                JSONObject().apply {
                                    put("command_id", ack.commandId)
                                    put("ok", ack.ok)
                                    ack.error?.let { put("error", it) }
                                }
                            )
                        }
                    },
                )
                contract?.let {
                    put("contract", encodeContract(it))
                }
            }
        )

        require(json.optBoolean("ok", false)) {
            json.optString("error", "Base44 bridge sync failed")
        }

        val commandsJson = json.optJSONArray("commands") ?: JSONArray()
        val commands = buildList {
            for (index in 0 until commandsJson.length()) {
                val item = commandsJson.getJSONObject(index)
                add(
                    Base44BridgeCommand(
                        commandId = item.getString("command_id"),
                        binding = item.getString("binding"),
                        value = jsonValueToString(item.opt("value")),
                    )
                )
            }
        }
        return Base44BridgeSyncResult(commands)
    }

    fun health(): Boolean =
        runCatching {
            post(JSONObject().apply { put("action", "health") })
                .optBoolean("ok", false)
        }.getOrDefault(false)

    private fun post(body: JSONObject): JSONObject {
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://localhost")) {
            "Base44 Bridge URL must use HTTPS."
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(body.toString())
            }
            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)

            if (status !in 200..299) {
                error(json.optString("error", "HTTP " + status))
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeContract(contract: AppHardwareIntegrationContract): JSONObject =
        JSONObject().apply {
            put("schema_version", contract.schemaVersion)
            put(
                "channels",
                JSONArray().apply {
                    contract.channels.forEach { channel ->
                        put(encodeChannel(channel))
                    }
                },
            )
            put(
                "pages",
                JSONArray().apply {
                    contract.pages.forEach { page ->
                        put(encodePage(page))
                    }
                },
            )
        }

    private fun encodeChannel(channel: AppBridgeChannel): JSONObject =
        JSONObject().apply {
            put("id", channel.id)
            put("binding", channel.binding)
            put("direction", channel.direction.name)
            put("value_type", channel.valueType.name)
            channel.displayName?.let { put("display_name", it) }
            put("presentation", channel.presentation.name)
            channel.unit?.let { put("unit", it) }
            channel.min?.let { put("min", it) }
            channel.max?.let { put("max", it) }
            channel.step?.let { put("step", it) }
            if (channel.allowedValues.isNotEmpty()) {
                put("allowed_values", JSONArray(channel.allowedValues))
            }
        }

    private fun encodePage(page: AppBridgePage): JSONObject =
        JSONObject().apply {
            put("id", page.id)
            put("title", page.title)
            put("order", page.order)
            put(
                "widgets",
                JSONArray().apply {
                    page.widgets.forEach { widget ->
                        put(encodeWidget(widget))
                    }
                },
            )
        }

    private fun encodeWidget(widget: AppBridgeWidget): JSONObject =
        JSONObject().apply {
            put("id", widget.id)
            put("binding", widget.binding)
            put("display_name", widget.displayName)
            put("presentation", widget.presentation.name)
            put("span", widget.span.name)
            put("order", widget.order)
        }

    private fun jsonValueToString(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is Boolean, is Number -> value.toString()
        is String -> value
        else -> value.toString()
    }
}

class Base44BridgeCredentialStore(
    private val preferences: android.content.SharedPreferences,
) {
    fun save(
        localProjectId: String,
        credentials: Base44BridgeCredentials,
    ) {
        preferences.edit()
            .putString(key(localProjectId, REMOTE_PROJECT_ID), credentials.projectId)
            .putString(key(localProjectId, SESSION_ID), credentials.sessionId)
            .putString(key(localProjectId, DEVICE_ID), credentials.deviceId)
            .putString(key(localProjectId, DEVICE_TOKEN), credentials.deviceToken)
            .apply()
    }

    fun load(localProjectId: String): Base44BridgeCredentials? {
        val remoteProjectId =
            preferences.getString(key(localProjectId, REMOTE_PROJECT_ID), null)
                ?: return null
        val sessionId =
            preferences.getString(key(localProjectId, SESSION_ID), null)
                ?: return null
        val deviceId =
            preferences.getString(key(localProjectId, DEVICE_ID), null)
                ?: return null
        val deviceToken =
            preferences.getString(key(localProjectId, DEVICE_TOKEN), null)
                ?: return null

        return Base44BridgeCredentials(
            projectId = remoteProjectId,
            sessionId = sessionId,
            deviceId = deviceId,
            deviceToken = deviceToken,
        )
    }

    fun clear(localProjectId: String) {
        preferences.edit()
            .remove(key(localProjectId, REMOTE_PROJECT_ID))
            .remove(key(localProjectId, SESSION_ID))
            .remove(key(localProjectId, DEVICE_ID))
            .remove(key(localProjectId, DEVICE_TOKEN))
            .apply()
    }

    private fun key(localProjectId: String, field: String): String =
        "project." + localProjectId + "." + field

    companion object {
        private const val REMOTE_PROJECT_ID = "remote_project_id"
        private const val SESSION_ID = "session_id"
        private const val DEVICE_ID = "device_id"
        private const val DEVICE_TOKEN = "device_token"
    }
}
