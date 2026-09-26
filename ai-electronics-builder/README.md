# AI Electronics Builder

AI electronics design + guided assembly + no-code MCU deployment.

## Product goal

Beginner mode is not a toy/lesson mode. It should let a user describe a practical device in ordinary language and complete it without writing code, choosing GPIOs, selecting libraries, or configuring a build.

Target flow:

1. Describe what you want to build.
2. The compiler asks only unresolved physical/safety requirements.
3. The system selects a verified MCU/components/power topology.
4. Deterministic validation checks electrical compatibility.
5. The app renders an authoritative graphical wiring/build guide.
6. The user assembles/solders one connection at a time while viewing the phone.
7. "Configure device" performs provisioning/deployment in one guided action.
8. The same app becomes the generated control panel, logger, settings UI, and diagnostic tool.

Advanced mode uses the same Design IR but exposes generated code, pin mapping, manifest, build output, logs, and direct editing.

## Architecture

The single source of truth is Design IR:

natural language -> requirements -> capabilities -> components -> power -> pins -> CircuitGraph -> validation -> DesignCore -> diagrams / manifest / UI / diagnostics -> finalized Design IR

Electrical safety is deterministic. AI may propose and explain, but it does not override the validator.

## Implemented now

- core-model
  - Design IR
  - DesignCore
  - CircuitGraph / ReleaseBundle
- project-compiler
  - DefaultRequirementResolver
  - RuleBasedAutoDecisionEngine
  - DefaultProjectCompiler orchestration
  - DefaultDesignCoreAssembler
  - compiler entrypoint tests
- runtime-protocol
  - one-action deployment contracts
  - runtime-mutable settings contracts
- examples
  - practical ventilation reference fixture

## Next implementation targets

- DB-backed CapabilityMapper / ComponentResolver
- BoardSelector
- PowerPlanner
- PinAllocator
- CircuitCompiler
- deterministic ElectricalValidator
- DiagramSpec / graphical wiring renderer
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
