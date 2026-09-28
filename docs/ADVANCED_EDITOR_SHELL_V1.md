# Advanced editor shell v1

Advanced mode is an explicit opt-in workspace. Beginner mode remains code-free.

## Implemented

- generated project file tree;
- editable C++ source baseline;
- editable generated project header;
- editable Runtime Manifest text;
- read-only authoritative hardware netlist;
- protected generated baseline and separate working copy;
- C/C++ syntax coloring;
- text editing;
- search / replace-next / replace-all;
- line-oriented generated-vs-working diff;
- restore selected file;
- restore entire project to the validated generated baseline;
- validation/build log;
- local workspace validation;
- build-service abstraction.

## Build honesty

The default build service returns ServiceUnavailable. It never fabricates a firmware binary.

This shell is therefore safe to expose now:
- editing/diff/restore are real;
- local structural validation is real;
- firmware compilation remains explicitly unavailable until a real build service is connected.

## Safety boundary

Changing C++ source, Manifest, pin data or hardware references must not bypass the deterministic electrical validator.

hardware/netlist.txt remains read-only in this version. A future pin/Manifest structured editor must regenerate Design IR / CircuitGraph and rerun ElectricalValidator before deployment.

## Beginner/advanced separation

Beginner:
- no code;
- no GPIO/library/build-mode choices;
- generated image-first wiring and guided build.

Advanced:
- explicit "上級者モードでコードを見る" action;
- code/Manifest/file tree/diff/log access;
- immutable path back to the validated generated baseline.
