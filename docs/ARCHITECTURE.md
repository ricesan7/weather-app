# Architecture

## Core rule

Design IR is authoritative. No feature may independently invent wiring, pins, or firmware configuration.

## Compiler stages

1. Intent parsing
2. Requirement resolution
3. Capability mapping
4. Component resolution
5. Power planning
6. Pin allocation
7. CircuitGraph compilation
8. Deterministic electrical validation
9. Behavior compilation
10. Design IR finalization
11. Assembly/DiagramSpec compilation
12. Runtime manifest compilation
13. Generated-firmware fallback when required
14. Generated mobile UI
15. Diagnostic plan
16. Cross-artifact consistency validation
17. Beginner/advanced mode projection

## Beginner mode

The beginner surface hides technical implementation choices. It does not reduce supported project complexity.

## Advanced mode

Advanced mode reveals editable source, manifest, pins, dependencies, build output, logs, and direct deployment controls.

## Safety

LLM output is never authoritative for electrical safety. Deterministic validation owns voltage, current, pin capability, power-domain, load-driver, protection, and conflict rules.

## Diagram rule

Generative images may illustrate the finished appearance, but authoritative wiring/assembly images are rendered deterministically from CircuitGraph + verified component assets and pin anchors.
