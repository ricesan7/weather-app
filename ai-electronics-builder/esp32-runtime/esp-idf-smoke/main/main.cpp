#include "aie/BleRuntimeBridge.hpp"
#include "aie/Protocol.hpp"
#include "aie/RuntimeBleGattService.hpp"
#include "aie/RuntimeCore.hpp"

#include "esp_err.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "host/ble_hs.h"
#include "nimble/nimble_port.h"
#include "nvs.h"
#include "nvs_flash.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#include <cstdint>
#include <cstdio>
#include <optional>
#include <set>
#include <string>
#include <vector>

namespace {

constexpr const char* TAG = "aie_ble_smoke";
constexpr const char* NVS_NAMESPACE = "aie_rt";

std::string settingKey(
    const std::string& projectId,
    const std::string& settingId
) {
    std::uint64_t hash = 1469598103934665603ULL;
    const std::string material = projectId + ":" + settingId;
    for (const unsigned char byte : material) {
        hash ^= byte;
        hash *= 1099511628211ULL;
    }

    char key[16] = {};
    std::snprintf(
        key,
        sizeof(key),
        "s%014llx",
        static_cast<unsigned long long>(
            hash & 0x00FFFFFFFFFFFFFFULL
        )
    );
    return std::string(key);
}

std::optional<std::string> nvsReadString(const std::string& key) {
    nvs_handle_t handle = 0;
    if (
        nvs_open(
            NVS_NAMESPACE,
            NVS_READONLY,
            &handle
        ) != ESP_OK
    ) {
        return std::nullopt;
    }

    size_t length = 0;
    esp_err_t rc = nvs_get_str(handle, key.c_str(), nullptr, &length);
    if (rc != ESP_OK || length == 0) {
        nvs_close(handle);
        return std::nullopt;
    }

    std::vector<char> buffer(length);
    rc = nvs_get_str(handle, key.c_str(), buffer.data(), &length);
    nvs_close(handle);

    if (rc != ESP_OK) return std::nullopt;
    return std::string(buffer.data());
}

bool nvsWriteString(
    const std::string& key,
    const std::string& value
) {
    nvs_handle_t handle = 0;
    if (
        nvs_open(
            NVS_NAMESPACE,
            NVS_READWRITE,
            &handle
        ) != ESP_OK
    ) {
        return false;
    }

    esp_err_t rc = nvs_set_str(handle, key.c_str(), value.c_str());
    if (rc == ESP_OK) {
        rc = nvs_commit(handle);
    }
    nvs_close(handle);
    return rc == ESP_OK;
}

std::optional<std::string> nvsReadBlobString(const std::string& key) {
    nvs_handle_t handle = 0;
    if (
        nvs_open(
            NVS_NAMESPACE,
            NVS_READONLY,
            &handle
        ) != ESP_OK
    ) {
        return std::nullopt;
    }

    size_t length = 0;
    esp_err_t rc = nvs_get_blob(handle, key.c_str(), nullptr, &length);
    if (rc != ESP_OK || length == 0) {
        nvs_close(handle);
        return std::nullopt;
    }

    std::vector<char> buffer(length);
    rc = nvs_get_blob(handle, key.c_str(), buffer.data(), &length);
    nvs_close(handle);

    if (rc != ESP_OK) return std::nullopt;
    return std::string(buffer.data(), length);
}

bool nvsWriteBlobString(
    const std::string& key,
    const std::string& value
) {
    nvs_handle_t handle = 0;
    if (
        nvs_open(
            NVS_NAMESPACE,
            NVS_READWRITE,
            &handle
        ) != ESP_OK
    ) {
        return false;
    }

    esp_err_t rc = nvs_set_blob(
        handle,
        key.c_str(),
        value.data(),
        value.size()
    );
    if (rc == ESP_OK) {
        rc = nvs_commit(handle);
    }
    nvs_close(handle);
    return rc == ESP_OK;
}

class SmokeHardware final : public aie::RuntimeHardware {
public:
    bool setOutput(
        const std::string&,
        const std::string&
    ) override {
        return true;
    }

    bool runTest(const std::string&) override {
        return true;
    }

    std::optional<std::string> loadSetting(
        const std::string& projectId,
        const std::string& settingId
    ) override {
        return nvsReadString(settingKey(projectId, settingId));
    }

    bool storeSetting(
        const std::string& projectId,
        const std::string& settingId,
        const std::string& value
    ) override {
        return nvsWriteString(
            settingKey(projectId, settingId),
            value
        );
    }

    std::optional<std::string> loadManifest() override {
        return nvsReadBlobString("manifest");
    }

    bool storeManifest(const std::string& encodedManifest) override {
        return nvsWriteBlobString("manifest", encodedManifest);
    }
};

SmokeHardware hardware;
aie::RuntimeCore runtime(
    hardware,
    std::set<std::string>{
        "drv_sht31",
        "drv_gpio_sink",
    }
);
aie::ProtocolDispatcher dispatcher(runtime);
aie::BleRuntimeBridge bridge(dispatcher);
aie::RuntimeBleGattService bleService(bridge);

void onStackReset(int reason) {
    ESP_LOGW(TAG, "NimBLE reset reason=%d", reason);
}

void onStackSync() {
    const int rc = bleService.startAdvertising();
    if (rc != 0) {
        ESP_LOGE(TAG, "advertising failed rc=%d", rc);
    }
}

void runtimeControlTask(void*) {
    ESP_LOGI(TAG, "autonomous runtime control task started");
    while (true) {
        runtime.tick();
        vTaskDelay(pdMS_TO_TICKS(100));
    }
}

void nimbleHostTask(void*) {
    ESP_LOGI(TAG, "NimBLE host task started");
    nimble_port_run();
    vTaskDelete(nullptr);
}

} // namespace

extern "C" void app_main(void) {
    esp_err_t rc = nvs_flash_init();
    if (
        rc == ESP_ERR_NVS_NO_FREE_PAGES ||
        rc == ESP_ERR_NVS_NEW_VERSION_FOUND
    ) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        rc = nvs_flash_init();
    }
    ESP_ERROR_CHECK(rc);

    std::string restoreError;
    if (runtime.restorePersistedManifest(restoreError)) {
        ESP_LOGI(TAG, "restored persisted project manifest");
    } else {
        ESP_LOGI(
            TAG,
            "no restorable project manifest: %s",
            restoreError.c_str()
        );
    }

    rc = nimble_port_init();
    if (rc != ESP_OK) {
        ESP_LOGE(TAG, "nimble_port_init failed rc=%d", rc);
        return;
    }

#if CONFIG_BT_NIMBLE_GAP_SERVICE
    ble_svc_gap_init();
    ble_svc_gap_device_name_set("AI Electronics Runtime");
#endif
    ble_svc_gatt_init();

    const int serviceRc = bleService.registerService();
    if (serviceRc != 0) {
        ESP_LOGE(TAG, "Runtime GATT service failed rc=%d", serviceRc);
        return;
    }

    ble_hs_cfg.reset_cb = onStackReset;
    ble_hs_cfg.sync_cb = onStackSync;

    const BaseType_t runtimeTaskRc = xTaskCreate(
        runtimeControlTask,
        "AI Runtime",
        4096,
        nullptr,
        6,
        nullptr
    );
    if (runtimeTaskRc != pdPASS) {
        ESP_LOGE(TAG, "failed to create autonomous runtime task");
        return;
    }

    const BaseType_t taskRc = xTaskCreate(
        nimbleHostTask,
        "NimBLE Host",
        4096,
        nullptr,
        5,
        nullptr
    );
    if (taskRc != pdPASS) {
        ESP_LOGE(TAG, "failed to create NimBLE host task");
    }
}
