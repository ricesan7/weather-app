package com.aielectronics.ble

data class BlePacket(
    val messageId: Int,
    val sequence: Int,
    val start: Boolean,
    val end: Boolean,
    val payload: ByteArray,
) {
    init {
        require(messageId in 0..0xFFFF)
        require(sequence in 0..0xFFFF)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlePacket) return false
        return messageId == other.messageId &&
            sequence == other.sequence &&
            start == other.start &&
            end == other.end &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = messageId
        result = 31 * result + sequence
        result = 31 * result + start.hashCode()
        result = 31 * result + end.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object BlePacketCodec {
    const val HEADER_SIZE = 7
    private const val MAGIC = 0xA1
    private const val VERSION = 1
    private const val FLAG_START = 0x01
    private const val FLAG_END = 0x02

    fun encode(packet: BlePacket): ByteArray {
        val flags =
            (if (packet.start) FLAG_START else 0) or
                (if (packet.end) FLAG_END else 0)

        return ByteArray(HEADER_SIZE + packet.payload.size).also { out ->
            out[0] = MAGIC.toByte()
            out[1] = VERSION.toByte()
            out[2] = flags.toByte()
            out[3] = (packet.messageId ushr 8).toByte()
            out[4] = packet.messageId.toByte()
            out[5] = (packet.sequence ushr 8).toByte()
            out[6] = packet.sequence.toByte()
            packet.payload.copyInto(out, destinationOffset = HEADER_SIZE)
        }
    }

    fun decode(bytes: ByteArray): BlePacket {
        require(bytes.size >= HEADER_SIZE) { "BLE packet too short" }
        require(bytes[0].toInt() and 0xFF == MAGIC) { "BLE packet magic mismatch" }
        require(bytes[1].toInt() and 0xFF == VERSION) { "BLE packet version mismatch" }

        val flags = bytes[2].toInt() and 0xFF
        val messageId =
            ((bytes[3].toInt() and 0xFF) shl 8) or
                (bytes[4].toInt() and 0xFF)
        val sequence =
            ((bytes[5].toInt() and 0xFF) shl 8) or
                (bytes[6].toInt() and 0xFF)

        return BlePacket(
            messageId = messageId,
            sequence = sequence,
            start = flags and FLAG_START != 0,
            end = flags and FLAG_END != 0,
            payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
        )
    }
}

class BlePacketizer {

    fun split(
        messageId: Int,
        payload: ByteArray,
        maxPacketBytes: Int,
    ): List<ByteArray> {
        require(maxPacketBytes > BlePacketCodec.HEADER_SIZE) {
            "BLE packet size cannot fit payload"
        }

        val maxPayload = maxPacketBytes - BlePacketCodec.HEADER_SIZE
        if (payload.isEmpty()) {
            return listOf(
                BlePacketCodec.encode(
                    BlePacket(
                        messageId = messageId,
                        sequence = 0,
                        start = true,
                        end = true,
                        payload = byteArrayOf(),
                    )
                )
            )
        }

        return payload
            .asList()
            .chunked(maxPayload)
            .mapIndexed { index, chunk ->
                BlePacketCodec.encode(
                    BlePacket(
                        messageId = messageId,
                        sequence = index,
                        start = index == 0,
                        end = (index + 1) * maxPayload >= payload.size,
                        payload = chunk.toByteArray(),
                    )
                )
            }
    }

    fun join(packets: List<ByteArray>): ByteArray {
        require(packets.isNotEmpty()) { "BLE response has no packets" }

        val decoded = packets.map(BlePacketCodec::decode)
        val messageId = decoded.first().messageId

        require(decoded.all { it.messageId == messageId }) {
            "BLE message IDs are mixed"
        }
        require(decoded.first().start) { "BLE message start missing" }
        require(decoded.last().end) { "BLE message end missing" }

        decoded.forEachIndexed { index, packet ->
            require(packet.sequence == index) {
                "BLE packet sequence mismatch"
            }
        }

        val total = decoded.sumOf { it.payload.size }
        return ByteArray(total).also { result ->
            var offset = 0
            decoded.forEach { packet ->
                packet.payload.copyInto(result, destinationOffset = offset)
                offset += packet.payload.size
            }
        }
    }
}
