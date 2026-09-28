# Dynamic Component Usability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let researched, previously unregistered electronic parts become compiler-usable when their role-specific engineering data, authoritative evidence, electrical constraints, and supported runtime profile are sufficient, then automatically retry design compilation.

**Architecture:** Keep `GoldenEngineeringCatalog` immutable and introduce a dynamic researched-component overlay plus a role-aware usability evaluator. Component-resolution gaps become a structured compiler result; the application layer can research/admit a concrete part, persist it, update the overlay, and retry the compiler without putting network logic inside the deterministic compiler.

**Tech Stack:** Kotlin/JVM 17, Android/Kotlin, JUnit 5, SQLiteOpenHelper, org.json, existing Base44 `hardwareBridge/research_component` contract.

**Spec:** `ai-electronics-builder/docs/superpowers/specs/2026-09-28-dynamic-component-usability-design.md`

## Global Constraints

- `GoldenEngineeringCatalog` remains unchanged as the bundled known-good catalog.
- Critical electrical facts require authoritative source evidence; confidence alone never authorizes a part.
- Unknown values are not guessed.
- Runtime interaction is READY only for explicitly supported driver IDs/families.
- Current low-voltage admission must fail closed for 100 V / 200 V, three-phase, VFD/inverter, SSR, contactor, and similar hazardous-energy equipment.
- Network research stays outside `project-compiler`; compilation remains deterministic for a fixed catalog.
- Existing golden projects retain their current behavior.

## Review Focus

- A candidate with complete-looking fields but only weak/non-authoritative sources must remain NEEDS_INFO; Task 1 tests this.
- A candidate with an unsupported driver family must be UNSUPPORTED rather than NEEDS_INFO; Task 1 tests this.
- A dynamic duplicate of a golden component must not override the golden record; Task 2 tests this.
- A compiler capability gap must be returned structurally, not detected by parsing an error message; Task 3 tests this.
- A READY component that later fails board/power/electrical validation must still be blocked by the existing validators; Task 5 integration tests this.

---

### Task 1: Research domain model and usability evaluator

**Files:**
- Modify: `ai-electronics-builder/core-model/src/main/kotlin/com/aielectronics/core/model/EngineeringSpecs.kt`
- Create: `ai-electronics-builder/parts-db/src/main/kotlin/com/aielectronics/parts/ResearchedComponents.kt`
- Modify: `ai-electronics-builder/parts-db/build.gradle.kts`
- Create: `ai-electronics-builder/parts-db/src/test/kotlin/com/aielectronics/parts/ComponentUsabilityEvaluatorTest.kt`

**Interfaces:**
- Produces: `ComponentResearchCandidate`, `ComponentSourceRecord`, `SourceAuthority`, `RuntimeDriverFamily`, `RuntimeDriverProfile`, `ComponentUsabilityStatus`, `ComponentUsabilityReport`, `ResearchedComponentRecord`, `ComponentUsabilityEvaluator.evaluate(candidate, requiredCapabilities)`.
- Extends: `ElectricalInterface` with `ONE_WIRE`, `RS485`; `ComponentPinRole` with `DATA`, `SIGNAL_INPUT`, `SIGNAL_OUTPUT`.

- [ ] **Step 1: Write failing evaluator tests**

Add focused tests for:
- authoritative I2C temperature sensor with VCC/GND/SDA/SCL, address, voltage, capability and supported profile -> READY;
- same sensor missing address -> NEEDS_INFO with `i2c_address`;
- load/actuator missing `currentMaxMa` -> NEEDS_INFO;
- complete candidate with unsupported runtime family -> UNSUPPORTED;
- DHT-style ONE_WIRE + DHT_PULSE_SENSOR with required timing fields -> READY;
- critical voltage/pin facts with only OTHER sources -> NEEDS_INFO;
- hazardous-energy candidate keywords/interface context -> UNSUPPORTED.

- [ ] **Step 2: Run the parts-db evaluator tests and verify RED**

Run: `./gradlew :parts-db:test --tests '*ComponentUsabilityEvaluatorTest*'`

Expected: FAIL because the research-domain types/evaluator and enum values do not exist.

- [ ] **Step 3: Implement the minimal research-domain types and evaluator**

Implement exact result semantics:
- `READY`: role-specific required fields, authoritative evidence, internal voltage range validity, and supported runtime driver/profile.
- `NEEDS_INFO`: missing required engineering facts or insufficient authoritative evidence.
- `UNSUPPORTED`: known unsupported runtime/protocol or hazardous-energy class.
- `ComponentResearchCandidate.toComponentSpec(designReady: Boolean)` converts normalized research data without inventing values.

- [ ] **Step 4: Run evaluator tests and the entire parts-db suite**

Run: `./gradlew :parts-db:test`

Expected: PASS, zero test failures.

- [ ] **Step 5: Commit**

Commit message: `feat: add researched component usability evaluation`

### Task 2: Dynamic researched-component catalog overlay

**Files:**
- Create: `ai-electronics-builder/parts-db/src/main/kotlin/com/aielectronics/parts/ResearchedComponentCatalog.kt`
- Create: `ai-electronics-builder/parts-db/src/test/kotlin/com/aielectronics/parts/ResearchedComponentCatalogTest.kt`

**Interfaces:**
- Consumes: `ResearchedComponentRecord`, `ComponentUsabilityStatus.READY` from Task 1.
- Produces: `MutableResearchedComponentCatalog.upsert(record)`, `record(canonicalId)`, `recordByAlias(alias)`; `CompositeEngineeringCatalog(primary, overlay)`.

- [ ] **Step 1: Write failing overlay tests**

Assert:
- only READY researched records appear as design-ready components;
- NEEDS_INFO/UNSUPPORTED records remain retrievable as research records but are absent from compiler candidates;
- aliases resolve case-insensitively to the stored researched record;
- golden/primary component wins when canonical IDs collide;
- boards and power supplies still come from the primary catalog.

- [ ] **Step 2: Run the catalog tests and verify RED**

Run: `./gradlew :parts-db:test --tests '*ResearchedComponentCatalogTest*'`

Expected: FAIL because overlay classes do not exist.

- [ ] **Step 3: Implement the mutable overlay and composite catalog**

Keep insertion deterministic by canonical ID; do not mutate `GoldenEngineeringCatalog`.

- [ ] **Step 4: Run all parts-db tests**

Run: `./gradlew :parts-db:test`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add dynamic engineering catalog overlay`

### Task 3: Structured compiler result for unresolved hardware capabilities

**Files:**
- Modify: `ai-electronics-builder/project-compiler/src/main/kotlin/com/aielectronics/compiler/CompilerContracts.kt`
- Modify: `ai-electronics-builder/project-compiler/src/main/kotlin/com/aielectronics/compiler/CatalogComponentResolver.kt`
- Modify: `ai-electronics-builder/project-compiler/src/main/kotlin/com/aielectronics/compiler/DefaultProjectCompiler.kt`
- Modify: exhaustive `CompileResult` consumers found by code search in app/test-harness.
- Modify/Create tests under `ai-electronics-builder/project-compiler/src/test/kotlin/com/aielectronics/compiler/`

**Interfaces:**
- Produces: `UnresolvedHardwareCapabilitiesException(val capabilities: Set<CapabilityId>)`.
- Produces: `CompileResult.NeedComponentResearch(val capabilities: Set<CapabilityId>)`.
- Preserves: existing `ComponentResolver.resolve(...): Result<ResolvedComponents>`.

- [ ] **Step 1: Write failing compiler tests**

Assert:
- a missing hardware capability yields `NeedComponentResearch` carrying the exact capability set;
- a non-research component-resolver exception remains `CompileResult.Failed(stage="component_resolve")`;
- current golden temperature/humidity/fan resolution still succeeds.

- [ ] **Step 2: Run project-compiler tests and verify RED**

Run: `./gradlew :project-compiler:test --tests '*CatalogResolutionTest*' --tests '*DefaultProjectCompilerTest*'`

Expected: FAIL because the structured result/exception does not exist.

- [ ] **Step 3: Implement typed unresolved-capability propagation**

`CatalogComponentResolver` throws the typed exception when no design-ready candidate covers the remaining hardware capabilities. `DefaultProjectCompiler` maps only that type to `NeedComponentResearch`; all other failures keep existing behavior.

- [ ] **Step 4: Update exhaustive consumers and run compiler + application-core tests**

Run: `./gradlew :project-compiler:test :application-core:test`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: expose component research needs from compiler`

### Task 4: Researched-component persistence

**Files:**
- Extend: `ai-electronics-builder/parts-db/src/main/kotlin/com/aielectronics/parts/ResearchedComponents.kt`
- Create: `ai-electronics-builder/parts-db/src/main/kotlin/com/aielectronics/parts/ResearchedComponentCodec.kt`
- Create: `ai-electronics-builder/parts-db/src/test/kotlin/com/aielectronics/parts/ResearchedComponentCodecTest.kt`
- Modify: `ai-electronics-builder/project-storage-android/build.gradle.kts`
- Create: `ai-electronics-builder/project-storage-android/src/main/kotlin/com/aielectronics/storage/android/SqliteResearchedComponentRepository.kt`

**Interfaces:**
- Produces: `ResearchedComponentRepository.list/load/save`.
- Produces: `InMemoryResearchedComponentRepository`.
- Produces: deterministic string codec used by Android SQLite persistence.
- Produces: `SqliteResearchedComponentRepository(context)` backed by its own `ai_electronics_researched_components.db` database to avoid migration risk to the existing project database.

- [ ] **Step 1: Write failing repository/codec round-trip tests**

Assert all engineering fields, aliases, sources, driver profile, evaluation, timestamp, and schema version survive encode/decode and in-memory save/load.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :parts-db:test --tests '*ResearchedComponentCodecTest*'`

Expected: FAIL because repository/codec do not exist.

- [ ] **Step 3: Implement codec, repositories, and thin SQLite adapter**

The SQLite adapter stores canonical ID + encoded payload, uses replace-on-ID semantics, and never changes `SqliteProjectRepository` schema/version.

- [ ] **Step 4: Run parts-db tests and compile Android storage**

Run: `./gradlew :parts-db:test :project-storage-android:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: persist researched component records`

### Task 5: Admission orchestration and automatic compiler retry

**Files:**
- Modify: `ai-electronics-builder/application-core/src/main/kotlin/com/aielectronics/application/ApplicationProjectEngine.kt`
- Create: `ai-electronics-builder/application-core/src/main/kotlin/com/aielectronics/application/ComponentAdmissionService.kt`
- Create: `ai-electronics-builder/application-core/src/test/kotlin/com/aielectronics/application/ComponentAdmissionServiceTest.kt`
- Modify: `ai-electronics-builder/app/src/main/kotlin/com/aielectronics/builder/AppState.kt`
- Modify: `ai-electronics-builder/app/src/main/kotlin/com/aielectronics/builder/BuilderAppViewModel.kt`
- Modify: `ai-electronics-builder/app/src/main/kotlin/com/aielectronics/builder/BuilderAppScreen.kt`
- Modify/Create app tests for the retry/state behavior.

**Interfaces:**
- Consumes: evaluator, overlay, persistence, `CompileResult.NeedComponentResearch`.
- Produces: `ComponentAdmissionService.admit(candidate, requiredCapabilities): ComponentUsabilityReport`.
- Produces ViewModel entrypoint: `submitComponentResearch(candidate)` for structured specs received from a research source.
- State exposes research status/message/missing fields instead of a dead-end generic verification state.

- [ ] **Step 1: Write failing admission/retry tests**

Assert:
- READY candidate is persisted, added to overlay, and makes the same previously unresolved compile proceed on retry;
- NEEDS_INFO is not admitted and exposes exact missing fields;
- UNSUPPORTED is not admitted and exposes blocking reason;
- after admission, existing board/power/electrical validation still executes and may block an unsafe topology.

- [ ] **Step 2: Run targeted tests and verify RED**

Run: `./gradlew :application-core:test :app:testDebugUnitTest`

Expected: FAIL because admission/state/retry behavior does not exist.

- [ ] **Step 3: Implement admission service and wire a shared composite catalog into the engine**

Ensure the same mutable overlay instance is used by resolver, board selector, power planner, pin allocator, circuit compiler, validator, diagram compiler, and manifest compiler.

- [ ] **Step 4: Wire ViewModel state and UI behavior**

On `NeedComponentResearch`, present a concrete research-required state rather than treating it as a compile error. On `submitComponentResearch`, admit/evaluate; if READY, automatically invoke the compile flow again and transition to DESIGN only after normal validation succeeds.

- [ ] **Step 5: Run application + app suites**

Run: `./gradlew :application-core:test :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: admit researched parts and retry design automatically`

### Task 6: Base44 research adapter and safe credential boundary

**Files:**
- Create: `ai-electronics-builder/application-core/src/main/kotlin/com/aielectronics/application/ComponentResearchService.kt`
- Create: `ai-electronics-builder/app/src/main/kotlin/com/aielectronics/builder/Base44ComponentResearchService.kt`
- Create: `ai-electronics-builder/app/src/test/kotlin/com/aielectronics/builder/Base44ComponentResearchServiceTest.kt`
- Modify: app wiring only where an existing paired Hardware Bridge credential provider is available.

**Interfaces:**
- Produces: `ComponentResearchRequest(requestedName, categoryHint, projectGoal, requiredCapabilities)`.
- Produces: `ComponentResearchService.research(request): Result<ComponentResearchCandidate>`.
- Base44 adapter sends `action=research_component`, `device_id`, `device_token`, `requested_name`, `category_hint`, `project_goal`, `required_capabilities` and parses the existing structured contract.
- Credentials are injected at runtime; no device token is placed in source code or BuildConfig.

- [ ] **Step 1: Write failing parser/service tests using an injectable HTTP transport**

Assert valid Base44 JSON maps to the normalized candidate, null/empty unknown values stay unknown, source authority is preserved, and non-2xx/invalid JSON returns failure.

- [ ] **Step 2: Run app tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*Base44ComponentResearchServiceTest*'`

Expected: FAIL because adapter/service types do not exist.

- [ ] **Step 3: Implement the adapter without weakening Base44 authentication**

Use the existing Hardware Bridge session credentials when available. If the Android branch has no credential source yet, keep the adapter injectable and leave automatic remote calls disabled rather than introducing a second authentication system; `submitComponentResearch` remains the safe admission path.

- [ ] **Step 4: Run app tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add Base44 component research adapter`

### Task 7: Full regression, build, and branch review

**Files:**
- No production files unless a failing regression requires a TDD fix.
- Update documentation only for exact final behavior if needed.

**Interfaces:**
- Consumes all previous tasks.
- Produces verification evidence and a reviewable feature branch.

- [ ] **Step 1: Run the full JVM/Android test suite**

Run: `./gradlew test testDebugUnitTest` where supported by the root project; otherwise run every module test task exposed by `./gradlew tasks`.

Expected: zero failures.

- [ ] **Step 2: Build the Android app**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Review branch diff against main**

Check specifically:
- no string parsing of compiler errors;
- no mutation of `GoldenEngineeringCatalog`;
- no hard-coded Base44 device token;
- no low-voltage READY path for hazardous-energy equipment;
- no skipped existing electrical validator.

- [ ] **Step 4: Run final regression after any review fixes**

Run the same full test + app build commands again.

Expected: zero failures and BUILD SUCCESSFUL.

- [ ] **Step 5: Prepare pull request**

Create a PR from `feature/dynamic-component-usability` to `main` only after verification. Do not merge without explicit user authorization.
