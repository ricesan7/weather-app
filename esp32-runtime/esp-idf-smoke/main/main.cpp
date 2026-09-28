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
#include "nvs_flash.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#include <set>
#include <string>

namespace {

constexpr const char* TAG = "aie_ble_smoke";

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
