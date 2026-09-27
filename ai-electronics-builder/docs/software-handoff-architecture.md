# Software Handoff Architecture

## Purpose

The Android application in this repository is the trusted hardware runtime and bridge.
Base44 is the application-layer generator for projects that require smartphone software.

Base44 must not directly own device-specific BLE, USB, Wi-Fi, Android permission, firmware deployment, or diagnostic logic.

## Pipeline

1. User describes the physical product.
2. Requirement resolution and electrical compilation produce a validated hardware design.
3. Software architecture detection decides whether companion smartphone software is required.
4. If no companion software is required, the project remains hardware/firmware only.
5. If companion software is required, the compiler emits:
   - a Base44 UI/application handoff,
   - a semantic Device Bridge contract,
   - live telemetry bindings,
   - application-side alert definitions,
   - the normal firmware/runtime artifacts.
6. Base44 implements UI, UX, authentication, cloud data, history, application settings, alert evaluation, notifications, automation, and AI features.
7. The Android Hardware Bridge translates semantic commands to the actual device transport and forwards telemetry upstream.

## Example: temperature monitor

A user may request:

> Show the current temperature on the smartphone and notify me when it exceeds 35°C.

The generated architecture is:

```text
Temperature sensor
      ↓
MCU / firmware
      ↓
BLE / USB / Wi-Fi
      ↓
Android Hardware Bridge
      ↓ telemetry.temperature
Base44
  ├─ Current temperature card
  ├─ Temperature history
  ├─ High-temperature threshold setting
  └─ Alert / notification
```

The application alert threshold is represented as:

```text
appSettings.temperature_high_threshold = 35.0
```

The threshold is evaluated in the Base44 application layer by default.
It is not translated into a BLE characteristic write unless the physical device itself also needs that threshold for local autonomous control.

This keeps cloud/app behavior separate from safety-critical device behavior.

## Boundary rule

Application-side code may send semantic hardware commands such as:

```json
{
  "command": "temp_on",
  "binding": "settings.temp_on",
  "value": 30
}
```

It must not send Android/BLE implementation instructions such as "write bytes to characteristic X".
That translation is owned by the Android Hardware Bridge.

Application-only settings such as a notification threshold remain in Base44:

```json
{
  "setting": "appSettings.temperature_high_threshold",
  "value": 35
}
```

## Generated contract

`SoftwarePlan` contains:

- whether companion software is required,
- whether Base44 design is required,
- why it was selected,
- transport selection,
- command definitions,
- telemetry definitions,
- event definitions,
- live telemetry bindings,
- configurable application alerts,
- Base44 UI handoff.

This contract is generated from the same validated DesignCore and UiSpec as the hardware project, so the software and hardware stay aligned.
