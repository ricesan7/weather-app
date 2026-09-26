# System regression harness v1

The SystemRegressionTests specification is now executable.

## Contract

Every practical fixture must terminate in one of two acceptable states:

1. COMPILED — the real deterministic application compiler produced a ReleaseBundle.
2. UNSUPPORTED — the current verified product scope cannot support it, and the harness lists the exact missing feature and/or hardware families.

NEEDS_USER_INPUT and FAILED are regression failures for these fixed fixtures.

This prevents the application from silently inventing hardware support or collapsing into an ambiguous compiler error.

## Current coverage

Current verified scope compiles:
- reg_env_01 — environment monitoring + automatic ventilation.

Current scope explicitly reports unsupported for:
- irrigation;
- door alarm;
- motor positioning;
- multi-sensor logger;
- multi-output remote;
- heater fail-safe project;
- beginner-to-advanced code editing.

These unsupported results are expected at v1. They become actionable expansion targets.

## Shared application core

BeginnerIntentInterpreter and ApplicationProjectEngine live in the JVM application-core module. Android app and test-harness use the same implementation.

This prevents regression tests from testing a duplicate fake compiler path.

## Release rule

A future feature/catalog expansion must update CurrentRegressionSupport only after:
- verified engineering data exists;
- the actual compiler path supports the feature;
- the matching regression fixture compiles.

Do not mark a feature supported merely to make a test pass.
