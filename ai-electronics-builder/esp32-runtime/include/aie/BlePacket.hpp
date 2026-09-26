#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace aie {

struct BlePacket {
    std::uint16_t messageId = 0;
    std::uint16_t sequence = 0;
    bool start = false;
    bool end = false;
    std::vector<std::uint8_t> payload;
};

class BlePacketCodec {
public:
    static constexpr std::size_t HEADER_SIZE = 7;
    static constexpr std::uint8_t MAGIC = 0xA1;
    static constexpr std::uint8_t VERSION = 1;

    std::vector<std::uint8_t> encode(const BlePacket& packet) const;
    BlePacket decode(const std::vector<std::uint8_t>& bytes) const;
};

class BlePacketizer {
public:
    std::vector<std::vector<std::uint8_t>> split(
        std::uint16_t messageId,
        const std::vector<std::uint8_t>& payload,
        std::size_t maxPacketBytes
    ) const;

    std::vector<std::uint8_t> join(
        const std::vector<std::vector<std::uint8_t>>& packets
    ) const;
};

class BleMessageAssembler {
public:
    std::optional<std::vector<std::uint8_t>> accept(
        const std::vector<std::uint8_t>& encodedPacket
    );

    void reset();

private:
    BlePacketCodec codec_;
    bool active_ = false;
    std::uint16_t messageId_ = 0;
    std::uint16_t nextSequence_ = 0;
    std::vector<std::uint8_t> buffer_;
};

} // namespace aie
