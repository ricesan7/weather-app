# Compiler entrypoint v1

This milestone implements the first executable layer of the product architecture.

## RequirementResolver

`DefaultRequirementResolver` follows the zero-config rule:

- fill safe defaults automatically;
- ask only when a physical/safety requirement cannot be safely derived;
- never ask a beginner for GPIO, I2C/SPI selection, libraries, framework or build settings.

The resolver expects an upstream intent parser (LLM or deterministic parser) to provide structured facts such as:

- `has_actuator=true`
- `automation_required=true`
- `automation_rule=...`
- `logging_requested=true`
- `fixed_load_model=true`

The resolver remains deterministic once those facts are supplied.

## AutoDecisionEngine

`RuleBasedAutoDecisionEngine` consumes verified candidates from later DB-backed resolvers.

If beginner AUTO/AUTO_SAFE has no verified candidate, it returns BLOCKED. It deliberately does not convert missing engineering knowledge into a user question.

## ProjectCompiler

`DefaultProjectCompiler` now orchestrates:

capabilities -> components -> board -> power -> pins -> circuit -> electrical validation -> behavior -> DesignCore -> diagrams -> manifest -> UI -> diagnostics -> final Design IR

Any electrical BLOCK stops the pipeline before behavior/deployment artifacts are generated.

## DesignCore

`DesignCore` separates validated engineering truth from derived presentation/runtime artifacts. This avoids a circular dependency where Manifest/UI/Diagnostics would require a DesignIr that itself contains those artifacts.

## Tests

The project-compiler module includes tests for:

- safe default resolution;
- physical-environment clarification;
- missing-goal clarification;
- verified automatic technical decisions;
- no technical fallback question for beginner mode;
- advanced override;
- immediate stop on electrical BLOCK;
- successful ReleaseBundle assembly.
