#ifdef ESP_PLATFORM

#include "aie/RuntimeBleGattService.hpp"

#include "esp_log.h"
#include "host/ble_att.h"
#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/ble_hs_mbuf.h"
#include "host/ble_uuid.h"
#include "os/os_mbuf.h"

#include <cstddef>
#include <cstdint>
#include <exception>
#include <vector>

namespace aie {
namespace {

constexpr const char* TAG = "aie_runtime_ble";

#define AIE_SVC_BYTES  0x00, 0x10, 0x4b, 0x2c, 0x6a, 0x3d, 0x4f, 0x9e,                        0x7a, 0x4c, 0x6a, 0x7f, 0x00, 0x00, 0xa1, 0xf4
#define AIE_RX_BYTES   0x00, 0x10, 0x4b, 0x2c, 0x6a, 0x3d, 0x4f, 0x9e,                        0x7a, 0x4c, 0x6a, 0x7f, 0x01, 0x00, 0xa1, 0xf4
#define AIE_TX_BYTES   0x00, 0x10, 0x4b, 0x2c, 0x6a, 0x3d, 0x4f, 0x9e,                        0x7a, 0x4c, 0x6a, 0x7f, 0x02, 0x00, 0xa1, 0xf4

static const ble_uuid128_t SERVICE_UUID = BLE_UUID128_INIT(AIE_SVC_BYTES);
static const ble_uuid128_t RX_UUID = BLE_UUID128_INIT(AIE_RX_BYTES);
static const ble_uuid128_t TX_UUID = BLE_UUID128_INIT(AIE_TX_BYTES);

BleRuntimeBridge* runtimeBridge = nullptr;

std::uint16_t txValueHandle = 0;
std::uint16_t activeConnection = BLE_HS_CONN_HANDLE_NONE;
std::uint8_t ownAddressType = 0;
bool subscribed = false;

ble_gatt_chr_def characteristics[3] = {};
ble_gatt_svc_def services[2] = {};

std::size_t responsePacketBytes(std::uint16_t connHandle) {
    const auto mtu = ble_att_mtu(connHandle);
    return mtu > 3 ? static_cast<std::size_t>(mtu - 3) : 20;
}

int notifyPacket(
    std::uint16_t connHandle,
    const std::vector<std::uint8_t>& packet
) {
    if (
        connHandle == BLE_HS_CONN_HANDLE_NONE ||
        connHandle != activeConnection ||
        !subscribed
    ) {
        return BLE_HS_ENOTCONN;
    }

    auto* om = ble_hs_mbuf_from_flat(packet.data(), packet.size());
    if (om == nullptr) {
        return BLE_HS_ENOMEM;
    }

    const int rc = ble_gatts_notify_custom(
        connHandle,
        txValueHandle,
        om
    );

    if (rc == BLE_HS_ENOTSUP) {
        os_mbuf_free_chain(om);
    }

    return rc;
}

int runtimeAccessCallback(
    std::uint16_t connHandle,
    std::uint16_t,
    ble_gatt_access_ctxt* ctxt,
    void*
) {
    if (
        ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR ||
        ble_uuid_cmp(ctxt->chr->uuid, &RX_UUID.u) != 0
    ) {
        return ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR
            ? BLE_ATT_ERR_READ_NOT_PERMITTED
            : BLE_ATT_ERR_WRITE_NOT_PERMITTED;
    }

    const auto total = OS_MBUF_PKTLEN(ctxt->om);
    if (total < BlePacketCodec::HEADER_SIZE || total > 512) {
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    }

    std::vector<std::uint8_t> packet(total);
    std::uint16_t copied = 0;

    const int flattenRc = ble_hs_mbuf_to_flat(
        ctxt->om,
        packet.data(),
        packet.size(),
        &copied
    );
    if (flattenRc != 0 || copied != total) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    if (runtimeBridge == nullptr) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    try {
        const auto responses = runtimeBridge->onPacket(
            packet,
            responsePacketBytes(connHandle)
        );

        for (const auto& response : responses) {
            const int notifyRc = notifyPacket(connHandle, response);
            if (notifyRc != 0) {
                ESP_LOGW(TAG, "notify failed rc=%d", notifyRc);
                return BLE_ATT_ERR_UNLIKELY;
            }
        }
    } catch (const std::exception& e) {
        ESP_LOGW(TAG, "runtime packet rejected: %s", e.what());
        runtimeBridge->reset();
        return BLE_ATT_ERR_UNLIKELY;
    }

    return 0;
}

int gapEventCallback(ble_gap_event* event, void* arg) {
    auto* service = static_cast<RuntimeBleGattService*>(arg);
    return service != nullptr ? service->onGapEvent(event) : 0;
}

} // namespace

RuntimeBleGattService::RuntimeBleGattService(BleRuntimeBridge& bridge)
    : bridge_(bridge) {}

int RuntimeBleGattService::registerService() {
    runtimeBridge = &bridge_;

    characteristics[0] = {};
    characteristics[0].uuid = &RX_UUID.u;
    characteristics[0].access_cb = runtimeAccessCallback;
    characteristics[0].flags = BLE_GATT_CHR_F_WRITE;

    characteristics[1] = {};
    characteristics[1].uuid = &TX_UUID.u;
    characteristics[1].access_cb = runtimeAccessCallback;
    characteristics[1].flags = BLE_GATT_CHR_F_NOTIFY;
    characteristics[1].val_handle = &txValueHandle;

    characteristics[2] = {};

    services[0] = {};
    services[0].type = BLE_GATT_SVC_TYPE_PRIMARY;
    services[0].uuid = &SERVICE_UUID.u;
    services[0].characteristics = characteristics;
    services[1] = {};

    int rc = ble_gatts_count_cfg(services);
    if (rc != 0) {
        ESP_LOGE(TAG, "ble_gatts_count_cfg rc=%d", rc);
        return rc;
    }

    rc = ble_gatts_add_svcs(services);
    if (rc != 0) {
        ESP_LOGE(TAG, "ble_gatts_add_svcs rc=%d", rc);
    }
    return rc;
}

int RuntimeBleGattService::startAdvertising() {
    int rc = ble_hs_id_infer_auto(0, &ownAddressType);
    if (rc != 0) {
        ESP_LOGE(TAG, "ble_hs_id_infer_auto rc=%d", rc);
        return rc;
    }

    ble_hs_adv_fields fields = {};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = const_cast<ble_uuid128_t*>(&SERVICE_UUID);
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;

    rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
        ESP_LOGE(TAG, "ble_gap_adv_set_fields rc=%d", rc);
        return rc;
    }

    ble_gap_adv_params params = {};
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;

    rc = ble_gap_adv_start(
        ownAddressType,
        nullptr,
        BLE_HS_FOREVER,
        &params,
        gapEventCallback,
        this
    );
    if (rc != 0) {
        ESP_LOGE(TAG, "ble_gap_adv_start rc=%d", rc);
    }
    return rc;
}

int RuntimeBleGattService::onGapEvent(ble_gap_event* event) {
    if (event == nullptr) {
        return 0;
    }

    switch (event->type) {
        case BLE_GAP_EVENT_CONNECT:
            if (event->connect.status == 0) {
                activeConnection = event->connect.conn_handle;
                subscribed = false;
                bridge_.reset();
                ESP_LOGI(
                    TAG,
                    "connected handle=%u",
                    static_cast<unsigned>(activeConnection)
                );
            } else {
                startAdvertising();
            }
            return 0;

        case BLE_GAP_EVENT_DISCONNECT:
            ESP_LOGI(
                TAG,
                "disconnected reason=%d",
                event->disconnect.reason
            );
            activeConnection = BLE_HS_CONN_HANDLE_NONE;
            subscribed = false;
            bridge_.reset();
            startAdvertising();
            return 0;

        case BLE_GAP_EVENT_SUBSCRIBE:
            if (event->subscribe.attr_handle == txValueHandle) {
                subscribed = event->subscribe.cur_notify != 0;
                ESP_LOGI(
                    TAG,
                    "notify subscribed=%d",
                    subscribed ? 1 : 0
                );
            }
            return 0;

        case BLE_GAP_EVENT_ADV_COMPLETE:
            startAdvertising();
            return 0;

        case BLE_GAP_EVENT_MTU:
            ESP_LOGI(
                TAG,
                "mtu=%u",
                static_cast<unsigned>(event->mtu.value)
            );
            return 0;

        default:
            return 0;
    }
}

} // namespace aie

#endif // ESP_PLATFORM
