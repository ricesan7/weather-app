#pragma once

#include "aie/Expression.hpp"
#include "aie/Manifest.hpp"
#include "aie/Value.hpp"
#include <optional>
#include <set>
#include <string>
#include <unordered_map>
#include <vector>

namespace aie {

class RuntimeHardware {
public:
    virtual ~RuntimeHardware() = default;
    virtual bool setOutput(const std::string& outputId, const std::string& value) = 0;
    virtual bool runTest(const std::string& command) = 0;

    virtual std::optional<std::string> loadSetting(
        const std::string&,
        const std::string&
    ) {
        return std::nullopt;
    }

    virtual bool storeSetting(
        const std::string&,
        const std::string&,
        const std::string&
    ) {
        return false;
    }

    virtual std::optional<std::string> loadManifest() {
        return std::nullopt;
    }

    virtual bool storeManifest(const std::string&) {
        return false;
    }
};

struct RuntimeEvent {
    std::string id;
};

class RuntimeCore {
public:
    RuntimeCore(
        RuntimeHardware& hardware,
        std::set<std::string> supportedDrivers
    );

    bool deploy(const Manifest& manifest, std::string& error);
    bool persistManifest(
        const std::string& encodedManifest,
        std::string& error
    );
    bool restorePersistedManifest(std::string& error);
    bool verifyProject(const std::string& projectId) const;

    bool setSetting(
        const std::string& settingId,
        const std::string& value,
        std::string& error
    );
    std::optional<std::string> getSetting(const std::string& settingId) const;

    void updateInput(const std::string& id, const Value& value);
    void tick();

    bool runTest(const std::string& testId);
    std::unordered_map<std::string, Value> telemetry() const;

    const std::vector<RuntimeEvent>& events() const { return events_; }
    void clearEvents() { events_.clear(); }

private:
    bool validateSetting(
        const SettingSpec& spec,
        const std::string& candidate,
        std::string& error
    ) const;

    void executeActions(const std::vector<std::string>& actions);
    std::unordered_map<std::string, Value> context() const;
    const SettingSpec* settingSpec(const std::string& id) const;

    RuntimeHardware& hardware_;
    std::set<std::string> supportedDrivers_;
    std::optional<Manifest> manifest_;
    std::unordered_map<std::string, std::string> settings_;
    std::unordered_map<std::string, Value> inputs_;
    std::unordered_map<std::string, std::string> outputs_;
    std::vector<RuntimeEvent> events_;
    ExpressionEvaluator evaluator_;
};

} // namespace aie
