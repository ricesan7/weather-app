package com.aielectronics.runtime

data class RuntimeSettingValue(
    val settingId: String,
    val value: String,
)

data class SettingAck(
    val settingId: String,
    val appliedValue: String,
    val persisted: Boolean,
)

sealed interface SettingUpdateResult {
    data class Success(val ack: SettingAck) : SettingUpdateResult
    data class Rejected(val userMessage: String, val technicalCode: String) : SettingUpdateResult
}

interface RuntimeSettingsService {
    fun set(value: RuntimeSettingValue): SettingUpdateResult
    fun get(settingId: String): RuntimeSettingValue?
}
