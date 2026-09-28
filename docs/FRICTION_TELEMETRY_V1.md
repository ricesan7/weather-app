# Friction telemetry v1

The beginner workflow now measures effort instead of describing it qualitatively.

## Golden baseline

Request:
"温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。"

Release budget:
- questions presented: 0
- technical choices presented: 0
- manual technical settings: 0
- screen transitions: 6
- workflow app switches: 0

Expected screen path:
HOME → DESIGN → PARTS → WIRING → BUILD → CONNECT → CONTROL

Two transitions are automatic:
- HOME → DESIGN after successful compilation;
- CONNECT → CONTROL after successful deploy/self-test.

Four transitions are user navigation.

## Physical work is not counted as technical friction

The current Golden wiring contains 10 deterministic GuidedBuild connections.
Each "接続済み" confirmation is counted separately as a physical assembly action.

Current Golden measurements:
- guided build confirmations: 10
- goal submissions: 1
- user-initiated screen transitions: 4
- device connect actions: 1
- deploy actions: 1
- essential user actions: 17
- avoidable technical actions: 0

This distinction prevents the metric from pretending that unavoidable soldering/wiring work has disappeared.

## Runtime instrumentation

BuilderAppViewModel records:
- goal submission;
- questions presented/answered;
- screen transitions;
- connect action;
- deploy action.

The Build screen records each completed wiring step.

Technical-choice and manual-technical-setting event types exist explicitly. The beginner UI currently emits neither; if a future change exposes them, the metric increases and the regression budget fails.

No external analytics service is required for v1. The recorder is local and deterministic; persistence/export can be added later.
