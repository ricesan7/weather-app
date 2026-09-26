# Runtime protocol + one-action deploy v1

This milestone implements the transport-independent logical protocol used by BLE, USB and Wi-Fi transports.

## Message types

- HELLO
- CAPABILITIES
- DEPLOY_MANIFEST
- DEPLOY_RESULT
- VERIFY_PROJECT
- VERIFY_RESULT
- START
- STOP
- TELEMETRY
- SET_VALUE
- GET_VALUE
- VALUE
- RUN_TEST
- TEST_RESULT
- LOG
- ERROR

RuntimeFrameCodec provides deterministic framing and escaping. Transport implementations only need to move RuntimeFrame payloads.

## One-action deploy

DefaultDeploymentPlanner decides whether the existing Universal Runtime is sufficient. The beginner does not choose a firmware mode.

DefaultDeployManager executes:
1. identify;
2. preflight;
3. install/update Runtime when needed;
4. deploy manifest;
5. verify project;
6. run required self-tests;
7. READY.

The normal MANIFEST_ONLY path requires no source editing.

## Runtime settings

DefaultRuntimeSettingsService changes values such as thresholds and modes through SET_VALUE/GET_VALUE.

Changing normal settings does not send DEPLOY_MANIFEST and therefore does not require recompiling or reflashing.

## Current limitation

Generated-firmware fallback is represented by the planner but intentionally returns a clear not-implemented result in DefaultDeployManager until the build/flash service exists.
