# Zero-config UX gate

Beginner release criteria:

- manual programming actions: 0
- manual pin decisions: 0
- manual library choices: 0
- manual build configuration: 0
- technical mode choices: 0
- required schematic reading: 0
- rebuilds for normal settings changes: 0
- raw compiler/error-log dependency: 0
- app switches during normal design/build/deploy/use flow: 0

Normal runtime-mutable settings include thresholds, hysteresis, schedules, modes, logging interval, retention, calibration, presets, and manual override.

The user should be asked only about requirements that change the physical goal, physical environment, power/load constraints, or safety behavior and cannot be safely derived.
