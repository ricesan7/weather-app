# Offline Autonomy V1

## Principle

The generated device must not require Android, CircuitFlow/Base44, or cloud connectivity
for ordinary local operation unless the user's requested function inherently depends on
an external input such as a phone camera, phone GPS, phone microphone, or explicitly
required cloud AI.

The default operating mode is:

`AUTONOMOUS_MCU`

## What stays on the MCU

For autonomous projects, the MCU runtime owns:

- normal control rules;
- schedules and state transitions supported by the runtime;
- mandatory interlocks;
- failsafe evaluation;
- output actuation;
- runtime-mutable settings;
- the last deployed project manifest;
- persisted setting values.

Android and Base44 remain optional supervisory surfaces for:

- commissioning and deployment;
- configuration changes;
- telemetry and history forwarding;
- remote control;
- diagnostics;
- application UI and notifications.

Disconnecting Android or Base44 must not stop the local control loop.

## Reboot behavior

A successful deployment is not considered complete until the encoded manifest is
persisted on the MCU.

On ESP32 targets, the runtime stores:

- the latest deployed manifest;
- runtime setting values;

in NVS.

At boot the runtime attempts to restore the persisted manifest before Bluetooth starts.
Runtime settings are then restored by project ID and setting ID. Invalid persisted values
are rejected and the manifest default is used.

## Explicit external-input exception

If the requested core function explicitly depends on an external input, the compiler uses:

`EXTERNAL_INPUT_REQUIRED`

Examples:

- phone camera input is the actual machine sensor;
- phone GPS/location is required for the control decision;
- phone microphone is required for the control decision;
- phone motion sensors are required;
- cloud AI is explicitly stated to be mandatory for the decision.

Even in this mode:

- local safety execution remains required;
- failsafe/interlock logic remains local;
- runtime settings remain persisted;
- the external dependency and reason are exposed in the design.

A request that merely asks to display values on a phone, change settings from a phone,
view history, or receive notifications does **not** make the MCU dependent on the phone.

## Manifest compatibility

Offline autonomy semantics are part of manifest version 1.1.

Generated manifests require runtime version 0.2.0 or newer so an older runtime cannot
silently accept a project without persistent-manifest behavior.

## Runtime contract

RuntimeHardware must provide durable storage implementations for:

- `loadManifest()`;
- `storeManifest()`;
- `loadSetting(projectId, settingId)`;
- `storeSetting(projectId, settingId, value)`.

The ESP-IDF runtime uses NVS for these values.

A DEPLOY_MANIFEST command succeeds only after both parsing/deployment and durable
manifest storage succeed.

## Verification

Regression coverage must verify:

1. a local control rule can execute without BLE, Android, or Base44;
2. setting values survive RuntimeCore recreation;
3. the full encoded manifest survives RuntimeCore recreation;
4. restored runtime continues local control with the persisted setting values;
5. ordinary phone display/configuration projects remain AUTONOMOUS_MCU;
6. true phone-sensor dependency is explicitly EXTERNAL_INPUT_REQUIRED.
