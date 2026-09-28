#pragma once

#include "aie/Manifest.hpp"
#include "aie/RuntimeCore.hpp"
#include <string>
#include <unordered_map>

namespace aie {

enum class MessageType {
    HELLO,
    CAPABILITIES,
    DEPLOY_MANIFEST,
    DEPLOY_RESULT,
    VERIFY_PROJECT,
    VERIFY_RESULT,
    START,
    STOP,
    TELEMETRY,
    SET_VALUE,
    GET_VALUE,
    VALUE,
    RUN_TEST,
    TEST_RESULT,
    LOG,
    ERROR,
};

struct RuntimeFrame {
    int protocolVersion = 1;
    MessageType type = MessageType::ERROR;
    std::string requestId;
    std::unordered_map<std::string, std::string> fields;
};

class FrameCodec {
public:
    std::string encode(const RuntimeFrame& frame) const;
    RuntimeFrame decode(const std::string& text) const;
};

class ProtocolDispatcher {
public:
    ProtocolDispatcher(RuntimeCore& core, ManifestParser parser = ManifestParser());

    RuntimeFrame handle(const RuntimeFrame& request);

private:
    RuntimeFrame response(
        MessageType type,
        const RuntimeFrame& request,
        std::unordered_map<std::string, std::string> fields
    ) const;

    RuntimeCore& core_;
    ManifestParser parser_;
};

} // namespace aie
