#include "aie/BlePacket.hpp"

#include <algorithm>
#include <stdexcept>

namespace aie {
namespace {

constexpr std::uint8_t FLAG_START = 0x01;
constexpr std::uint8_t FLAG_END = 0x02;

} // namespace

std::vector<std::uint8_t> BlePacketCodec::encode(const BlePacket& packet) const {
    std::vector<std::uint8_t> result;
    result.reserve(HEADER_SIZE + packet.payload.size());

    result.push_back(MAGIC);
    result.push_back(VERSION);

    std::uint8_t flags = 0;
    if (packet.start) flags |= FLAG_START;
    if (packet.end) flags |= FLAG_END;
    result.push_back(flags);

    result.push_back(static_cast<std::uint8_t>((packet.messageId >> 8) & 0xFF));
    result.push_back(static_cast<std::uint8_t>(packet.messageId & 0xFF));
    result.push_back(static_cast<std::uint8_t>((packet.sequence >> 8) & 0xFF));
    result.push_back(static_cast<std::uint8_t>(packet.sequence & 0xFF));

    result.insert(result.end(), packet.payload.begin(), packet.payload.end());
    return result;
}

BlePacket BlePacketCodec::decode(const std::vector<std::uint8_t>& bytes) const {
    if (bytes.size() < HEADER_SIZE) {
        throw std::runtime_error("BLE packet too short");
    }
    if (bytes[0] != MAGIC) {
        throw std::runtime_error("BLE packet magic mismatch");
    }
    if (bytes[1] != VERSION) {
        throw std::runtime_error("BLE packet version mismatch");
    }

    BlePacket packet;
    const auto flags = bytes[2];

    packet.start = (flags & FLAG_START) != 0;
    packet.end = (flags & FLAG_END) != 0;
    packet.messageId =
        static_cast<std::uint16_t>(
            (static_cast<std::uint16_t>(bytes[3]) << 8) |
            static_cast<std::uint16_t>(bytes[4])
        );
    packet.sequence =
        static_cast<std::uint16_t>(
            (static_cast<std::uint16_t>(bytes[5]) << 8) |
            static_cast<std::uint16_t>(bytes[6])
        );
    packet.payload.assign(bytes.begin() + HEADER_SIZE, bytes.end());

    return packet;
}

std::vector<std::vector<std::uint8_t>> BlePacketizer::split(
    std::uint16_t messageId,
    const std::vector<std::uint8_t>& payload,
    std::size_t maxPacketBytes
) const {
    if (maxPacketBytes <= BlePacketCodec::HEADER_SIZE) {
        throw std::runtime_error("BLE packet size cannot fit payload");
    }

    const auto maxPayload = maxPacketBytes - BlePacketCodec::HEADER_SIZE;
    BlePacketCodec codec;

    if (payload.empty()) {
        return {
            codec.encode(BlePacket{
                messageId,
                0,
                true,
                true,
                {}
            })
        };
    }

    std::vector<std::vector<std::uint8_t>> result;
    std::size_t offset = 0;
    std::uint16_t sequence = 0;

    while (offset < payload.size()) {
        const auto remaining = payload.size() - offset;
        const auto count = std::min(maxPayload, remaining);
        const bool isStart = offset == 0;
        const bool isEnd = offset + count >= payload.size();

        std::vector<std::uint8_t> chunk(
            payload.begin() + static_cast<std::ptrdiff_t>(offset),
            payload.begin() + static_cast<std::ptrdiff_t>(offset + count)
        );

        result.push_back(
            codec.encode(BlePacket{
                messageId,
                sequence,
                isStart,
                isEnd,
                std::move(chunk)
            })
        );

        offset += count;
        ++sequence;
    }

    return result;
}

std::vector<std::uint8_t> BlePacketizer::join(
    const std::vector<std::vector<std::uint8_t>>& packets
) const {
    if (packets.empty()) {
        throw std::runtime_error("BLE response has no packets");
    }

    BlePacketCodec codec;
    std::vector<BlePacket> decoded;
    decoded.reserve(packets.size());

    for (const auto& bytes : packets) {
        decoded.push_back(codec.decode(bytes));
    }

    const auto messageId = decoded.front().messageId;
    if (!decoded.front().start) {
        throw std::runtime_error("BLE message start missing");
    }
    if (!decoded.back().end) {
        throw std::runtime_error("BLE message end missing");
    }

    std::vector<std::uint8_t> result;

    for (std::size_t i = 0; i < decoded.size(); ++i) {
        const auto& packet = decoded[i];
        if (packet.messageId != messageId) {
            throw std::runtime_error("BLE message IDs are mixed");
        }
        if (packet.sequence != i) {
            throw std::runtime_error("BLE packet sequence mismatch");
        }
        result.insert(result.end(), packet.payload.begin(), packet.payload.end());
    }

    return result;
}

std::optional<std::vector<std::uint8_t>> BleMessageAssembler::accept(
    const std::vector<std::uint8_t>& encodedPacket
) {
    const auto packet = codec_.decode(encodedPacket);

    if (packet.start) {
        reset();
        active_ = true;
        messageId_ = packet.messageId;
        nextSequence_ = 0;
    }

    if (!active_) {
        throw std::runtime_error("BLE message start missing");
    }
    if (packet.messageId != messageId_) {
        reset();
        throw std::runtime_error("BLE message ID changed mid-stream");
    }
    if (packet.sequence != nextSequence_) {
        reset();
        throw std::runtime_error("BLE packet sequence mismatch");
    }

    buffer_.insert(buffer_.end(), packet.payload.begin(), packet.payload.end());
    ++nextSequence_;

    if (!packet.end) {
        return std::nullopt;
    }

    auto completed = buffer_;
    reset();
    return completed;
}

void BleMessageAssembler::reset() {
    active_ = false;
    messageId_ = 0;
    nextSequence_ = 0;
    buffer_.clear();
}

} // namespace aie
