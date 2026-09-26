# Dynamic Android dashboard v1

The Android control surface is generated from UiSpec and TestPlan. It is transport-independent and talks only to RuntimeTransport.

## Runtime bindings

- telemetry.<id> -> TELEMETRY response
- settings.<id> -> GET_VALUE / SET_VALUE
- logging.<id> -> phone-side telemetry history
- tests.<id> -> RUN_TEST

The UI never asks the beginner to choose BLE MTU, GPIO, protocol, upload mode, library or build configuration.

## Reference pages

The practical ventilation controller resolves to:
- 状態: temperature, humidity, fan state, AUTO/MANUAL, manual fan
- 設定: runtime-mutable thresholds
- 履歴: phone-side telemetry history
- 診断: sensor and fan self-test actions

Diagnostics is projected from TestPlan instead of being embedded in UiSpec. This keeps the compiler pipeline order intact: UI compilation and diagnostic compilation remain independent artifacts.

## State behavior

RuntimeDashboardViewModel:
- refreshes TELEMETRY and all visible runtime settings;
- appends numeric telemetry to bounded phone-side chart history;
- sends setting changes through SET_VALUE without rebuild/reflash;
- sends self-tests through RUN_TEST;
- exposes pending/error/test state to Compose;
- performs protocol work on Dispatchers.IO.

## Rendering

RuntimeControlDashboard renders UiWidget deterministically with Jetpack Compose:
- ValueCard
- Gauge
- LineChart
- Toggle
- Slider
- Select
- Button / diagnostic action
- Status
- Alarm

The renderer is intentionally independent of BLE. USB and Wi-Fi transports can implement RuntimeTransport and reuse the same UI.
