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
   - the normal firmware/runtime artifacts.
6. Base44 implements UI, UX, authentication, cloud data, history, automation, and AI features.
7. The Android Hardware Bridge translates semantic commands to the actual device transport.

## Boundary rule

Application-side code sends semantic commands such as:

```json
{
  "command": "temp_on",
  "binding": "settings.temp_on",
  "value": 30
}
```

It must not send Android/BLE implementation instructions such as "write bytes to characteristic X".
That translation is owned by the Android Hardware Bridge.

## Generated contract

`SoftwarePlan` contains:

- whether companion software is required,
- whether Base44 design is required,
- why it was selected,
- transport selection,
- command definitions,
- telemetry definitions,
- event definitions,
- Base44 UI handoff.

This contract is generated from the same validated DesignCore and UiSpec as the hardware project, so the software and hardware stay aligned.
