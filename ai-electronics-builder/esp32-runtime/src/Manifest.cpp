#include "aie/Manifest.hpp"

#include <sstream>
#include <stdexcept>

namespace aie {
namespace {

std::string decodeField(std::string value) {
    struct Replacement {
        const char* encoded;
        const char* decoded;
    };
    const Replacement replacements[] = {
        {"%0D", "\r"},
        {"%0A", "\n"},
        {"%09", "\t"},
        {"%25", "%"},
    };

    for (const auto& replacement : replacements) {
        std::string::size_type pos = 0;
        while ((pos = value.find(replacement.encoded, pos)) != std::string::npos) {
            value.replace(pos, std::string(replacement.encoded).size(), replacement.decoded);
            pos += std::string(replacement.decoded).size();
        }
    }
    return value;
}

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

std::vector<std::string> decodedTabs(const std::string& line) {
    auto values = split(line, '\t');
    for (auto& value : values) value = decodeField(value);
    return values;
}

std::optional<double> optionalDouble(const std::string& value) {
    if (value.empty()) return std::nullopt;
    return std::stod(value);
}

bool boolValue(const std::string& value) {
    return value == "true" || value == "1";
}

std::unordered_map<std::string, std::string> keyValues(const std::string& value) {
    std::unordered_map<std::string, std::string> result;
    if (value.empty()) return result;

    for (const auto& token : split(value, ',')) {
        const auto pos = token.find(':');
        if (pos == std::string::npos) continue;
        result[token.substr(0, pos)] = token.substr(pos + 1);
    }
    return result;
}

} // namespace

Manifest ManifestParser::parse(const std::string& text) const {
    Manifest manifest;
    std::stringstream stream(text);
    std::string line;

    while (std::getline(stream, line)) {
        if (line.empty()) continue;
        const auto fields = decodedTabs(line);
        if (fields.empty()) continue;

        const auto& kind = fields[0];

        if (kind == "meta") {
            if (fields.size() < 3) throw std::runtime_error("Invalid meta line");
            if (fields[1] == "version") manifest.version = fields[2];
            else if (fields[1] == "project") manifest.projectId = fields[2];
            else if (fields[1] == "board") manifest.boardId = fields[2];
            else if (fields[1] == "runtime_min") manifest.minimumRuntimeVersion = fields[2];
        } else if (kind == "driver") {
            if (fields.size() < 2) throw std::runtime_error("Invalid driver line");
            manifest.drivers.push_back(fields[1]);
        } else if (kind == "device") {
            if (fields.size() < 4) throw std::runtime_error("Invalid device line");
            manifest.devices.push_back(DeviceSpec{
                fields[1],
                fields[2],
                keyValues(fields[3]),
            });
        } else if (kind == "setting") {
            if (fields.size() < 10) throw std::runtime_error("Invalid setting line");
            SettingSpec setting;
            setting.id = fields[1];
            setting.type = fields[2];
            setting.defaultValue = fields[3];
            setting.mutableAtRuntime = boolValue(fields[4]);
            setting.constraint.min = optionalDouble(fields[5]);
            setting.constraint.max = optionalDouble(fields[6]);
            setting.constraint.step = optionalDouble(fields[7]);
            if (!fields[8].empty()) setting.constraint.allowed = split(fields[8], ',');
            setting.constraint.relationalRule = fields[9];
            manifest.settings.push_back(std::move(setting));
        } else if (kind == "rule") {
            if (fields.size() < 5) throw std::runtime_error("Invalid rule line");
            manifest.rules.push_back(RuleSpec{
                fields[1],
                std::stoi(fields[2]),
                fields[3],
                split(fields[4], ';'),
            });
        } else if (kind == "failsafe") {
            if (fields.size() < 4) throw std::runtime_error("Invalid failsafe line");
            manifest.failsafe.push_back(FailsafeSpec{
                fields[1],
                fields[2],
                split(fields[3], ';'),
            });
        } else if (kind == "interlock") {
            if (fields.size() < 5) throw std::runtime_error("Invalid interlock line");
            manifest.interlocks.push_back(InterlockSpec{
                fields[1],
                boolValue(fields[2]),
                fields[3],
                split(fields[4], ','),
            });
        } else if (kind == "telemetry") {
            if (fields.size() < 2) throw std::runtime_error("Invalid telemetry line");
            manifest.telemetryIds.push_back(fields[1]);
        } else if (kind == "test") {
            if (fields.size() < 4) throw std::runtime_error("Invalid test line");
            manifest.tests.push_back(TestSpec{
                fields[1],
                fields[2],
                boolValue(fields[3]),
            });
        }
    }

    if (manifest.version.empty()) throw std::runtime_error("Manifest version missing");
    if (manifest.projectId.empty()) throw std::runtime_error("Project ID missing");
    if (manifest.boardId.empty()) throw std::runtime_error("Board ID missing");

    return manifest;
}

} // namespace aie
