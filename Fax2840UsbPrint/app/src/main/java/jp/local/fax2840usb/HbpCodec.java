package jp.local.fax2840usb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class HbpCodec {
    private static final int MAX_BLOCK_BYTES = 16_350;
    private static final int MAX_LINES_PER_BAND = 64;
    private HbpCodec() {}
    static byte[] encodeAbsoluteLine(byte[] line) {
        boolean blank = true;
        for (byte b : line) { if (b != 0) { blank = false; break; } }
        if (blank) return new byte[]{(byte)0xFF};
        ByteArrayOutputStream out = new ByteArrayOutputStream(line.length + 16);
        out.write(1);
        writeSubstitute(out, 0, line);
        return out.toByteArray();
    }
    private static void writeSubstitute(ByteArrayOutputStream out, int offset, byte[] bytes) {
        int count = bytes.length - 1;
        int offsetLow = Math.min(offset, 15);
        int countLow = Math.min(count, 7);
        out.write((offsetLow << 3) | countLow);
        writeOverflow(out, offset - 15);
        writeOverflow(out, count - 7);
        out.write(bytes, 0, bytes.length);
    }
    private static void writeOverflow(ByteArrayOutputStream out, int value) {
        if (value < 0) return;
        if (value < 255) { out.write(value); return; }
        int full = value / 255;
        for (int i = 0; i < full; i++) out.write(255);
        out.write(value % 255);
    }
    static final class BlockWriter {
        private final Fax2840Usb transport;
        private final List<byte[]> lines = new ArrayList<>();
        private int bytes;
        BlockWriter(Fax2840Usb transport) { this.transport = transport; }
        void addLine(byte[] encoded) throws IOException {
            if (encoded.length == 0) throw new IOException("Empty HBP raster line");
            if (!lines.isEmpty() && (lines.size() >= MAX_LINES_PER_BAND || bytes + encoded.length >= MAX_BLOCK_BYTES)) flush();
            if (encoded.length >= MAX_BLOCK_BYTES) throw new IOException("Encoded HBP line exceeds block limit: " + encoded.length);
            lines.add(encoded); bytes += encoded.length;
        }
        void flush() throws IOException {
            if (lines.isEmpty()) return;
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes + 32);
            byte[] length = Integer.toString(bytes + 2).getBytes(StandardCharsets.US_ASCII);
            out.write(length, 0, length.length); out.write('w'); out.write(0); out.write(lines.size() & 0xff);
            for (byte[] line : lines) out.write(line, 0, line.length);
            transport.write(out.toByteArray()); lines.clear(); bytes = 0;
        }
    }
}
