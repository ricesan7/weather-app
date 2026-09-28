# Zero-config gate v1

The 14 release-blocking ZeroConfigUX rules are now executable against the real Golden beginner flow.

The gate compiles:
"温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。"

It then derives evidence from:
- resolved requirements and actual user questions;
- assigned CircuitGraph pins;
- Manifest runtime settings;
- deterministic GuidedBuildPlan;
- beginner error presentation;
- same-app journey contract;
- design explanation output.

The CI fails if any release-blocking metric misses its target.

## Metrics

Targets:
- manual_programming_actions = 0
- manual_pin_decisions = 0
- manual_library_choices = 0
- manual_build_config = 0
- technical_mode_choices = 0
- required_schematic_reading = 0
- rebuilds_for_normal_settings = 0
- nonessential_questions = 0
- simultaneous_build_decisions = 1
- beginner_raw_error_dependency = 0
- workflow_app_switches = 0
- beginner_role_categories <= 4
- required_domain_terms = 0
- explainability_available = true

## User-facing corrections

The gate also closes two concrete UX gaps:
1. beginner-facing errors are mapped to plain Japanese instead of exposing internal E_/W_ codes;
2. the design screen exposes concise reasons for automatic board/component/power/wiring choices.

Internal diagnostic codes remain available to the engineering/runtime layers; they are simply not required for beginner operation.
