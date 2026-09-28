# Android BLE GATT adapter v1

This module bridges Android BluetoothGatt to the portable BlePacketIo interface.

## Connection flow

1. request the platform runtime permissions before use;
2. scan for the Universal Runtime service UUID;
3. connect with BluetoothGatt;
4. discover the runtime service and write/notify characteristics;
5. enable notifications through CCCD;
6. request MTU;
7. use the MTU reported by onMtuChanged;
8. pass packet writes/notifications to the portable BLE transport core;
9. perform HELLO/CAPABILITIES handshake.

## Permissions

For Android 12 / API 31 and later:
- BLUETOOTH_SCAN
- BLUETOOTH_CONNECT

The manifest uses neverForLocation for scanning because this app does not use BLE scan results to derive physical location.

For Android 11 and earlier:
- legacy BLUETOOTH / BLUETOOTH_ADMIN declarations
- ACCESS_FINE_LOCATION for scanning

## API compatibility

API 33 and later uses the value-taking BluetoothGatt writeCharacteristic/writeDescriptor methods.
Older Android versions use the deprecated cached-value methods only inside the compatibility branch.

## Runtime GATT profile

Service:
- f4a10000-7f6a-4c7a-9e4f-3d6a2c4b1000

App -> MCU write:
- f4a10001-7f6a-4c7a-9e4f-3d6a2c4b1000

MCU -> App notify:
- f4a10002-7f6a-4c7a-9e4f-3d6a2c4b1000

The ESP32 BLE adapter must expose the same profile.

## Boundary

This is the Android transport adapter, not yet a complete user-facing Android app screen. UI permission prompts, device progress presentation and lifecycle/ViewModel integration belong in the app/feature-connect layer.
