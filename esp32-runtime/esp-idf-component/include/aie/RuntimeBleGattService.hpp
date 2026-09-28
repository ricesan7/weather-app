#pragma once

#ifdef ESP_PLATFORM

#include "aie/BleRuntimeBridge.hpp"

#include "host/ble_gap.h"

namespace aie {

class RuntimeBleGattService {
public:
    explicit RuntimeBleGattService(BleRuntimeBridge& bridge);

    int registerService();
    int startAdvertising();
    int onGapEvent(ble_gap_event* event);

private:
    BleRuntimeBridge& bridge_;
};

} // namespace aie

#endif // ESP_PLATFORM
