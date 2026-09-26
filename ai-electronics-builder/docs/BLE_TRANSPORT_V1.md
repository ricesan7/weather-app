# BLE transport v1

The runtime protocol is transport-independent. This module provides the BLE packet/session layer without exposing BLE mechanics to beginner users.

## Implemented

- packet fragmentation for negotiated BLE payload size;
- deterministic binary packet header;
- ordered reassembly;
- automatic connection on first request;
- one reconnect/retry after link loss;
- RuntimeFrame request/response transport;
- requestId correlation;
- runtime protocol-version mismatch detection;
- HELLO / CAPABILITIES handshake.

Long manifest payloads are allowed to span many BLE packets.

## Packet shape

Each BLE application packet contains:
- magic byte;
- packet protocol version;
- START/END flags;
- 16-bit logical message ID;
- 16-bit sequence number;
- payload bytes.

The actual GATT adapter provides the maximum packet byte count after MTU negotiation.

## Beginner behavior

The user does not choose MTU, packet size, reconnect behavior, protocol version or upload mode. These are transport/runtime responsibilities.

## Remaining hardware boundary

Still required for Android real-device operation:
- Android BluetoothGatt scan/connect implementation;
- characteristic discovery;
- MTU request;
- write/notification transaction bridge implementing BlePacketIo;
- Android 12+ permission handling.

Those pieces are deliberately outside the portable transport core so the packet/protocol logic remains unit-testable.
