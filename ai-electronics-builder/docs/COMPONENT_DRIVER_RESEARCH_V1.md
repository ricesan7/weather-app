# Component Driver Research V1

## Purpose

When a finalized electronics project references a component that is not in the verified
catalog, the application can research the exact part, persist its engineering data, and
resolve a compatible runtime driver without silently substituting another component.

The deterministic compiler remains authoritative.

## Pipeline

1. The compiler extracts explicitly requested parts.
2. A known design-ready catalog entry is reused.
3. An unknown part produces `ComponentResearchRequest`.
4. Research checks current web evidence, preferring manufacturer datasheets and product
   documentation.
5. Android validates identity, voltage, interface, pins, capabilities, evidence and
   runtime-driver information.
6. The validated record is stored in the local SQLite research catalog.
7. Only `DESIGN_READY` components are exposed to the compiler.
8. The compiler is automatically retried.
9. A runtime-ready declarative driver profile is encoded into the Project Manifest.
10. The ESP32 runtime validates the profile family again before accepting deployment.

## Driver profiles, not arbitrary source code

Research is not permitted to return arbitrary executable C/C++ as an automatically
trusted driver. It can only describe one of the allow-listed profile families.

V1 families:

- `DHT_PULSE_SENSOR`: runtime-ready for validated DHT11/DHT22 timing profiles.
- `GPIO_DIGITAL_INPUT`: runtime-ready for simple digital inputs.
- `GPIO_DIGITAL_OUTPUT`: schema reserved; not automatically runtime-ready yet.
- `I2C_REGISTER_SENSOR`: schema reserved; not automatically runtime-ready yet.
- `NONE`: no safe generic runtime profile could be established.

A profile contains:

- deterministic driver ID;
- interface type;
- sampling interval;
- bounded key/value protocol parameters;
- semantic telemetry mappings;
- evidence source IDs;
- verification status.

## DHT validation

A DHT profile is promoted to `RUNTIME_READY` only when Android confirms:

- sensor component kind;
- `ONE_WIRE` interface;
- DHT11 or DHT22 variant;
- sampling interval >= 1000 ms;
- bounded start-low and bit timing thresholds;
- unambiguous 0/1 threshold ordering;
- both temperature and humidity telemetry mappings.

The ESP32 implementation validates these constraints again during deployment and rejects
modified or unsupported profiles.

The runtime performs the start pulse, captures the 40-bit pulse frame, validates the
checksum, decodes DHT11/DHT22 values, and exposes normalized telemetry.

## Runtime compatibility

HELLO/CAPABILITIES advertises supported profile families. Android compares the manifest's
required profile families with the connected runtime before sending the manifest.

A project that requires a generated driver is blocked before transfer when the connected
runtime does not advertise that family.

## Fail-closed behavior

A researched component does not become `DESIGN_READY` when:

- official evidence is missing;
- electrical identity is incomplete;
- the runtime driver is missing;
- a proposed driver profile violates the allow-list or parameter bounds;
- the protocol is too complex for an implemented profile family;
- the project involves hazardous-energy equipment that is not explicitly supported.

100/200 V mains, three-phase equipment, VFDs/inverters, contactors, SSRs and similar
industrial power equipment are not automatically promoted by this low-voltage driver
research path.

## Persistence

Research records, component specifications and runtime driver profiles are persisted in
the Android SQLite research catalog. The same verified profile therefore survives app
restart and can be reused by later compiler runs.

## Source of truth

Research proposes facts and a bounded runtime profile. It does not bypass:

- Component Research validation;
- deterministic component resolution;
- pin allocation;
- CircuitGraph generation;
- electrical validation;
- manifest generation;
- runtime capability negotiation;
- runtime-side driver profile validation.
