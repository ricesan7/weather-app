#include "aie/BleRuntimeBridge.hpp"
#include "aie/Protocol.hpp"
#include "aie/RuntimeBleGattService.hpp"
#include "aie/RuntimeCore.hpp"

#include "driver/gpio.h"
#include "esp_err.h"
#include "esp_log.h"
#include "esp_rom_sys.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "host/ble_hs.h"
#include "nimble/nimble_port.h"
#include "nvs.h"
#include "nvs_flash.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#include <array>
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

    bool supportsDriverProfile(
        const aie::DriverProfileSpec& profile
    ) const override {
        return (
            profile.family == "DHT_PULSE_SENSOR" &&
            profile.interfaceType == "ONE_WIRE"
        ) || (
            profile.family == "GPIO_DIGITAL_INPUT" &&
            profile.interfaceType == "GPIO"
        );
    }

    std::uint64_t monotonicMillis() const override {
        return static_cast<std::uint64_t>(
            esp_timer_get_time() / 1000
        );
    }

    std::optional<
        std::unordered_map<std::string, aie::Value>
    > sampleDevice(
        const aie::DeviceSpec& device,
        const aie::DriverProfileSpec& profile
    ) override {
        const auto gpioIt = device.config.find("gpio");
        if (gpioIt == device.config.end()) {
            return std::nullopt;
        }

        int gpioNumber = -1;
        try {
            gpioNumber = std::stoi(gpioIt->second);
        } catch (...) {
            return std::nullopt;
        }
        if (
            gpioNumber < 0 ||
            gpioNumber >= GPIO_NUM_MAX
        ) {
            return std::nullopt;
        }

        const auto pin =
            static_cast<gpio_num_t>(gpioNumber);

        if (profile.family == "GPIO_DIGITAL_INPUT") {
            gpio_config_t config = {};
            config.pin_bit_mask =
                1ULL << static_cast<unsigned>(gpioNumber);
            config.mode = GPIO_MODE_INPUT;
            config.pull_up_en = GPIO_PULLUP_ENABLE;
            config.pull_down_en = GPIO_PULLDOWN_DISABLE;
            config.intr_type = GPIO_INTR_DISABLE;
            if (gpio_config(&config) != ESP_OK) {
                return std::nullopt;
            }

            return std::unordered_map<
                std::string,
                aie::Value
            >{
                {
                    "DIGITAL_STATE",
                    aie::Value(
                        gpio_get_level(pin) != 0
                    ),
                },
            };
        }

        if (profile.family == "DHT_PULSE_SENSOR") {
            return readDht(pin, profile);
        }

        return std::nullopt;
    }

private:
    static std::optional<std::uint32_t>
    levelDurationUs(
        gpio_num_t pin,
        int level,
        std::uint32_t timeoutUs
    ) {
        const std::int64_t start =
            esp_timer_get_time();
        while (gpio_get_level(pin) == level) {
            const std::int64_t elapsed =
                esp_timer_get_time() - start;
            if (
                elapsed >
                static_cast<std::int64_t>(timeoutUs)
            ) {
                return std::nullopt;
            }
        }
        return static_cast<std::uint32_t>(
            esp_timer_get_time() - start
        );
    }

    static std::optional<
        std::unordered_map<std::string, aie::Value>
    > readDht(
        gpio_num_t pin,
        const aie::DriverProfileSpec& profile
    ) {
        const auto variantIt =
            profile.parameters.find("variant");
        const auto startIt =
            profile.parameters.find("start_low_us");
        const auto zeroIt =
            profile.parameters.find(
                "zero_high_max_us"
            );
        const auto oneIt =
            profile.parameters.find(
                "one_high_min_us"
            );
        if (
            variantIt == profile.parameters.end() ||
            startIt == profile.parameters.end() ||
            zeroIt == profile.parameters.end() ||
            oneIt == profile.parameters.end()
        ) {
            return std::nullopt;
        }

        int startLowUs = 0;
        int zeroHighMaxUs = 0;
        int oneHighMinUs = 0;
        try {
            startLowUs = std::stoi(startIt->second);
            zeroHighMaxUs = std::stoi(zeroIt->second);
            oneHighMinUs = std::stoi(oneIt->second);
        } catch (...) {
            return std::nullopt;
        }

        if (
            startLowUs < 800 ||
            startLowUs > 25000 ||
            zeroHighMaxUs < 20 ||
            zeroHighMaxUs > 55 ||
            oneHighMinUs < 45 ||
            oneHighMinUs > 90 ||
            oneHighMinUs <= zeroHighMaxUs
        ) {
            return std::nullopt;
        }

        gpio_set_pull_mode(pin, GPIO_PULLUP_ONLY);
        gpio_set_direction(pin, GPIO_MODE_OUTPUT);
        gpio_set_level(pin, 0);
        esp_rom_delay_us(
            static_cast<std::uint32_t>(startLowUs)
        );
        gpio_set_level(pin, 1);
        esp_rom_delay_us(30);
        gpio_set_direction(pin, GPIO_MODE_INPUT);

        if (!levelDurationUs(pin, 1, 200)) {
            return std::nullopt;
        }
        if (!levelDurationUs(pin, 0, 200)) {
            return std::nullopt;
        }
        if (!levelDurationUs(pin, 1, 200)) {
            return std::nullopt;
        }

        std::array<std::uint8_t, 5> data = {};
        for (int bit = 0; bit < 40; ++bit) {
            if (!levelDurationUs(pin, 0, 120)) {
                return std::nullopt;
            }
            const auto high =
                levelDurationUs(pin, 1, 120);
            if (!high) {
                return std::nullopt;
            }

            int value = 0;
            if (
                static_cast<int>(*high) >=
                oneHighMinUs
            ) {
                value = 1;
            } else if (
                static_cast<int>(*high) <=
                zeroHighMaxUs
            ) {
                value = 0;
            } else {
                return std::nullopt;
            }

            data[bit / 8] =
                static_cast<std::uint8_t>(
                    (data[bit / 8] << 1) |
                    value
                );
        }

        const std::uint8_t checksum =
            static_cast<std::uint8_t>(
                data[0] +
                data[1] +
                data[2] +
                data[3]
            );
        if (checksum != data[4]) {
            return std::nullopt;
        }

        double humidity = 0.0;
        double temperature = 0.0;
        if (variantIt->second == "DHT11") {
            humidity =
                static_cast<double>(data[0]) +
                static_cast<double>(data[1]) * 0.1;
            temperature =
                static_cast<double>(data[2] & 0x7F) +
                static_cast<double>(data[3]) * 0.1;
            if ((data[2] & 0x80) != 0) {
                temperature = -temperature;
            }
        } else if (variantIt->second == "DHT22") {
            const std::uint16_t rawHumidity =
                static_cast<std::uint16_t>(
                    (data[0] << 8) | data[1]
                );
            const std::uint16_t rawTemperature =
                static_cast<std::uint16_t>(
                    ((data[2] & 0x7F) << 8) |
                    data[3]
                );
            humidity =
                static_cast<double>(rawHumidity) *
                0.1;
            temperature =
                static_cast<double>(rawTemperature) *
                0.1;
            if ((data[2] & 0x80) != 0) {
                temperature = -temperature;
            }
        } else {
            return std::nullopt;
        }

        return std::unordered_map<
            std::string,
            aie::Value
        >{
            {
                "DHT_TEMPERATURE",
                aie::Value(temperature),
            },
            {
                "DHT_HUMIDITY",
                aie::Value(humidity),
            },
        };
    }

public:
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
        "drv_binary_output",
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
