# Android app shell v1

This milestone turns the previously separate P0 modules into one installable Android application.

## User flow

1. Home — describe the device in ordinary language.
2. Clarification — only unresolved user/safety questions are shown.
3. Design — board, safety state and generated-artifact summary.
4. Parts — selected MCU/components/external supply.
5. Wiring — deterministic DiagramSpec rendering from CircuitGraph.
6. Build — one connection at a time through GuidedBuildStateMachine.
7. Connect — Android BLE permission, scan and GATT handshake.
8. Configure device — manifest deploy, verification and required self-tests in one action.
9. Control — generated RuntimeControlDashboard for telemetry, settings, history and diagnostics.

## Beginner intent entrypoint

BeginnerIntentInterpreter is intentionally a local deterministic adapter for the currently verified catalog scope. It extracts:
- actuator presence;
- automation intent;
- logging intent;
- temperature threshold;
- humidity threshold.

It then hands structured facts to DefaultRequirementResolver.

This is not the long-term AI/LLM intent parser. The boundary is explicit so a later AI parser can replace the interpreter without changing the deterministic compiler/safety pipeline.

## Compiler composition

AppProjectEngine composes the existing production modules:
- DefaultRequirementResolver
- DefaultCapabilityMapper
- CatalogComponentResolver
- CatalogBoardSelector
- CatalogPowerPlanner
- CatalogPinAllocator
- CatalogCircuitCompiler
- CatalogElectricalValidator
- DefaultBehaviorCompiler
- DefaultDesignCoreAssembler
- DefaultDiagramCompiler through a DiagramCompiler adapter
- DefaultManifestCompiler
- DefaultUiCompiler
- DefaultDiagnosticCompiler

No electrical design truth is generated inside the Android UI.

## Current completion boundary

The app is buildable as an Android APK, the Golden ventilation project is covered by an app-entrypoint unit test, and projects can be saved/resumed locally with current safety rules re-applied on restore.

Still not equivalent to production release:
- real-device XIAO ESP32S3 + Android bench validation remains required;
- the local deterministic intent interpreter should be replaced/augmented by the AI conversational parser;
- persistent project storage is implemented in PROJECT_PERSISTENCE_V1.md;
- broad catalog coverage is not yet implemented;
- release signing/store packaging is not configured.
