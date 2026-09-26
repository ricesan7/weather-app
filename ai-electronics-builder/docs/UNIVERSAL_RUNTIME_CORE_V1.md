# Universal Runtime core v1

The beginner path uses one reusable runtime instead of generating and reflashing a custom program for every project.

## Runtime responsibilities

The host-testable C++17 core now provides:
- canonical manifest parsing;
- driver capability validation;
- runtime-mutable settings;
- setting constraints and relational checks;
- expression evaluation for AUTO/MANUAL rules;
- failsafe evaluation before normal rules;
- output actions and events;
- telemetry projection;
- required self-test dispatch;
- transport-independent runtime protocol dispatch.

## Golden behavior

The reference ventilation project can run without custom project source:
- AUTO: temperature threshold controls fan;
- MANUAL: phone setting controls fan;
- sensor timeout: fan forced OFF and fault event raised;
- threshold changes: applied at runtime;
- telemetry: temperature and fan state;
- self-tests: sensor probe and fan-output test.

## Boundary

This directory intentionally separates portable runtime logic from ESP32 hardware adapters.

Still required for real hardware:
- ESP32 GPIO adapter;
- ESP32 I2C/SHT31 driver adapter;
- nonvolatile manifest/settings storage;
- BLE transport adapter;
- firmware image/partition integration.

Those adapters should remain thin. Project behavior belongs in the manifest, not in project-specific firmware source.
