package com.aielectronics.runtime

class DefaultRuntimeSettingsService(
    private val client: RuntimeClient,
) : RuntimeSettingsService {

    override fun set(value: RuntimeSettingValue): SettingUpdateResult =
        client.setValue(
            requestId = "set-" + value.settingId,
            settingId = value.settingId,
            value = value.value,
        ).fold(
            onSuccess = { ack -> SettingUpdateResult.Success(ack) },
            onFailure = {
                SettingUpdateResult.Rejected(
                    userMessage = "設定を変更できませんでした。",
                    technicalCode = "E_SETTING_REJECTED",
                )
            },
        )

    override fun get(settingId: String): RuntimeSettingValue? =
        client.getValue("get-" + settingId, settingId).getOrNull()
}
