# Diagnostics correlator v1

The diagnostics path now resolves self-test failures to the exact physical wiring steps generated from the same CircuitGraph.

## Flow

RUN_TEST failure
-> DiagnosticSpec trigger
-> concrete connection:<conn_id> references
-> DiagnosticCorrelator
-> GuidedBuildStep order / DiagramView
-> Android diagnostic UI
-> user taps "配線 N を確認"
-> Build screen opens that exact step
-> only that connection is highlighted.

## Compile-time correlation

DefaultDiagnosticCompiler no longer emits abstract placeholders such as:
- connection:sensor_power
- connection:i2c
- connection:driver

Instead, it inspects DesignCore.connections and emits actual connection IDs.

Sensor diagnostics include:
- sensor VDD;
- sensor GND;
- I2C SDA;
- I2C SCL.

Fan diagnostics include the verified load path:
- MCU -> driver control;
- external supply -> fan;
- driver -> fan;
- driver clamp/common power;
- driver GND;
- common ground to MCU.

## Beginner UI

When a self-test fails, the user sees:
- a plain-language diagnosis title;
- likely causes;
- one or more "配線 N を確認" actions.

The beginner does not need to read GPIO numbers, I2C logs, exception text, or raw diagnostic codes.

## Regression

Golden integration tests verify that:
- every diagnostic reference resolves to a real CircuitGraph connection;
- every referenced connection resolves to a GuidedBuildStep;
- sensor diagnostics resolve exactly the sensor power/GND/SDA/SCL set.
