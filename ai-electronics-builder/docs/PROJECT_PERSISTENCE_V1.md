# Project persistence v1

Projects are stored locally on Android and can be resumed after the app is closed.

## What is persisted

- project ID and display title;
- original user goal text;
- answers to clarification questions;
- completed wiring connection IDs;
- current guided-build step index;
- last app screen;
- whether the project was previously deployed;
- created/updated timestamps.

## What is deliberately not persisted

Compiled electrical artifacts are not treated as durable truth:
- ReleaseBundle;
- CircuitGraph;
- DiagramSpec;
- ProjectManifest;
- validation results.

On resume, the app reloads the user's durable intent and answers, then runs the current deterministic compiler and safety validator again. Only build progress whose connection IDs still exist in the newly compiled CircuitGraph is restored.

This avoids reopening a stale design after component data, validation rules or compiler logic change.

## Android storage

project-storage-android uses SQLiteOpenHelper with an internal projects table. No cloud account is required.

## Resume behavior

1. Home lists saved projects newest first.
2. User taps 再開.
3. Goal + clarifications are loaded.
4. Current compiler/safety pipeline runs again.
5. Saved completed connection IDs are intersected with the new CircuitGraph.
6. GuidedBuildStateMachine resumes from the stored step.
7. BLE connections are never persisted; a previously deployed project returns to the connect screen before device control.

## UX

Saving is automatic. It is not counted as an additional beginner action in FrictionTelemetry.
