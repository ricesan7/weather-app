# Dynamic Component Usability Design

Date: 2026-09-28
Status: Approved design specification
Scope: ai-electronics-builder Android app + Base44 CircuitFlow hardwareBridge integration

## 1. Goal

Allow previously unregistered electronic parts to become usable in design automatically when trustworthy specification data is available and sufficient for the requested role.

The system must no longer treat "not present in GoldenEngineeringCatalog" or a static `designReady=false` state as the primary reason a part cannot be used.

Instead, usability is determined from:

1. The requested role/capability in the current project.
2. The engineering data required for that role.
3. Source quality and confidence.
4. Electrical compatibility.
5. Runtime/driver support.
6. Safety constraints.

A researched part that satisfies the requirements becomes available to the compiler without editing the hard-coded golden catalog.

## 2. Existing System

### Base44

CircuitFlow already exposes `hardwareBridge.action = research_component`.

The research response can include:

- manufacturer/model/display name
- component kind
- primary interface
- operating voltage range
- preferred supply voltage
- maximum current
- I2C address
- external-power requirement
- driver ID or structured driver profile
- capabilities
- aliases
- pins
- sources and source authority
- confidence
- notes

Research prioritizes manufacturer datasheets, manufacturer product pages, then authorized distributors.

### Android

The Android compiler currently uses:

- `GoldenEngineeringCatalog`
- `CatalogComponentResolver`
- `ComponentSpec.designReady`

`CatalogComponentResolver` filters candidates with `designReady == true`.

This makes catalog registration, rather than sufficient engineering knowledge, the effective gate.

## 3. Design Principle

Separate four concepts that are currently conflated:

- **Known**: the system has a record for the part.
- **Researched**: engineering data and sources have been collected.
- **Usable for a role**: enough trustworthy data exists for the current project role.
- **Runtime-supported**: Android/ESP32 runtime can actually read/control the part.

A part can be researched without being usable. A part can be electrically usable but runtime-unsupported. A part can be usable for one role while lacking data for another.

## 4. Usability Result

Add an explicit evaluation result:

```kotlin
enum class ComponentUsabilityStatus {
    READY,
    NEEDS_INFO,
    UNSUPPORTED,
}
```

The evaluator returns a structured report:

```kotlin
data class ComponentUsabilityReport(
    val status: ComponentUsabilityStatus,
    val missingFields: Set<String>,
    val blockingReasons: List<String>,
    val warnings: List<String>,
    val acceptedSourceIds: Set<String>,
)
```

Semantics:

### READY

The part may be offered to `CatalogComponentResolver` for the requested role.

Requirements:

- role-specific engineering fields are complete
- at least one acceptable source supports critical electrical facts
- voltage/current/interface/pin constraints are internally consistent
- the selected board/power topology can support the part, or the compiler can add the required support component
- a supported runtime driver or supported structured driver profile exists when runtime interaction is required

### NEEDS_INFO

The part appears potentially usable, but one or more required facts are missing.

The report must identify exact missing fields so the research layer can request only those facts.

Examples:

- maximum current unknown for a load
- SDA/SCL pin roles missing for an I2C sensor
- operating voltage range incomplete
- protocol timing missing for a pulse sensor
- source authority too weak for a safety-critical electrical value

### UNSUPPORTED

The specifications may be complete, but the current system cannot safely use the part.

Examples:

- unsupported protocol family
- required initialization sequence cannot be expressed by current runtime
- board logic level is incompatible and no supported level shifter can satisfy it
- load current exceeds available supported drivers/power design
- mains/high-energy equipment is outside the current supported safety class

## 5. Role-Specific Required Information

Do not require every possible field for every part. Required fields are derived from the requested capability and component kind.

### I2C sensor

Required:

- manufacturer/model identity
- voltage range
- VCC/GND/SDA/SCL pin roles
- I2C address or address-selection rule
- provided measurement capability
- supported driver or supported I2C register profile
- authoritative source for voltage/pin/protocol facts

### GPIO digital input

Required:

- voltage/logic compatibility
- GND and signal pin
- active-high/active-low semantics where relevant
- pull-up/pull-down requirement if required
- supported `GPIO_DIGITAL_INPUT` runtime profile
- authoritative electrical source

### GPIO digital output / low-current actuator

Required:

- operating voltage
- current requirement
- control pin
- whether direct GPIO drive is valid
- supported output profile

If direct drive is unsafe, the part must not become READY until an appropriate supported driver stage is resolved.

### Load / fan / relay / solenoid

Required:

- operating voltage
- maximum or design current
- positive/negative/load terminals
- external-power requirement
- switching method
- support-driver requirement
- flyback/clamp requirement where applicable
- authoritative electrical source

### DHT-style pulse sensor

Required:

- voltage range
- DATA/VCC/GND pins
- protocol family
- start pulse timing
- zero/one pulse discrimination thresholds
- sample interval
- telemetry mappings
- authoritative protocol source

### Other protocols

SPI, UART, RS485, ONE_WIRE and future protocol families use their own requirement profiles.

A protocol is not READY merely because its name is known; the current runtime must support the required interaction.

## 6. Source Trust Policy

Critical engineering facts must be backed by acceptable sources.

Priority:

1. Manufacturer datasheet
2. Manufacturer product documentation
3. Authorized distributor technical documentation
4. Other source only for non-critical supplemental information

Critical facts include:

- voltage limits
- current limits
- pin assignments
- logic thresholds
- protocol timing
- load ratings

Blogs, marketplaces and forum posts must not independently authorize a component.

Source records remain attached to the dynamic component so the usability decision is auditable.

## 7. Dynamic Catalog Overlay

Do not mutate `GoldenEngineeringCatalog`.

Add a composable catalog layer, conceptually:

```text
GoldenEngineeringCatalog
        +
ResearchedComponentCatalog
        ↓
CompositeEngineeringCatalog
        ↓
CatalogComponentResolver
```

### GoldenEngineeringCatalog

Contains bundled, regression-tested, known-good components.

### ResearchedComponentCatalog

Contains components produced from research data and accepted by the usability evaluator.

### CompositeEngineeringCatalog

Presents a single `EngineeringCatalog` interface.

Rules:

- golden entries take precedence for an identical canonical component ID
- dynamic aliases may resolve to golden components
- rejected/partial research records remain available for retry, but are not returned as design-ready candidates
- dynamic components are scoped to the application/user data store and survive project restarts
- research records retain their source metadata and evaluation report

## 8. Component Data Model Changes

Extend Android engineering models so Base44 research results can be represented without data loss.

### ElectricalInterface

Add at minimum:

- ONE_WIRE
- RS485

Retain existing GPIO/I2C/SPI/UART/PWM/ADC/USB.

### ComponentPinRole

Add at minimum:

- DATA
- SIGNAL_INPUT
- SIGNAL_OUTPUT

Existing roles remain unchanged.

### Dynamic specification metadata

Introduce a wrapper or adjacent metadata object rather than overloading `ComponentSpec` with research state.

Recommended:

```kotlin
data class ResearchedComponentRecord(
    val canonicalId: String,
    val requestedName: String,
    val spec: ComponentSpec,
    val aliases: Set<String>,
    val sourceRecords: List<ComponentSourceRecord>,
    val confidence: Double,
    val driverProfile: RuntimeDriverProfile?,
    val evaluation: ComponentUsabilityReport,
    val researchedAtEpochMs: Long,
)
```

## 9. Runtime Driver Compatibility

A component is READY for runtime interaction only if one of these is true:

1. `driverId` resolves to an existing runtime driver.
2. A structured driver profile maps to a supported runtime family.

Initially supported research profile families remain constrained to explicit implementations such as:

- GPIO_DIGITAL_INPUT
- GPIO_DIGITAL_OUTPUT
- DHT_PULSE_SENSOR
- I2C_REGISTER_SENSOR
- NONE for components that require no active runtime driver

Unknown arbitrary source code must never be accepted as a driver.

If specifications are complete but the driver family is unsupported, status is `UNSUPPORTED`, not `NEEDS_INFO`.

## 10. Research-to-Compiler Flow

Target flow:

```text
Project requires capability
        ↓
CatalogComponentResolver cannot satisfy capability
        ↓
Search local researched catalog / aliases
        ↓
Still unresolved
        ↓
Base44 research_component
        ↓
Normalize research result
        ↓
ComponentUsabilityEvaluator
        ├── READY
        │     ↓
        │  Save researched component
        │     ↓
        │  Add to dynamic catalog overlay
        │     ↓
        │  Re-run compiler automatically
        │
        ├── NEEDS_INFO
        │     ↓
        │  Show exact missing information
        │     ↓
        │  Re-research missing fields and/or ask user only when necessary
        │
        └── UNSUPPORTED
              ↓
           Show concrete unsupported reason
```

The user should not have to leave the project and manually register a component.

## 11. Compiler Integration

`CatalogComponentResolver` should continue to operate on the `EngineeringCatalog` abstraction.

The resolver itself should not perform network research.

Responsibilities:

- resolver: deterministic component selection
- research service: obtain candidate specification data
- usability evaluator: decide whether researched data is admissible
- catalog overlay: expose admissible dynamic components
- application orchestration: trigger research and retry compilation

This keeps the compiler deterministic and testable.

## 12. Failure and Retry Behavior

### Research unavailable

Do not mark the part unsupported.

Return `NEEDS_INFO` with a research/network error and retain the unresolved capability.

### Ambiguous identity

If the requested name could refer to multiple electrically different products or modules, do not merge them.

Require disambiguation by manufacturer/model/module variant.

### Partial research

Persist the partial research record and missing-field list. Retry should enrich the same record rather than create duplicates.

### Conflicting sources

Critical conflicts block READY until resolved.

Manufacturer documentation takes precedence over lower-authority sources, but materially conflicting manufacturer documents should be surfaced rather than silently selected.

### Compiler failure after READY

READY means the component specification itself is admissible, not that every possible project topology will compile.

Normal board, power, pin and electrical validators still run after dynamic catalog admission.

## 13. Safety Boundary

Dynamic admission must not weaken existing electrical validation.

For extra-low-voltage electronics, the normal validator decides topology safety.

For future 100 V / 200 V AC equipment, inverter control and other hazardous-energy systems, introduce separate component classes and safety policies before permitting automatic READY status.

Until that subsystem exists, such parts must return `UNSUPPORTED` or require a dedicated high-energy design path. They must not be admitted using the low-voltage component rules.

## 14. UI Behavior

When an unknown part is encountered, the Android app should show a progress state such as:

- "部品仕様を調査しています"
- "メーカー資料を確認しています"
- "使用条件を確認しています"

Possible results:

### READY

"必要な仕様を確認できました。この部品を設計に使用します。"

Then automatically retry compilation and move forward when successful.

### NEEDS_INFO

Display the exact missing facts, not a generic "verification pending".

Example:

"この部品を使用するには、最大消費電流とDATA端子の電圧仕様が必要です。追加調査します。"

### UNSUPPORTED

Display the technical reason.

Example:

"部品仕様は確認できましたが、現在のRuntimeはこの通信方式に対応していません。"

Do not trap the user on a dead-end verification screen.

## 15. Persistence

Persist researched component records locally so the same part does not require repeated research.

Minimum persisted data:

- canonical identity
- aliases
- normalized ComponentSpec
- driver profile
- source records
- confidence
- usability report
- research timestamp
- schema version

Re-evaluate the record when:

- the schema version changes
- supported runtime driver families change
- the user supplies newer authoritative specifications
- a previous NEEDS_INFO record receives missing data

## 16. Base44 Contract

The existing `research_component` action remains the public research entrypoint.

The Android client should use its structured response rather than parse free-form prose.

Base44 remains responsible for:

- web research
- source prioritization
- normalization of retrieved facts
- returning unknown values as null/empty rather than guessing

Android remains responsible for:

- applicability to the current role
- electrical/runtime compatibility
- READY / NEEDS_INFO / UNSUPPORTED decision
- persistence and compiler admission

The Base44 response may include confidence, but confidence alone must never set READY.

## 17. Compatibility

Existing golden projects must behave identically.

Acceptance requirements:

- existing golden component tests continue to pass
- GoldenEngineeringCatalog remains valid without dynamic records
- dynamic catalog can be empty
- resolver output remains deterministic for a fixed composite catalog
- no network dependency is introduced inside project-compiler

## 18. Tests

### Unit tests

Add tests for:

- I2C sensor with sufficient specs -> READY
- I2C sensor missing address/pin data -> NEEDS_INFO
- load missing max current -> NEEDS_INFO
- complete unsupported protocol -> UNSUPPORTED
- supported DHT profile -> READY
- weak-only sources for critical voltage data -> NEEDS_INFO
- conflicting critical source values -> NEEDS_INFO or blocked evaluation
- golden component precedence over dynamic duplicate
- dynamic alias resolution
- persisted researched record reload

### Compiler tests

Add tests proving:

- unresolved capability fails before dynamic admission
- READY dynamic component satisfies the same capability after overlay insertion
- support components are still added recursively
- power/pin/electrical validation still runs
- runtime driver incompatibility cannot bypass the compiler

### Integration test

Simulate:

1. project asks for a capability absent from golden catalog
2. research response is returned
3. evaluator marks READY
4. record is persisted
5. composite catalog changes
6. compile retry succeeds
7. app transitions to DESIGN

Also test NEEDS_INFO and UNSUPPORTED paths.

## 19. Implementation Boundaries

Primary Android areas expected to change:

- `core-model`
- `parts-db`
- `application-core`
- `app`
- tests in corresponding modules

Likely new responsibilities/files:

- ComponentUsabilityEvaluator
- ComponentRequirementProfile / role requirement rules
- ResearchedComponentRecord
- ResearchedComponentRepository
- CompositeEngineeringCatalog
- Base44 component research client/adapter
- research orchestration state in BuilderAppViewModel

Base44 changes should be limited to contract gaps discovered during implementation. Existing research logic should be reused rather than replaced.

## 20. Definition of Done

The feature is complete when a user can request a component that is not in `GoldenEngineeringCatalog`, the system can research it, validate that the necessary trustworthy specifications and runtime support exist, dynamically admit it, re-run project compilation, and continue to the design screen without manual catalog editing.

The system must still refuse or pause components whose information is incomplete, whose evidence is insufficient, whose electrical use is unsafe, or whose protocol/runtime is unsupported.
