package com.aielectronics.builder

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class RevisionSpeaker {
    USER,
    ASSISTANT,
}

data class RevisionChatMessage(
    val id: String,
    val speaker: RevisionSpeaker,
    val text: String,
)

data class RevisionLanguageRequest(
    val currentGoal: String,
    val userMessage: String,
    val history: List<RevisionChatMessage>,
)

data class RevisionLanguageResult(
    val assistantMessage: String,
    val updatedGoal: String,
    val needsClarification: Boolean,
    val clarificationQuestion: String,
)

interface RevisionLanguageAssistant {
    val statusLabel: String

    suspend fun refine(request: RevisionLanguageRequest): Result<RevisionLanguageResult>
}

class LocalRevisionLanguageAssistant : RevisionLanguageAssistant {
    override val statusLabel: String = "ローカル対話（AIゲートウェイ未設定）"

    override suspend fun refine(
        request: RevisionLanguageRequest,
    ): Result<RevisionLanguageResult> = runCatching {
        RevisionLanguageResult(
            assistantMessage = "内容を受け取りました。必要な条件を確認して設計へ反映します。",
            updatedGoal = appendRevision(
                currentGoal = request.currentGoal,
                message = request.userMessage,
            ),
            needsClarification = false,
            clarificationQuestion = "",
        )
    }

    private fun appendRevision(
        currentGoal: String,
        message: String,
    ): String =
        buildString {
            append(currentGoal.trim())
            append("\n\n【追加要望・回答】\n")
            append(message.trim())
        }
}

class GatewayRevisionLanguageAssistant(
    private val endpoint: String,
    private val gatewayToken: String = "",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : RevisionLanguageAssistant {
    override val statusLabel: String = "AI対話（ゲートウェイ接続）"

    override suspend fun refine(
        request: RevisionLanguageRequest,
    ): Result<RevisionLanguageResult> = runCatching {
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://localhost")) {
            "AIゲートウェイURLはHTTPSで指定してください。"
        }

        val body = JSONObject().apply {
            put("current_goal", request.currentGoal)
            put("user_message", request.userMessage)
            put(
                "conversation",
                JSONArray().apply {
                    request.history.takeLast(20).forEach { message ->
                        put(
                            JSONObject().apply {
                                put(
                                    "role",
                                    if (message.speaker == RevisionSpeaker.USER) {
                                        "user"
                                    } else {
                                        "assistant"
                                    },
                                )
                                put("text", message.text)
                            }
                        )
                    }
                },
            )
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (gatewayToken.isNotBlank()) {
                setRequestProperty("X-AI-Gateway-Token", gatewayToken)
            }
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }

            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream
                else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                error("AIゲートウェイがHTTP $statusを返しました。")
            }

            val json = JSONObject(responseText)
            RevisionLanguageResult(
                assistantMessage = json.getString("assistant_message"),
                updatedGoal = json.getString("updated_goal"),
                needsClarification = json.optBoolean("needs_clarification", false),
                clarificationQuestion = json.optString("clarification_question"),
            ).also {
                require(it.updatedGoal.isNotBlank()) {
                    "AIゲートウェイから有効な要件が返りませんでした。"
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
