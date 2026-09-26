# AI Electronics Builder

AI electronics design + guided assembly + no-code MCU deployment.

## Product goal

Beginner mode is not a toy/lesson mode. It should let a user describe a practical device in ordinary language and complete it without writing code, choosing GPIOs, selecting libraries, or configuring a build.

Target flow:

1. Describe what you want to build.
2. The compiler asks only unresolved physical/safety requirements.
3. The system maps intent to capabilities and selects verified components, board and power topology.
4. Pins are allocated automatically and an authoritative CircuitGraph is compiled.
5. Deterministic electrical validation must pass.
6. Behavior rules, runtime-mutable settings, Runtime Manifest and phone UI are generated from the validated design.
7. The app renders graphical wiring/build steps from CircuitGraph.
8. The user assembles/solders one connection at a time while viewing the phone.
9. "Configure device" deploys the manifest/runtime configuration in one guided action.
10. The same app becomes the control panel, logger, settings UI and diagnostic tool.

Advanced mode uses the same Design IR but exposes generated code, pin mapping, manifest, build output, logs and direct editing.

## Implemented now

- core-model
  - Design IR / DesignCore / CircuitGraph
  - engineering component/board/power/pin specifications
- parts-db
  - engineering catalog abstraction
  - initial Golden reference catalog
- project-compiler
  - RequirementResolver / AutoDecisionEngine
  - CapabilityMapper
  - ComponentResolver / BoardSelector / PowerPlanner
  - PinAllocator / CircuitCompiler / ElectricalValidator
  - DefaultBehaviorCompiler
  - DefaultManifestCompiler
  - DefaultUiCompiler
  - DefaultDiagnosticCompiler
  - DefaultProjectCompiler orchestration
- runtime-protocol
  - one-action deployment contracts
  - runtime-mutable settings contracts

## Next implementation targets

- ComponentAssets + verified pin anchors
- DiagramSpec compiler
- graphical wiring renderer
- guided solder-step compiler
- Android app shell / Compose screens
- BLE Runtime protocol implementation
- ESP32-S3 Universal Runtime
- system regression harness

## UX release gate

Beginner mode targets zero manual:
- source/block programming
- GPIO selection
- library/driver selection
- build configuration
- technical transport/runtime-mode selection
- schematic-reading requirement
- rebuild/reflash for normal settings changes

The user should mainly decide the goal, prepare parts, perform physical assembly and complete required safety confirmations.
