#include "aie/RuntimeCore.hpp"

#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace aie {
namespace {

std::vector<std::string> split(const std::string& value, char delimiter) {
    std::vector<std::string> result;
    std::string token;
    for (char c : value) {
        if (c == delimiter) {
            result.push_back(token);
            token.clear();
        } else {
            token += c;
        }
    }
    result.push_back(token);
    return result;
}

bool parseBool(const std::string& value, bool& parsed) {
    if (value == "true" || value == "TRUE" || value == "1" || value == "ON") {
        parsed = true;
        return true;
    }
    if (value == "false" || value == "FALSE" || value == "0" || value == "OFF") {
        parsed = false;
        return true;
    }
    return false;
}

} // namespace

RuntimeCore::RuntimeCore(
    RuntimeHardware& hardware,
    std::set<std::string> supportedDrivers
) : hardware_(hardware), supportedDrivers_(std::move(supportedDrivers)) {}

bool RuntimeCore::deploy(const Manifest& manifest, std::string& error) {
    for (const auto& driver : manifest.drivers) {
        if (supportedDrivers_.count(driver) == 0) {
            error = "Unsupported driver: " + driver;
            return false;
        }
    }

    for (const auto& setting : manifest.settings) {
        std::string validationError;
        if (!validateSetting(setting, setting.defaultValue, validationError)) {
            error = "Invalid default for " + setting.id + ": " + validationError;
            return false;
        }
    }

    manifest_ = manifest;
    settings_.clear();
    inputs_.clear();
    outputs_.clear();
    events_.clear();

    for (const auto& setting : manifest.settings) {
        std::string value = setting.defaultValue;

        if (manifest.autonomy.persistRuntimeSettings) {
            const auto persisted = hardware_.loadSetting(
                manifest.projectId,
                setting.id
            );
            if (persisted) {
                std::string validationError;
                if (validateSetting(setting, *persisted, validationError)) {
                    value = *persisted;
                } else {
                    events_.push_back({
                        "persisted_setting_invalid:" + setting.id
                    });
                }
            }
        }

        settings_[setting.id] = value;
    }

    return true;
}

bool RuntimeCore::verifyProject(const std::string& projectId) const {
    return manifest_.has_value() && manifest_->projectId == projectId;
}

bool RuntimeCore::setSetting(
    const std::string& settingId,
    const std::string& value,
    std::string& error
) {
    if (!manifest_) {
        error = "No project deployed";
        return false;
    }

    const auto* spec = settingSpec(settingId);
    if (!spec) {
        error = "Unknown setting";
        return false;
    }
    if (!spec->mutableAtRuntime) {
        error = "Setting is not runtime mutable";
        return false;
    }
    if (!validateSetting(*spec, value, error)) {
        return false;
    }

    auto trial = settings_;
    trial[settingId] = value;

    if (!spec->constraint.relationalRule.empty()) {
        auto ctx = context();
        for (const auto& entry : trial) {
            ctx[entry.first] = Value(entry.second);
        }
        try {
            if (!evaluator_.evaluate(spec->constraint.relationalRule, ctx)) {
                error = "Relational constraint rejected";
                return false;
            }
        } catch (const std::exception&) {
            error = "Relational constraint invalid";
            return false;
        }
    }

    if (
        manifest_->autonomy.persistRuntimeSettings &&
        !hardware_.storeSetting(
            manifest_->projectId,
            settingId,
            value
        )
    ) {
        error = "Failed to persist setting";
        return false;
    }

    settings_[settingId] = value;
    return true;
}

std::optional<std::string> RuntimeCore::getSetting(const std::string& settingId) const {
    const auto it = settings_.find(settingId);
    if (it == settings_.end()) return std::nullopt;
    return it->second;
}

void RuntimeCore::updateInput(const std::string& id, const Value& value) {
    inputs_[id] = value;
}

void RuntimeCore::tick() {
    if (!manifest_) return;

    const auto ctx = context();

    for (const auto& failsafe : manifest_->failsafe) {
        try {
            if (evaluator_.evaluate(failsafe.condition, ctx)) {
                executeActions(failsafe.actions);
                return;
            }
        } catch (const std::exception&) {
            events_.push_back({"runtime_expression_error"});
            return;
        }
    }

    std::vector<RuleSpec> rules = manifest_->rules;
    std::sort(
        rules.begin(),
        rules.end(),
        [](const RuleSpec& a, const RuleSpec& b) {
            if (a.priority != b.priority) return a.priority > b.priority;
            return a.id < b.id;
        }
    );

    for (const auto& rule : rules) {
        try {
            if (evaluator_.evaluate(rule.condition, ctx)) {
                executeActions(rule.actions);
            }
        } catch (const std::exception&) {
            events_.push_back({"runtime_expression_error"});
            return;
        }
    }
}

bool RuntimeCore::runTest(const std::string& testId) {
    if (!manifest_) return false;
    const auto it = std::find_if(
        manifest_->tests.begin(),
        manifest_->tests.end(),
        [&](const TestSpec& test) { return test.id == testId; }
    );
    if (it == manifest_->tests.end()) return false;
    return hardware_.runTest(it->command);
}

std::unordered_map<std::string, Value> RuntimeCore::telemetry() const {
    std::unordered_map<std::string, Value> result;
    if (!manifest_) return result;

    for (const auto& id : manifest_->telemetryIds) {
        if (const auto input = inputs_.find(id); input != inputs_.end()) {
            result[id] = input->second;
            continue;
        }

        if (const auto output = outputs_.find(id); output != outputs_.end()) {
            result[id] = Value(output->second);
            continue;
        }

        const std::string suffix = "_state";
        if (id.size() > suffix.size() &&
            id.compare(id.size() - suffix.size(), suffix.size(), suffix) == 0) {
            const auto base = id.substr(0, id.size() - suffix.size());
            if (const auto output = outputs_.find(base); output != outputs_.end()) {
                result[id] = Value(output->second);
            }
        }
    }
    return result;
}

bool RuntimeCore::validateSetting(
    const SettingSpec& spec,
    const std::string& candidate,
    std::string& error
) const {
    if (spec.type == "NUMBER" || spec.type == "DURATION") {
        Value value(candidate);
        const auto number = value.asNumber();
        if (!number) {
            error = "Expected number";
            return false;
        }
        if (spec.constraint.min && *number < *spec.constraint.min) {
            error = "Below minimum";
            return false;
        }
        if (spec.constraint.max && *number > *spec.constraint.max) {
            error = "Above maximum";
            return false;
        }
        if (spec.constraint.step && spec.constraint.min) {
            const double steps = (*number - *spec.constraint.min) / *spec.constraint.step;
            if (std::fabs(steps - std::round(steps)) > 1e-7) {
                error = "Invalid step";
                return false;
            }
        }
    } else if (spec.type == "BOOLEAN") {
        bool parsed = false;
        if (!parseBool(candidate, parsed)) {
            error = "Expected boolean";
            return false;
        }
    } else if (spec.type == "ENUM") {
        if (!spec.constraint.allowed.empty() &&
            std::find(spec.constraint.allowed.begin(), spec.constraint.allowed.end(), candidate) ==
                spec.constraint.allowed.end()) {
            error = "Value not allowed";
            return false;
        }
    }

    return true;
}

void RuntimeCore::executeActions(const std::vector<std::string>& actions) {
    for (const auto& action : actions) {
        const auto parts = split(action, ':');
        if (parts.empty()) continue;

        if (parts[0] == "set" && parts.size() >= 3) {
            const auto& outputId = parts[1];
            const auto& value = parts[2];

            bool blocked = false;
            if (manifest_) {
                const auto ctx = context();
                for (const auto& interlock : manifest_->interlocks) {
                    try {
                        if (evaluator_.evaluate(interlock.condition, ctx)) {
                            if (interlock.blockedActions.empty() ||
                                std::find(
                                    interlock.blockedActions.begin(),
                                    interlock.blockedActions.end(),
                                    outputId
                                ) != interlock.blockedActions.end() ||
                                std::find(
                                    interlock.blockedActions.begin(),
                                    interlock.blockedActions.end(),
                                    action
                                ) != interlock.blockedActions.end()) {
                                blocked = true;
                                if (interlock.mandatory) {
                                    events_.push_back({"interlock:" + interlock.id});
                                }
                            }
                        }
                    } catch (const std::exception&) {
                        blocked = true;
                        events_.push_back({"runtime_expression_error"});
                    }
                }
            }

            if (!blocked && hardware_.setOutput(outputId, value)) {
                outputs_[outputId] = value;
            }
        } else if (parts[0] == "event" && parts.size() >= 2) {
            events_.push_back({parts[1]});
        } else if (parts[0] == "state" && parts.size() >= 2) {
            inputs_["state"] = Value(parts[1]);
        }
    }
}

std::unordered_map<std::string, Value> RuntimeCore::context() const {
    std::unordered_map<std::string, Value> ctx = inputs_;

    for (const auto& entry : settings_) {
        const auto* spec = settingSpec(entry.first);
        if (spec && (spec->type == "NUMBER" || spec->type == "DURATION")) {
            Value raw(entry.second);
            const auto number = raw.asNumber();
            ctx[entry.first] = number ? Value(*number) : Value(entry.second);
        } else if (spec && spec->type == "BOOLEAN") {
            bool parsed = false;
            parseBool(entry.second, parsed);
            ctx[entry.first] = Value(parsed);
        } else {
            ctx[entry.first] = Value(entry.second);
        }
    }

    for (const auto& entry : outputs_) {
        ctx[entry.first] = Value(entry.second);
        ctx[entry.first + "_state"] = Value(entry.second);
    }

    return ctx;
}

const SettingSpec* RuntimeCore::settingSpec(const std::string& id) const {
    if (!manifest_) return nullptr;
    const auto it = std::find_if(
        manifest_->settings.begin(),
        manifest_->settings.end(),
        [&](const SettingSpec& setting) { return setting.id == id; }
    );
    return it == manifest_->settings.end() ? nullptr : &(*it);
}

} // namespace aie
