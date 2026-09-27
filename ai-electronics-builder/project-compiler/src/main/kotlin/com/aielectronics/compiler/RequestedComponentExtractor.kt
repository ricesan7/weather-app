package com.aielectronics.compiler

import com.aielectronics.core.model.RequestedComponent

object RequestedComponentExtractor {

    private val ignoredTokens = setOf(
        "base44",
        "android",
        "project",
        "compiler",
        "gpio",
        "i2c",
        "spi",
        "uart",
        "usb",
        "wifi",
        "wi-fi",
        "ble",
        "firmware",
        "arduino",
        "ide",
        "esp-now",
    )

    fun extract(goal: String): List<RequestedComponent> {
        val result = linkedMapOf<String, RequestedComponent>()
        var section: String? = null

        goal.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("##")) {
                section = line.removePrefix("##").trim()
                return@forEach
            }
            if (!line.startsWith("-")) return@forEach

            val body = line.removePrefix("-").trim()
            if (body.isBlank()) return@forEach

            val pair = body.split(":", "：", limit = 2)
            val label = pair.first().trim()
            val value = pair.getOrNull(1)?.trim().orEmpty()
            val sectionValue = section.orEmpty()

            val relevant =
                sectionValue.containsAny(
                    "部品",
                    "component",
                    "parts",
                    "マイコン",
                    "board",
                ) ||
                    label.containsAny(
                        "センサー",
                        "sensor",
                        "ディスプレイ",
                        "display",
                        "oled",
                        "スイッチ",
                        "switch",
                        "ボタン",
                        "button",
                        "マイコン",
                        "board",
                        "電源",
                        "module",
                        "モジュール",
                    )
            if (!relevant) return@forEach

            val candidate = (value.ifBlank { label })
                .substringBefore("および")
                .substringBefore("および")
                .trim()
            if (candidate.isBlank()) return@forEach

            val quantity = parseQuantity(body)
            val category = categoryHint(label, sectionValue)
            addCandidate(result, candidate, quantity, category, body)
        }

        modelLikeTokens(goal).forEach { token ->
            addCandidate(
                result = result,
                rawName = token,
                quantity = 1,
                categoryHint = null,
                sourceText = token,
            )
        }

        return result.values.toList()
    }

    private fun addCandidate(
        result: LinkedHashMap<String, RequestedComponent>,
        rawName: String,
        quantity: Int,
        categoryHint: String?,
        sourceText: String,
    ) {
        val cleaned = rawName
            .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
            .replace(Regex("""\s*[×xX]\s*\d+\s*$"""), "")
            .replace(Regex("""\s*\d+\s*(?:個|台|枚|本)\s*$"""), "")
            .trim()
            .trimEnd('。', '、', ',', ';')
        if (cleaned.length < 2) return

        val normalized = normalize(cleaned)
        if (
            categoryHint == "board" ||
            normalized.startsWith("esp32") ||
            normalized.isBlank() ||
            normalized in ignoredTokens ||
            ignoredTokens.any { normalized == normalize(it) }
        ) {
            return
        }

        result.putIfAbsent(
            normalized,
            RequestedComponent(
                rawName = cleaned,
                quantity = quantity.coerceAtLeast(1),
                categoryHint = categoryHint,
                sourceText = sourceText,
            )
        )
    }

    private fun modelLikeTokens(goal: String): List<String> =
        Regex("""\b[A-Za-z]+[A-Za-z0-9]*(?:[-_][A-Za-z0-9]+)*\d+[A-Za-z0-9-]*\b""")
            .findAll(goal)
            .map { it.value }
            .filterNot {
                val normalized = normalize(it)
                normalized in ignoredTokens.map(::normalize) ||
                    normalized.startsWith("esp32")
            }
            .distinctBy(::normalize)
            .toList()

    private fun parseQuantity(text: String): Int {
        val patterns = listOf(
            Regex("""[×xX]\s*(\d+)"""),
            Regex("""(\d+)\s*(?:個|台|枚|本)"""),
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } ?: 1
    }

    private fun categoryHint(label: String, section: String): String? {
        val text = "$label $section".lowercase()
        return when {
            text.containsAny("マイコン", "board", "esp32") -> "board"
            text.containsAny("センサー", "sensor") -> "sensor"
            text.containsAny("ディスプレイ", "display", "oled") -> "display"
            text.containsAny("スイッチ", "switch", "ボタン", "button") -> "input"
            text.containsAny("電源", "power") -> "power"
            else -> null
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("""[^a-z0-9ぁ-んァ-ヶ一-龠]+"""), "")

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { contains(it, ignoreCase = true) }
}
