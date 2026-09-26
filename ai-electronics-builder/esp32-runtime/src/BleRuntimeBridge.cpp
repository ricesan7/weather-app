#include "aie/BleRuntimeBridge.hpp"

#include <algorithm>
#include <stdexcept>
#include <string>

namespace aie {

BleRuntimeBridge::BleRuntimeBridge(
    ProtocolDispatcher& dispatcher,
    std::size_t defaultMaxPacketBytes
) : dispatcher_(dispatcher),
    defaultMaxPacketBytes_(defaultMaxPacketBytes) {
    if (defaultMaxPacketBytes_ <= BlePacketCodec::HEADER_SIZE) {
        throw std::runtime_error("Invalid default BLE packet size");
    }
}

std::vector<std::vector<std::uint8_t>> BleRuntimeBridge::onPacket(
    const std::vector<std::uint8_t>& encodedPacket,
    std::size_t responseMaxPacketBytes
) {
    const auto message = assembler_.accept(encodedPacket);
    if (!message) {
        return {};
    }

    const std::string requestText(message->begin(), message->end());
    const auto request = frameCodec_.decode(requestText);
    const auto response = dispatcher_.handle(request);
    const auto responseText = frameCodec_.encode(response);

    std::vector<std::uint8_t> responseBytes(
        responseText.begin(),
        responseText.end()
    );

    const auto packetBytes =
        responseMaxPacketBytes > BlePacketCodec::HEADER_SIZE
            ? responseMaxPacketBytes
            : defaultMaxPacketBytes_;

    const auto responseId = nextResponseMessageId_;
    nextResponseMessageId_ =
        nextResponseMessageId_ == 0xFFFF
            ? 1
            : static_cast<std::uint16_t>(nextResponseMessageId_ + 1);

    return packetizer_.split(
        responseId,
        responseBytes,
        packetBytes
    );
}

void BleRuntimeBridge::reset() {
    assembler_.reset();
}

} // namespace aie
