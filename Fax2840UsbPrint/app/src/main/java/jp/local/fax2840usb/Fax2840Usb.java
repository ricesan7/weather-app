package jp.local.fax2840usb;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public final class Fax2840Usb implements Closeable {
    public static final int BROTHER_VENDOR_ID = 0x04F9;
    private static final int USB_CLASS_PRINTER = 7;
    private static final int CHUNK = 16 * 1024;
    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final UsbInterface printerInterface;
    private final UsbEndpoint bulkOut;
    private Fax2840Usb(UsbDevice device, UsbDeviceConnection connection, UsbInterface printerInterface, UsbEndpoint bulkOut) {
        this.device = device; this.connection = connection; this.printerInterface = printerInterface; this.bulkOut = bulkOut;
    }
    public static UsbDevice findAttached(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) return null;
        Collection<UsbDevice> devices = manager.getDeviceList().values();
        for (UsbDevice d : devices) if (isCandidate(d)) return d;
        return null;
    }
    public static boolean isCandidate(UsbDevice device) {
        return device != null && device.getVendorId() == BROTHER_VENDOR_ID && findPrinterInterface(device) != null;
    }
    public static Fax2840Usb open(Context context, UsbDevice device) throws IOException {
        if (!isCandidate(device)) throw new IOException("FAX-2840 USB device not found or unsupported USB interface");
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) throw new IOException("UsbManager unavailable");
        if (!manager.hasPermission(device)) throw new IOException("USB permission has not been granted");
        UsbInterface intf = findPrinterInterface(device);
        UsbEndpoint out = findBulkOut(intf);
        if (intf == null || out == null) throw new IOException("Printer bulk OUT endpoint not found");
        UsbDeviceConnection conn = manager.openDevice(device);
        if (conn == null) throw new IOException("Could not open USB device");
        if (!conn.claimInterface(intf, true)) { conn.close(); throw new IOException("Could not claim printer USB interface"); }
        return new Fax2840Usb(device, conn, intf, out);
    }
    private static UsbInterface findPrinterInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == USB_CLASS_PRINTER && findBulkOut(intf) != null) return intf;
        }
        return null;
    }
    private static UsbEndpoint findBulkOut(UsbInterface intf) {
        if (intf == null) return null;
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.getDirection() == UsbConstants.USB_DIR_OUT) return ep;
        }
        return null;
    }
    public String readIeee1284DeviceId() {
        byte[] buffer = new byte[2048];
        int interfaceNumber = printerInterface.getId() & 0xff;
        int alt = printerInterface.getAlternateSetting() & 0xff;
        int wIndexSpec = (interfaceNumber << 8) | alt;
        int n = connection.controlTransfer(0xA1, 0, 0, wIndexSpec, buffer, buffer.length, 1500);
        if (n < 3) n = connection.controlTransfer(0xA1, 0, 0, interfaceNumber, buffer, buffer.length, 1500);
        if (n < 3) return "";
        int declared = ((buffer[0] & 0xff) << 8) | (buffer[1] & 0xff);
        int end = Math.min(n, Math.max(2, declared));
        if (end <= 2) return "";
        return new String(buffer, 2, end - 2, StandardCharsets.US_ASCII).trim();
    }
    public boolean confirmsFax2840() {
        String id = readIeee1284DeviceId().toUpperCase();
        return !id.isEmpty() && (id.contains("MDL:FAX-2840") || id.contains("MDL:FAX 2840"));
    }
    public synchronized void write(byte[] data) throws IOException {
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(CHUNK, data.length - offset);
            int sent = connection.bulkTransfer(bulkOut, data, offset, len, 10000);
            if (sent <= 0) throw new IOException("USB bulk transfer failed at byte " + offset);
            offset += sent;
        }
    }
    public void writeAscii(String text) throws IOException { write(text.getBytes(StandardCharsets.US_ASCII)); }
    @Override public void close() {
        try { connection.releaseInterface(printerInterface); } catch (RuntimeException ignored) {}
        connection.close();
    }
}
