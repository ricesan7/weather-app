package jp.local.fax2840usb;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.util.concurrent.CancellationException;

final class BrotherHbpPrinter {
    interface CancelCheck { boolean isCancelled(); }
    interface DiagnosticSink { void onEvent(String message); }

    private static final int A4_WIDTH_PX = 4960;
    private static final int A4_HEIGHT_PX = 7016;

    // Brother FAX-2840 PPD: A4 ImageableArea = 12 12 583 830 points.
    // 12pt at 600dpi = exactly 100 pixels on every edge.
    private static final int PRINTABLE_MARGIN_PX = 100;
    private static final int PRINTABLE_WIDTH_PX = A4_WIDTH_PX - (PRINTABLE_MARGIN_PX * 2);
    private static final int PRINTABLE_HEIGHT_PX = A4_HEIGHT_PX - (PRINTABLE_MARGIN_PX * 2);

    private static final int STRIPE_HEIGHT = 64;
    private static final double MIDTONE_GAMMA = 0.78;

    // 8x8 Bayer ordered-dither matrix, values 0..63.
    private static final int[][] BAYER_8X8 = {
            { 0,48,12,60, 3,51,15,63},
            {32,16,44,28,35,19,47,31},
            { 8,56, 4,52,11,59, 7,55},
            {40,24,36,20,43,27,39,23},
            { 2,50,14,62, 1,49,13,61},
            {34,18,46,30,33,17,45,29},
            {10,58, 6,54, 9,57, 5,53},
            {42,26,38,22,41,25,37,21}
    };

    private BrotherHbpPrinter() {}

    static void printPdf(ParcelFileDescriptor pdfFd, int copies, Fax2840Usb transport, CancelCheck cancelled) throws IOException {
        printPdf(pdfFd, copies, transport, cancelled, null);
    }

    static void printPdf(ParcelFileDescriptor pdfFd, int copies, Fax2840Usb transport, CancelCheck cancelled, DiagnosticSink sink) throws IOException {
        printPdf(pdfFd, copies, transport, cancelled, sink, PrintQualitySettings.DEFAULT_DENSITY);
    }

    static void printPdf(ParcelFileDescriptor pdfFd, int copies, Fax2840Usb transport, CancelCheck cancelled, DiagnosticSink sink, int density) throws IOException {
        copies = Math.max(1, copies);
        density = clampDensity(density);
        event(sink, "HBP: verify printer");
        if (!transport.confirmsFax2840()) throw new IOException("Connected USB device did not identify as Brother FAX-2840");
        beginJob(transport, "Android FAX-2840", sink);
        boolean pageStarted = false;
        try (PdfRenderer renderer = new PdfRenderer(pdfFd)) {
            if (renderer.getPageCount() == 0) throw new IOException("PDF contains no pages");
            event(sink, "PDF pages=" + renderer.getPageCount());
            writePageHeader(transport, copies);
            for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                checkCancelled(cancelled);
                event(sink, "page " + (pageIndex + 1) + ": render/start");
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    pageStarted = true;
                    writeRasterPage(page, transport, cancelled, sink, density);
                }
                event(sink, "page " + (pageIndex + 1) + ": sent bytes=" + transport.getBytesWritten());
            }
        } finally {
            try {
                endJob(transport, "Android FAX-2840", sink);
            } catch (IOException endError) {
                if (pageStarted) throw endError;
            }
        }
    }

    static void printDiagnosticPage(Fax2840Usb transport, DiagnosticSink sink) throws IOException {
        event(sink, "direct-test: verify printer");
        if (!transport.confirmsFax2840()) throw new IOException("Connected USB device did not identify as Brother FAX-2840");
        beginJob(transport, "Android FAX-2840 Direct Test", sink);
        try {
            writePageHeader(transport, 1);
            transport.writeAscii("\033*b1030m");
            HbpCodec.BlockWriter block = new HbpCodec.BlockWriter(transport);
            int lineBytes = (A4_WIDTH_PX + 7) / 8;
            for (int y = 0; y < A4_HEIGHT_PX; y++) {
                byte[] mono = new byte[lineBytes];
                boolean horizontal = y >= 350 && y < 390;
                if (horizontal) {
                    for (int x = 600; x < A4_WIDTH_PX - 600; x++) mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                }
                if (y >= 600 && y < 1700) {
                    for (int x = 650; x < 690; x++) mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                    for (int x = A4_WIDTH_PX - 690; x < A4_WIDTH_PX - 650; x++) mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                }
                block.addLine(HbpCodec.encodeAbsoluteLine(mono));
                if ((y + 1) % 64 == 0) {
                    block.flush();
                    if ((y + 1) % 1024 == 0) event(sink, "direct-test: raster lines=" + (y + 1) + " bytes=" + transport.getBytesWritten());
                }
            }
            block.flush();
            transport.writeAscii("1030M\f");
            event(sink, "direct-test: page complete bytes=" + transport.getBytesWritten());
        } finally {
            endJob(transport, "Android FAX-2840 Direct Test", sink);
        }
    }

    private static void beginJob(Fax2840Usb transport, String name, DiagnosticSink sink) throws IOException {
        transport.write(new byte[128]);
        transport.writeAscii("\033%-12345X@PJL\n");
        transport.writeAscii("@PJL JOB NAME=\"" + name + "\"\n");
        event(sink, "PJL job started bytes=" + transport.getBytesWritten());
    }

    private static void endJob(Fax2840Usb transport, String name, DiagnosticSink sink) throws IOException {
        transport.writeAscii("\033%-12345X@PJL\n");
        transport.writeAscii("@PJL EOJ NAME=\"" + name + "\"\n");
        transport.writeAscii("\033%-12345X\n");
        event(sink, "PJL job ended bytes=" + transport.getBytesWritten());
    }

    private static void writePageHeader(Fax2840Usb transport, int copies) throws IOException {
        transport.writeAscii("\033%-12345X@PJL\n");
        transport.writeAscii("@PJL SET RAS1200MODE = FALSE\n@PJL SET RESOLUTION = 600\n@PJL SET ECONOMODE = OFF\n@PJL SET SOURCETRAY = AUTO\n@PJL SET MEDIATYPE = PLAIN\n@PJL SET PAPER = A4\n@PJL SET PAGEPROTECT = AUTO\n@PJL SET ORIENTATION = PORTRAIT\n@PJL ENTER LANGUAGE = PCL\n");
        transport.writeAscii("\033E");
        transport.writeAscii("\033&l" + copies + "X");
    }

    private static void writeRasterPage(PdfRenderer.Page page, Fax2840Usb transport, CancelCheck cancelled, DiagnosticSink sink, int density) throws IOException {
        transport.writeAscii("\033*b1030m");
        HbpCodec.BlockWriter block = new HbpCodec.BlockWriter(transport);
        int pageWidth = page.getWidth(), pageHeight = page.getHeight();
        event(sink, "raster target=" + A4_WIDTH_PX + "x" + A4_HEIGHT_PX
                + " printable=" + PRINTABLE_WIDTH_PX + "x" + PRINTABLE_HEIGHT_PX
                + " margin=" + PRINTABLE_MARGIN_PX
                + " source=" + pageWidth + "x" + pageHeight
                + " dither=8x8 gamma=" + MIDTONE_GAMMA
                + " density=" + density);

        int lineBytes = (A4_WIDTH_PX + 7) / 8;
        for (int startY = 0; startY < A4_HEIGHT_PX; startY += STRIPE_HEIGHT) {
            checkCancelled(cancelled);
            int stripeHeight = Math.min(STRIPE_HEIGHT, A4_HEIGHT_PX - startY);
            Bitmap bitmap = Bitmap.createBitmap(A4_WIDTH_PX, stripeHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, pageToStripeMatrix(pageWidth, pageHeight, startY), PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

            int[] pixels = new int[A4_WIDTH_PX * stripeHeight];
            bitmap.getPixels(pixels, 0, A4_WIDTH_PX, 0, 0, A4_WIDTH_PX, stripeHeight);
            bitmap.recycle();

            for (int y = 0; y < stripeHeight; y++) {
                byte[] mono = new byte[lineBytes];
                int rowOffset = y * A4_WIDTH_PX;
                int globalY = startY + y;
                for (int x = 0; x < A4_WIDTH_PX; x++) {
                    int argb = pixels[rowOffset + x];
                    int a = Color.alpha(argb);
                    int r = Color.red(argb);
                    int g = Color.green(argb);
                    int b = Color.blue(argb);
                    int lum = (77 * r + 150 * g + 29 * b) >> 8;
                    if (a < 255) lum = (lum * a + 255 * (255 - a)) / 255;
                    if (shouldPrintBlack(lum, x, globalY, density)) {
                        mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                    }
                }
                block.addLine(HbpCodec.encodeAbsoluteLine(mono));
            }
            block.flush();
            if ((startY / STRIPE_HEIGHT) % 16 == 0) {
                event(sink, "raster y=" + startY + " bytes=" + transport.getBytesWritten());
            }
        }
        transport.writeAscii("1030M\f");
    }

    private static Matrix pageToStripeMatrix(int pdfWidth, int pdfHeight, int startY) {
        Matrix matrix = new Matrix();
        if (pdfWidth <= pdfHeight) {
            float scale = Math.min(
                    PRINTABLE_WIDTH_PX / (float)pdfWidth,
                    PRINTABLE_HEIGHT_PX / (float)pdfHeight);
            float renderedWidth = pdfWidth * scale;
            float renderedHeight = pdfHeight * scale;
            float dx = PRINTABLE_MARGIN_PX + (PRINTABLE_WIDTH_PX - renderedWidth) / 2f;
            float dy = PRINTABLE_MARGIN_PX + (PRINTABLE_HEIGHT_PX - renderedHeight) / 2f;
            matrix.setValues(new float[]{
                    scale, 0f, dx,
                    0f, scale, dy - startY,
                    0f, 0f, 1f
            });
        } else {
            float scale = Math.min(
                    PRINTABLE_WIDTH_PX / (float)pdfHeight,
                    PRINTABLE_HEIGHT_PX / (float)pdfWidth);
            float renderedWidth = pdfHeight * scale;
            float renderedHeight = pdfWidth * scale;
            float dx = PRINTABLE_MARGIN_PX + (PRINTABLE_WIDTH_PX - renderedWidth) / 2f;
            float dy = PRINTABLE_MARGIN_PX + (PRINTABLE_HEIGHT_PX - renderedHeight) / 2f;
            matrix.setValues(new float[]{
                    0f, -scale, dx + renderedWidth,
                    scale, 0f, dy - startY,
                    0f, 0f, 1f
            });
        }
        return matrix;
    }

    private static boolean shouldPrintBlack(int luminance, int x, int y, int density) {
        // Keep true black text/lines crisp. Density mainly controls photos,
        // illustrations and anti-aliased gray regions.
        if (luminance <= 32) return true;
        if (luminance >= 252) return false;

        int d = clampDensity(density);
        int densityOffset = (PrintQualitySettings.DEFAULT_DENSITY - d) * 10;
        int adjusted = brightenLuminance(luminance) + densityOffset;
        adjusted = Math.max(0, Math.min(255, adjusted));

        int threshold = (BAYER_8X8[y & 7][x & 7] * 4) + 2;
        return adjusted < threshold;
    }

    private static int clampDensity(int density) {
        return Math.max(PrintQualitySettings.MIN_DENSITY,
                Math.min(PrintQualitySettings.MAX_DENSITY, density));
    }

    private static int brightenLuminance(int luminance) {
        if (luminance <= 32) return luminance;
        if (luminance >= 252) return 255;

        double normalized = luminance / 255.0;
        int adjusted = (int)Math.round(Math.pow(normalized, MIDTONE_GAMMA) * 255.0);
        return Math.max(0, Math.min(255, adjusted));
    }

    private static void event(DiagnosticSink sink, String message) {
        if (sink != null) sink.onEvent(message);
    }

    private static void checkCancelled(CancelCheck cancelled) {
        if (cancelled != null && cancelled.isCancelled()) throw new CancellationException("Print job cancelled");
    }
}
