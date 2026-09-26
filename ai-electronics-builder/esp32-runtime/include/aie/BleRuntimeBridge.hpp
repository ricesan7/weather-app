#pragma once

#include "aie/BlePacket.hpp"
#include "aie/Protocol.hpp"

#include <cstddef>
#include <cstdint>
#include <vector>

namespace aie {

class BleRuntimeBridge {
public:
    explicit BleRuntimeBridge(
        ProtocolDispatcher& dispatcher,
        std::size_t defaultMaxPacketBytes = 20
    );

    std::vector<std::vector<std::uint8_t>> onPacket(
        const std::vector<std::uint8_t>& encodedPacket,
        std::size_t responseMaxPacketBytes = 0
    );

    void reset();

private:
    ProtocolDispatcher& dispatcher_;
    FrameCodec frameCodec_;
    BlePacketizer packetizer_;
    BleMessageAssembler assembler_;
    std::uint16_t nextResponseMessageId_ = 1;
    std::size_t defaultMaxPacketBytes_;
};

} // namespace aie
