# Base44 ↔ Android Hardware App Integration

## Goal

This architecture is generic. It is not tied to a thermometer, fan, sensor, relay, or any other specific project.

When a physical project needs smartphone software, the system generates two cooperating layers:

1. a Base44 application layer,
2. the existing Android hardware application as the hardware bridge/runtime.

The Android hardware application remains the only layer that needs to understand device-specific BLE, USB, Wi-Fi, Android permissions, firmware deployment, and diagnostics.

Base44 works with semantic data and actions instead of device-specific transport details.

## Conceptual architecture

```text
Base44 application
   │
   │ semantic app/hardware integration contract
   │
Android Hardware Bridge / Runtime
   │
   ├─ BLE
   ├─ USB
   └─ Wi-Fi
   │
Physical device / MCU / sensors / actuators
```

The Base44 application can therefore be changed or regenerated without rewriting the low-level hardware integration.

## Generated integration contract

The compiler emits generic channels in three directions:

- HARDWARE_TO_BASE44
  - measured values
  - states
  - counters
  - diagnostic values
  - other telemetry

- BASE44_TO_HARDWARE
  - settings
  - commands
  - modes
  - actuator requests
  - other writable controls

- HARDWARE_EVENT_TO_BASE44
  - alarms
  - faults
  - state changes
  - other device events

These are semantic bindings such as:

```text
telemetry.some_value
settings.some_setting
events.some_event
```

The contract deliberately does not contain BLE characteristic UUID handling, byte encoding, Android permission flows, or device driver details. Those remain inside the Android Hardware Bridge.

## Base44 responsibilities

Base44 may implement whatever application capabilities the project requires, for example:

- live values and status
- history
- device controls
- configurable settings
- notifications
- remote access
- automation
- AI features

These are selected from the user's project requirements. No individual example is hard-coded as product behavior.

## Hardware app responsibilities

The Android hardware app handles:

- Android permissions
- discovering and connecting to hardware
- BLE / USB / Wi-Fi transport
- translating semantic commands into device protocol operations
- forwarding telemetry and events
- firmware deployment
- diagnostics

## Design principle

A project-specific feature such as displaying a measured value or changing a threshold is only an instance of the generic contract.

The permanent specification is:

**Base44 application ↔ semantic integration contract ↔ Android Hardware Bridge ↔ physical hardware**

This boundary lets the hardware platform stay stable while different Base44 applications are generated for different devices and use cases.
