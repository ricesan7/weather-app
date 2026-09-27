#pragma once

#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace aie {

struct SettingConstraint {
    std::optional<double> min;
    std::optional<double> max;
    std::optional<double> step;
    std::vector<std::string> allowed;
    std::string relationalRule;
};

struct SettingSpec {
    std::string id;
    std::string type;
    std::string defaultValue;
    bool mutableAtRuntime = false;
    SettingConstraint constraint;
};

struct RuleSpec {
    std::string id;
    int priority = 0;
    std::string condition;
    std::vector<std::string> actions;
};

struct FailsafeSpec {
    std::string id;
    std::string condition;
    std::vector<std::string> actions;
};

struct InterlockSpec {
    std::string id;
    bool mandatory = true;
    std::string condition;
    std::vector<std::string> blockedActions;
};

struct TestSpec {
    std::string id;
    std::string command;
    bool required = false;
};

struct AutonomySpec {
    std::string coreOperationMode = "AUTONOMOUS_MCU";
    bool localBehaviorExecutionRequired = true;
    bool localSafetyExecutionRequired = true;
    bool persistRuntimeSettings = true;
    std::string externalInputReason;
};

struct DeviceSpec {
    std::string instanceId;
    std::string driverId;
    std::unordered_map<std::string, std::string> config;
};

struct Manifest {
    std::string version;
    std::string projectId;
    std::string boardId;
    std::string minimumRuntimeVersion;
    AutonomySpec autonomy;
    std::vector<std::string> drivers;
    std::vector<SettingSpec> settings;
    std::vector<RuleSpec> rules;
    std::vector<FailsafeSpec> failsafe;
    std::vector<InterlockSpec> interlocks;
    std::vector<TestSpec> tests;
    std::vector<DeviceSpec> devices;
    std::vector<std::string> telemetryIds;
};

class ManifestParser {
public:
    Manifest parse(const std::string& text) const;
};

} // namespace aie
