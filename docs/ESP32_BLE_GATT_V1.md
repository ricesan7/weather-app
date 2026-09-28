# ESP32-S3 BLE GATT adapter v1

The ESP32 side uses the same logical runtime and BLE packet contract as Android.

## GATT profile

Service:
- f4a10000-7f6a-4c7a-9e4f-3d6a2c4b1000

Android -> ESP32 write:
- f4a10001-7f6a-4c7a-9e4f-3d6a2c4b1000

ESP32 -> Android notify:
- f4a10002-7f6a-4c7a-9e4f-3d6a2c4b1000

UUID bytes in the NimBLE source are stored in little-endian order, matching the convention used by Espressif's current NimBLE examples.

## Runtime path

Android RuntimeFrame
-> Android BLE packetizer
-> GATT write
-> ESP32 BleMessageAssembler
-> FrameCodec
-> ProtocolDispatcher
-> RuntimeCore
-> response RuntimeFrame
-> ESP32 BLE packetizer
-> GATT notifications
-> Android reassembly

The response packet size is derived from the active connection ATT MTU, minus the 3-byte ATT overhead.

## ESP-IDF integration

The directory esp32-runtime/esp-idf-component is an ESP-IDF component.

Application startup is responsible for:
1. NVS initialization;
2. nimble_port_init();
3. GAP/GATT standard service initialization as required;
4. construction of RuntimeHardware, RuntimeCore, ProtocolDispatcher and BleRuntimeBridge;
5. construction of RuntimeBleGattService;
6. registerService() before the NimBLE host starts;
7. calling startAdvertising() from the NimBLE host sync callback;
8. starting the NimBLE host task.

The GATT service restarts advertising after disconnect and clears partial packet state.

## Validation state

Host CI validates:
- exact 7-byte BLE header contract;
- fragmentation and reassembly;
- long DEPLOY_MANIFEST over multiple BLE packets;
- BLE packet -> ProtocolDispatcher -> BLE response round trip.

ESP-IDF target compilation and physical XIAO ESP32S3/Android interoperability still require hardware/ESP-IDF bench validation.
