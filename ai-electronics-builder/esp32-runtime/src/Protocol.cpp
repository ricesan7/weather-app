#include "aie/Protocol.hpp"

#include <algorithm>
#include <sstream>
#include <stdexcept>
#include <vector>

namespace aie {
namespace {

std::string replaceAll(std::string value, const std::string& from, const std::string& to) {
    std::string::size_type pos = 0;
    while ((pos = value.find(from, pos)) != std::string::npos) {
        value.replace(pos, from.size(), to);
        pos += to.size();
    }
    return value;
}

std::string escape(std::string value) {
    value = replaceAll(value, "%", "%25");
    value = replaceAll(value, "|", "%7C");
    value = replaceAll(value, "=", "%3D");
    value = replaceAll(value, "\n", "%0A");
    value = replaceAll(value, "\r", "%0D");
    return value;
}

std::string unescape(std::string value) {
    value = replaceAll(value, "%0D", "\r");
    value = replaceAll(value, "%0A", "\n");
    value = replaceAll(value, "%3D", "=");
    value = replaceAll(value, "%7C", "|");
    value = replaceAll(value, "%25", "%");
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

std::string typeName(MessageType type) {
    switch (type) {
        case MessageType::HELLO: return "HELLO";
        case MessageType::CAPABILITIES: return "CAPABILITIES";
        case MessageType::DEPLOY_MANIFEST: return "DEPLOY_MANIFEST";
        case MessageType::DEPLOY_RESULT: return "DEPLOY_RESULT";
        case MessageType::VERIFY_PROJECT: return "VERIFY_PROJECT";
        case MessageType::VERIFY_RESULT: return "VERIFY_RESULT";
        case MessageType::START: return "START";
        case MessageType::STOP: return "STOP";
        case MessageType::TELEMETRY: return "TELEMETRY";
        case MessageType::SET_VALUE: return "SET_VALUE";
        case MessageType::GET_VALUE: return "GET_VALUE";
        case MessageType::VALUE: return "VALUE";
        case MessageType::RUN_TEST: return "RUN_TEST";
        case MessageType::TEST_RESULT: return "TEST_RESULT";
        case MessageType::LOG: return "LOG";
        case MessageType::ERROR: return "ERROR";
    }
    return "ERROR";
}

MessageType parseType(const std::string& name) {
    if (name == "HELLO") return MessageType::HELLO;
    if (name == "CAPABILITIES") return MessageType::CAPABILITIES;
    if (name == "DEPLOY_MANIFEST") return MessageType::DEPLOY_MANIFEST;
    if (name == "DEPLOY_RESULT") return MessageType::DEPLOY_RESULT;
    if (name == "VERIFY_PROJECT") return MessageType::VERIFY_PROJECT;
    if (name == "VERIFY_RESULT") return MessageType::VERIFY_RESULT;
    if (name == "START") return MessageType::START;
    if (name == "STOP") return MessageType::STOP;
    if (name == "TELEMETRY") return MessageType::TELEMETRY;
    if (name == "SET_VALUE") return MessageType::SET_VALUE;
    if (name == "GET_VALUE") return MessageType::GET_VALUE;
    if (name == "VALUE") return MessageType::VALUE;
    if (name == "RUN_TEST") return MessageType::RUN_TEST;
    if (name == "TEST_RESULT") return MessageType::TEST_RESULT;
    if (name == "LOG") return MessageType::LOG;
    if (name == "ERROR") return MessageType::ERROR;
    throw std::runtime_error("Unknown message type");
}

} // namespace

std::string FrameCodec::encode(const RuntimeFrame& frame) const {
    std::ostringstream out;
    out << frame.protocolVersion << "|" << typeName(frame.type) << "|" << escape(frame.requestId);

    std::vector<std::pair<std::string, std::string>> fields(
        frame.fields.begin(),
        frame.fields.end()
    );
    std::sort(fields.begin(), fields.end());

    for (const auto& field : fields) {
        out << "|" << escape(field.first) << "=" << escape(field.second);
    }
    return out.str();
}

RuntimeFrame FrameCodec::decode(const std::string& text) const {
    const auto parts = split(text, '|');
    if (parts.size() < 3) throw std::runtime_error("Invalid runtime frame");

    RuntimeFrame frame;
    frame.protocolVersion = std::stoi(parts[0]);
    frame.type = parseType(parts[1]);
    frame.requestId = unescape(parts[2]);

    for (std::size_t i = 3; i < parts.size(); ++i) {
        const auto pos = parts[i].find('=');
        if (pos == std::string::npos || pos == 0) {
            throw std::runtime_error("Invalid runtime field");
        }
        frame.fields[unescape(parts[i].substr(0, pos))] =
            unescape(parts[i].substr(pos + 1));
    }

    return frame;
}

ProtocolDispatcher::ProtocolDispatcher(RuntimeCore& core, ManifestParser parser)
    : core_(core), parser_(std::move(parser)) {}

RuntimeFrame ProtocolDispatcher::handle(const RuntimeFrame& request) {
    try {
        if (request.type == MessageType::HELLO) {
            return response(
                MessageType::CAPABILITIES,
                request,
                {
                    {"protocol_version", "1"},
                    {"manifest", "true"},
                    {"settings", "true"},
                    {"self_test", "true"},
                    {"telemetry", "true"},
                }
            );
        }

        if (request.type == MessageType::DEPLOY_MANIFEST) {
            const auto payload = request.fields.at("payload");
            const auto manifest = parser_.parse(payload);
            std::string error;
            bool ok = core_.deploy(manifest, error);
            if (ok) {
                ok = core_.persistManifest(payload, error);
            }
            return response(
                MessageType::DEPLOY_RESULT,
                request,
                {
                    {"ok", ok ? "true" : "false"},
                    {"message", error},
                }
            );
        }

        if (request.type == MessageType::VERIFY_PROJECT) {
            const bool ok = core_.verifyProject(request.fields.at("project_id"));
            return response(
                MessageType::VERIFY_RESULT,
                request,
                {{"ok", ok ? "true" : "false"}}
            );
        }

        if (request.type == MessageType::SET_VALUE) {
            const auto id = request.fields.at("setting_id");
            const auto value = request.fields.at("value");
            std::string error;
            const bool ok = core_.setSetting(id, value, error);
            if (!ok) {
                return response(
                    MessageType::ERROR,
                    request,
                    {
                        {"code", "E_SETTING_REJECTED"},
                        {"message", error},
                    }
                );
            }
            return response(
                MessageType::VALUE,
                request,
                {
                    {"setting_id", id},
                    {"value", core_.getSetting(id).value_or("")},
                    {"persisted", "true"},
                }
            );
        }

        if (request.type == MessageType::GET_VALUE) {
            const auto id = request.fields.at("setting_id");
            const auto value = core_.getSetting(id);
            if (!value) {
                return response(
                    MessageType::ERROR,
                    request,
                    {
                        {"code", "E_SETTING_UNKNOWN"},
                        {"message", "Unknown setting"},
                    }
                );
            }
            return response(
                MessageType::VALUE,
                request,
                {
                    {"setting_id", id},
                    {"value", *value},
                }
            );
        }

        if (request.type == MessageType::RUN_TEST) {
            const auto id = request.fields.at("test_id");
            const bool passed = core_.runTest(id);
            return response(
                MessageType::TEST_RESULT,
                request,
                {
                    {"test_id", id},
                    {"passed", passed ? "true" : "false"},
                }
            );
        }

        if (request.type == MessageType::TELEMETRY) {
            std::unordered_map<std::string, std::string> fields;
            for (const auto& entry : core_.telemetry()) {
                fields[entry.first] = entry.second.asString();
            }
            return response(MessageType::TELEMETRY, request, std::move(fields));
        }

        return response(
            MessageType::ERROR,
            request,
            {
                {"code", "E_UNSUPPORTED_MESSAGE"},
                {"message", "Unsupported message"},
            }
        );
    } catch (const std::exception& e) {
        return response(
            MessageType::ERROR,
            request,
            {
                {"code", "E_PROTOCOL"},
                {"message", e.what()},
            }
        );
    }
}

RuntimeFrame ProtocolDispatcher::response(
    MessageType type,
    const RuntimeFrame& request,
    std::unordered_map<std::string, std::string> fields
) const {
    RuntimeFrame result;
    result.protocolVersion = request.protocolVersion;
    result.type = type;
    result.requestId = request.requestId;
    result.fields = std::move(fields);
    return result;
}

} // namespace aie
