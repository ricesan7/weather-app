package jp.local.fax2840usb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintJobId;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Fax2840PrintService extends PrintService {
    private static final String LOCAL_ID = "brother-fax-2840-usb";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<PrintJobId, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

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
                try (Fax2840Usb usb = Fax2840Usb.open(Fax2840PrintService.this, d)) {
                    confirmed = usb.confirmsFax2840();
                } catch (IOException ignored) {}
                if (!confirmed) return;

                PrinterId id = generatePrinterId(LOCAL_ID);
                PrinterCapabilitiesInfo caps = new PrinterCapabilitiesInfo.Builder(id)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                        .addResolution(new PrintAttributes.Resolution("600dpi", "600 dpi", 600, 600), true)
                        .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build();

                PrinterInfo info = new PrinterInfo.Builder(id, "Brother FAX-2840 (USB)", PrinterInfo.STATUS_IDLE)
                        .setDescription("USB / Brother HBP experimental v1.0")
                        .setCapabilities(caps)
                        .build();
                addPrinters(Collections.singletonList(info));
            }
        };
    }

    @Override protected void onPrintJobQueued(PrintJob printJob) {
        PrintDiagnostics diag = new PrintDiagnostics(this, "ANDROID PRINT SERVICE");
        if (printJob == null) {
            diag.add("ERROR: null PrintJob");
            return;
        }
        diag.add("queued jobId=" + printJob.getId());
        if (printJob.isCancelled()) {
            diag.add("job already cancelled");
            return;
        }

        UsbDevice device = Fax2840Usb.findAttached(this);
        if (device == null) {
            diag.add("ERROR: FAX-2840 not attached");
            printJob.fail("Brother FAX-2840 がUSB接続されていません");
            return;
        }
        diag.add(String.format(java.util.Locale.US, "USB %04X:%04X", device.getVendorId(), device.getProductId()));

        UsbManager manager = (UsbManager)getSystemService(USB_SERVICE);
        if (manager == null || !manager.hasPermission(device)) {
            diag.add("ERROR: USB permission missing");
            printJob.fail("FAX-2840 のUSBアクセス権限がありません。アプリを開いて接続を許可してください");
            return;
        }

        ParcelFileDescriptor pdf = printJob.getDocument().getData();
        if (pdf == null) {
            diag.add("ERROR: document data unavailable");
            printJob.fail("印刷データを取得できませんでした");
            return;
        }
        diag.add("document acquired");

        int copies = Math.max(1, printJob.getInfo().getCopies());
        int density = PrintQualitySettings.getDensity(this);
        String documentName = printJob.getDocument().getInfo() == null
                ? ""
                : printJob.getDocument().getInfo().getName();
        boolean preparedPreview = documentName != null
                && documentName.startsWith(PreparedPdfPrintAdapter.DOCUMENT_MARKER);
        int splitMode = preparedPreview
                ? PageSplitSettings.MODE_FIT_PAGE
                : PageSplitSettings.getMode(this);
        diag.add("copies=" + copies);
        diag.add("density=" + density);
        diag.add("preparedPreview=" + preparedPreview);
        diag.add("splitMode=" + splitMode + " (" + PageSplitSettings.labelForMode(splitMode) + ")");
        PrintJobId jobId = printJob.getId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancellationFlags.put(jobId, cancelled);

        if (!printJob.start()) {
            cancellationFlags.remove(jobId);
            try { pdf.close(); } catch (IOException ignored) {}
            diag.add("ERROR: printJob.start() returned false");
            printJob.fail("印刷ジョブを開始できませんでした");
            return;
        }
        diag.add("PrintJob state=STARTED");

        new Thread(() -> runPrintJobWorker(printJob, jobId, pdf, copies, density, splitMode, device, cancelled, diag), "fax2840-print-job").start();
    }

    private void runPrintJobWorker(PrintJob printJob,
                                   PrintJobId jobId,
                                   ParcelFileDescriptor incomingPdf,
                                   int copies,
                                   int density,
                                   int splitMode,
                                   UsbDevice device,
                                   AtomicBoolean cancelled,
                                   PrintDiagnostics diag) {
        String failure = null;
        boolean wasCancelled = false;

        try (SeekablePdfSpool spool = SeekablePdfSpool.from(this, incomingPdf)) {
            diag.add("spool bytes=" + spool.getBytesCopied());
            diag.add("spool seekable PDF ready");

            try (Fax2840Usb usb = Fax2840Usb.open(this, device)) {
                diag.add("USB opened");
                diag.add("port(before)=" + Fax2840Usb.describePortStatus(usb.readPortStatus()));
                long before = usb.getBytesWritten();
                BrotherHbpPrinter.printPdf(spool.getSeekablePdf(), copies, usb, cancelled::get, diag::add, density, splitMode);
                long after = usb.getBytesWritten();
                diag.add("USB bytes delta=" + (after - before) + " total=" + after);
                diag.add("port(after)=" + Fax2840Usb.describePortStatus(usb.readPortStatus()));
                wasCancelled = cancelled.get();
            }
        } catch (CancellationException e) {
            wasCancelled = true;
            diag.add("RESULT=CANCELLED");
        } catch (IOException | RuntimeException e) {
            failure = "FAX-2840 印刷エラー: " + safeMessage(e);
            diag.add("RESULT=ERROR " + e.getClass().getSimpleName() + ": " + safeMessage(e));
        }

        final boolean finalCancelled = wasCancelled;
        final String finalFailure = failure;
        mainHandler.post(() -> {
            cancellationFlags.remove(jobId);
            if (finalCancelled) {
                printJob.cancel();
            } else if (finalFailure != null) {
                printJob.fail(finalFailure);
            } else {
                diag.add("RESULT=PRINTSERVICE_COMPLETE");
                printJob.complete();
            }
        });
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    @Override protected void onRequestCancelPrintJob(PrintJob printJob) {
        if (printJob == null) return;
        AtomicBoolean flag = cancellationFlags.get(printJob.getId());
        if (flag != null) flag.set(true);
        printJob.cancel();
    }
}
