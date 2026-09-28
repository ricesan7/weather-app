package com.aielectronics.control

import com.aielectronics.core.model.UiWidget

object WidgetText {

    fun label(widget: UiWidget): String {
        if (widget is UiWidget.Button) return widget.label

        val key = widget.binding
            .removePrefix("telemetry.")
            .removePrefix("settings.")
            .removePrefix("controls.")
            .removePrefix("logging.")

        return when (key) {
            "temperature" -> "温度"
            "humidity" -> "湿度"
            "fan_state", "fan.state" -> "ファン"
            "mode" -> "動作モード"
            "manual_fan", "fan_manual" -> "手動ファン"
            "temp_on" -> "ファンON温度"
            "temp_off" -> "ファンOFF温度"
            "rh_on" -> "ファンON湿度"
            "rh_off" -> "ファンOFF湿度"
            else -> key
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }
    }
}
