# Bench E2E Gate v1

This feature prepares the final physical XIAO ESP32S3 + Android bench validation.

The app can run one explicit hardware gate after BLE connection:

1. HELLO / CAPABILITIES
2. DEPLOY_MANIFEST
3. VERIFY_PROJECT
4. every required RUN_TEST
5. TELEMETRY and required telemetry ID presence
6. GET_VALUE -> SET_VALUE with the same value for one runtime-mutable setting

The settings round-trip deliberately writes the same value back. This proves the live SET_VALUE path without changing project behavior during bench validation.

## PASS criteria

The report is PASS only when every gate step passes.

A missing Runtime capability, deployment rejection, project mismatch, failed self-test, missing telemetry item, or settings round-trip failure produces FAIL.

## Why this exists

CI already proves:
- Android APK builds;
- BLE packet protocol contracts;
- C++ Runtime behavior;
- ESP-IDF v6.1 ESP32-S3 compilation.

CI cannot prove RF/GATT interoperability with the user's physical board and phone. Bench E2E Gate provides the exact in-app sequence needed to close that remaining hardware validation item without relying on serial-console interpretation.
