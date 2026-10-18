# Base44 Design Handoff V1

## Purpose

CircuitFlow/Base44 is the conversational requirements and application front end.
The Android AI Electronics Builder remains the authoritative hardware compiler and
deployment bridge.

When the user selects **仕様を確定して実機設計へ送る**, CircuitFlow publishes a
versioned Design Handoff. The Android app receives it and automatically runs the
same deterministic compiler, electrical validation, Project Graph generation,
firmware/runtime manifest generation, wiring generation, and Base44 integration
contract generation used by locally-created projects.

Base44-generated electrical details are treated as requirements/proposals, not as
an authority that can bypass compiler validation.

## Handoff lifecycle

A handoff has a monotonically increasing revision and one of these states:

- `pending` — published by CircuitFlow, not yet received by Android.
- `delivered` — Android has received the revision and is processing it.
- `needs_input` — the deterministic resolver/compiler requires an additional
  user answer.
- `compiled` — design compilation and electrical validation completed.
- `failed` — the revision could not be compiled or safely validated.

Only the latest handoff revision is deliverable.

## Pairing

If the CircuitFlow project is already paired with an Android project, a new
handoff is delivered automatically during the next Bridge sync.

If it is not paired, publishing the handoff also creates a time-limited pairing
code. The Android home screen accepts this code under:

`Base44から設計を受け取る`

Android then:

1. pairs with the CircuitFlow project;
2. receives the latest Design Handoff;
3. creates a local Android project ID;
4. stores the Bridge credentials against that local project;
5. persists the imported draft;
6. runs the Project Compiler automatically.

## Handoff payload

The Bridge transfers:

- `revision`
- `status`
- `title`
- `goal_text`
- `spec_markdown`
- `spec_checksum`

`goal_text` contains the Base44 finalized specification plus an explicit
instruction that board selection, GPIO assignment, power topology, protection,
and electrical design must be revalidated by the Android Project Compiler.

## Android processing

For a new project:

`pair -> receive handoff -> create local project -> resolve -> compile -> ACK`

For an already paired project:

`Bridge sync -> new revision -> re-resolve -> recompile -> ACK`

When a new revision compiles successfully:

- the new ReleaseBundle replaces the previous generated design;
- physical deployment state is cleared;
- the existing physical device may continue its previously deployed autonomous
  firmware, but Android treats the new design as not yet deployed;
- the user must reconnect/redeploy before the device is considered to run the
  new revision.

This prevents a Base44 requirements edit from silently rewriting physical
hardware without a deployment boundary.

## Clarification recovery

If compilation requires more information, Android ACKs `needs_input` and shows
the normal plain-language clarification UI.

A `needs_input` handoff remains deliverable so an app restart can recover the
handoff. Existing clarification answers are preserved when the same revision is
resumed.

## Command safety during redesign

When a deliverable Design Handoff exists, the Base44 Bridge does not deliver
commands for the old Project Contract.

This prevents a queued command for the previous design from being applied while
Android is rebuilding the project.

Commands remain independently protected by the generated Project Contract
allow-list on both Base44 and Android.

## Base44 progress visibility

CircuitFlow polls Bridge status and displays the current handoff revision and
state without requiring a page refresh.

The ElectronicsProject also stores the latest summary fields:

- `handoff_revision`
- `handoff_status`
- `handoff_message`
- `handoff_pairing_code`
- `handoff_updated_at`

## Deployment boundary

V1 intentionally automates through **validated design generation**, not automatic
physical flashing.

Physical deployment changes a real device and therefore remains an explicit
Android operation after the hardware is connected and identified.

A future opt-in deployment policy may automate this only when the user has
explicitly enabled automatic deployment for that project/device.

## Offline operation

A successful handoff does not make the device dependent on CircuitFlow or
Android.

After deployment, projects default to `AUTONOMOUS_MCU` and the ESP32 runtime
restores the persisted manifest and settings from NVS at boot. Android/Base44
remain supervisory unless the requested core function explicitly requires an
external phone/cloud input.
