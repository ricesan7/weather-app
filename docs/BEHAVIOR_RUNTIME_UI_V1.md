# Behavior, runtime manifest and generated UI v1

This milestone removes source-code editing from the normal project flow.

## BehaviorCompiler

DefaultBehaviorCompiler converts project capabilities and user-level settings into:
- AUTO rules;
- hysteresis thresholds;
- MANUAL override rules;
- sensor-failure failsafe;
- runtime-mutable settings;
- phone logging configuration;
- fault events.

For the reference ventilation controller, normal settings include:
- mode;
- temperature ON/OFF thresholds;
- humidity ON/OFF thresholds;
- manual fan control.

All are runtime mutable and therefore do not require recompiling or reflashing firmware.

## Runtime Manifest

DefaultManifestCompiler derives the Universal Runtime manifest from the same DesignCore that produced the validated CircuitGraph.

The Golden XIAO manifest includes:
- I2C SDA = D4 / GPIO5;
- I2C SCL = D5 / GPIO6;
- fan-control GPIO = D3 / GPIO4;
- component driver IDs;
- behavior rules;
- runtime settings;
- failsafe rules;
- telemetry channels;
- self-test commands.

## Generated phone UI

DefaultUiCompiler derives phone screens from capabilities/settings:
- Dashboard: temperature, humidity, fan state, AUTO/MANUAL, manual fan control;
- Settings: threshold sliders;
- History: automatically generated charts for logged channels.

The user does not build the UI manually.

## Diagnostics

DefaultDiagnosticCompiler creates required self-tests and maps failures back to physical build areas such as sensor power, I2C, fan power, driver and common ground.
