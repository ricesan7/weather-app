# AI Electronics Builder

AI electronics design + guided assembly + no-code MCU deployment.

## Product goal

Beginner mode is not a toy/lesson mode. It should let a user describe a practical device in ordinary language and complete it without writing code, choosing GPIOs, selecting libraries, or configuring a build.

Target flow:

1. Describe what you want to build.
2. The compiler asks only unresolved physical/safety requirements.
3. The system maps intent to capabilities and selects verified components, board and power topology.
4. Pins are allocated automatically and an authoritative CircuitGraph is compiled.
5. Deterministic electrical validation must pass before build/deployment artifacts are released.
6. The app renders graphical wiring/build steps from CircuitGraph.
7. The user assembles/solders one connection at a time while viewing the phone.
8. "Configure device" performs provisioning/deployment in one guided action.
9. The same app becomes the generated control panel, logger, settings UI, and diagnostic tool.

Advanced mode uses the same Design IR but exposes generated code, pin mapping, manifest, build output, logs, and direct editing.

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
  - CatalogPinAllocator
  - CatalogCircuitCompiler
  - CatalogElectricalValidator
  - DefaultProjectCompiler orchestration
- runtime-protocol
  - one-action deployment contracts
  - runtime-mutable settings contracts
- examples
  - practical ventilation reference fixture

## Next implementation targets

- BehaviorCompiler
- Runtime Manifest compiler
- ComponentAssets + pin anchors
- DiagramSpec / graphical wiring renderer
- guided solder-step compiler
- Android app/features
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

The user should mainly decide the goal, prepare parts, perform physical assembly, and complete required safety confirmations.
