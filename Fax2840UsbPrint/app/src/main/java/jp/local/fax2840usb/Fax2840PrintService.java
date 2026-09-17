package jp.local.fax2840usb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class Fax2840PrintService extends PrintService {
    private static final String LOCAL_ID = "brother-fax-2840-usb";
    @Override protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new PrinterDiscoverySession() {
            @Override public void onStartPrinterDiscovery(List<PrinterId> priorityList) { publishIfPresent(); }
            @Override public void onStopPrinterDiscovery() {}
            @Override public void onValidatePrinters(List<PrinterId> printerIds) { publishIfPresent(); }
            @Override public void onStartPrinterStateTracking(PrinterId printerId) { publishIfPresent(); }
            @Override public void onStopPrinterStateTracking(PrinterId printerId) {}
            @Override public void onDestroy() {}
            private void publishIfPresent() {
                UsbDevice d = Fax2840Usb.findAttached(Fax2840PrintService.this);
                if (d == null) return;
                UsbManager um = (UsbManager)getSystemService(USB_SERVICE);
                if (um == null || !um.hasPermission(d)) return;
                boolean confirmed = false;
                try (Fax2840Usb usb = Fax2840Usb.open(Fax2840PrintService.this, d)) { confirmed = usb.confirmsFax2840(); } catch (IOException ignored) {}
                if (!confirmed) return;
                PrinterId id = generatePrinterId(LOCAL_ID);
                PrinterCapabilitiesInfo caps = new PrinterCapabilitiesInfo.Builder(id)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                        .addResolution(new PrintAttributes.Resolution("600dpi", "600 dpi", 600, 600), true)
                        .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();
                PrinterInfo info = new PrinterInfo.Builder(id, "Brother FAX-2840 (USB)", PrinterInfo.STATUS_IDLE)
                        .setDescription("USB / Brother HBP experimental").setCapabilities(caps).build();
                addPrinters(Collections.singletonList(info));
            }
        };
    }
    @Override protected void onPrintJobQueued(PrintJob printJob) { new Thread(() -> runPrintJob(printJob), "fax2840-print-job").start(); }
    private void runPrintJob(PrintJob printJob) {
        if (printJob == null || printJob.isCancelled()) return;
        UsbDevice device = Fax2840Usb.findAttached(this);
        if (device == null) { printJob.fail("Brother FAX-2840 がUSB接続されていません"); return; }
        UsbManager manager = (UsbManager)getSystemService(USB_SERVICE);
        if (manager == null || !manager.hasPermission(device)) { printJob.fail("FAX-2840 のUSBアクセス権限がありません。アプリを開いて接続を許可してください"); return; }
        ParcelFileDescriptor pdf = printJob.getDocument().getData();
        if (pdf == null) { printJob.fail("印刷データを取得できませんでした"); return; }
        int copies = Math.max(1, printJob.getInfo().getCopies());
        try (Fax2840Usb usb = Fax2840Usb.open(this, device)) {
            printJob.start();
            BrotherHbpPrinter.printPdf(pdf, copies, usb, printJob::isCancelled);
            if (printJob.isCancelled()) printJob.cancel(); else printJob.complete();
        } catch (CancellationException e) { printJob.cancel(); }
        catch (IOException | RuntimeException e) { printJob.fail("FAX-2840 印刷エラー: " + safeMessage(e)); }
    }
    private static String safeMessage(Throwable t) { String m=t.getMessage(); return (m==null||m.trim().isEmpty())?t.getClass().getSimpleName():m; }
    @Override protected void onRequestCancelPrintJob(PrintJob printJob) { printJob.cancel(); }
}
