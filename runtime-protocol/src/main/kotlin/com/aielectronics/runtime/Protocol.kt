package com.aielectronics.runtime

enum class RuntimeMessageType {
    HELLO,
    CAPABILITIES,
    DEPLOY_MANIFEST,
    DEPLOY_RESULT,
    VERIFY_PROJECT,
    VERIFY_RESULT,
    START,
    STOP,
    TELEMETRY,
    SET_VALUE,
    GET_VALUE,
    VALUE,
    RUN_TEST,
    TEST_RESULT,
    LOG,
    ERROR,
}

data class RuntimeFrame(
    val protocolVersion: Int = 1,
    val type: RuntimeMessageType,
    val requestId: String,
    val fields: Map<String, String> = emptyMap(),
)

interface RuntimeTransport {
    fun exchange(frame: RuntimeFrame): Result<RuntimeFrame>
}

object RuntimeFrameCodec {

    fun encode(frame: RuntimeFrame): String {
        val parts = mutableListOf(
            frame.protocolVersion.toString(),
            frame.type.name,
            escape(frame.requestId),
        )
        frame.fields.toSortedMap().forEach { entry ->
            parts += escape(entry.key) + "=" + escape(entry.value)
        }
        return parts.joinToString("|")
    }

    fun decode(text: String): RuntimeFrame {
        val parts = splitEscaped(text)
        require(parts.size >= 3) { "Invalid runtime frame" }

        val fields = linkedMapOf<String, String>()
        parts.drop(3).forEach { token ->
            val index = token.indexOf('=')
            require(index > 0) { "Invalid field token" }
            val key = unescape(token.substring(0, index))
            val value = unescape(token.substring(index + 1))
            fields[key] = value
        }

        return RuntimeFrame(
            protocolVersion = parts[0].toInt(),
            type = RuntimeMessageType.valueOf(parts[1]),
            requestId = unescape(parts[2]),
            fields = fields,
        )
    }

    private fun escape(value: String): String =
        value
            .replace("%", "%25")
            .replace("|", "%7C")
            .replace("=", "%3D")
            .replace("\n", "%0A")
            .replace("\r", "%0D")

    private fun unescape(value: String): String =
        value
            .replace("%0D", "\r")
            .replace("%0A", "\n")
            .replace("%3D", "=")
            .replace("%7C", "|")
            .replace("%25", "%")

    private fun splitEscaped(text: String): List<String> = text.split("|")
}
