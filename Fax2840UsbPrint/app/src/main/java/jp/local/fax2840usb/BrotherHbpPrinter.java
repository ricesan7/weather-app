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

    // Content detection is intentionally low resolution: enough to find the
    // actual table/document area without allocating another full 600dpi page.
    private static final int AUTO_SCAN_MAX_PX = 1024;
    private static final int AUTO_WHITE_THRESHOLD = 246;
    private static final int AUTO_SCAN_PADDING_PX = 6;
    private static final float AUTO_TARGET_SEGMENT_ASPECT = 3.6f;
    private static final int AUTO_MAX_COLUMNS = 4;

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

    static void printPdf(ParcelFileDescriptor pdfFd, int copies, Fax2840Usb transport,
                         CancelCheck cancelled, DiagnosticSink sink) throws IOException {
        printPdf(pdfFd, copies, transport, cancelled, sink,
                PrintQualitySettings.DEFAULT_DENSITY, PageSplitSettings.DEFAULT_MODE);
    }

    static void printPdf(ParcelFileDescriptor pdfFd, int copies, Fax2840Usb transport,
                         CancelCheck cancelled, DiagnosticSink sink, int density) throws IOException {
        printPdf(pdfFd, copies, transport, cancelled, sink,
                density, PageSplitSettings.DEFAULT_MODE);
    }

    static void printPdf(ParcelFileDescriptor pdfFd, int copies, Fax2840Usb transport,
                         CancelCheck cancelled, DiagnosticSink sink, int density, int splitMode) throws IOException {
        copies = Math.max(1, copies);
        density = clampDensity(density);
        splitMode = PageSplitSettings.normalizeMode(splitMode);

        event(sink, "HBP: verify printer");
        if (!transport.confirmsFax2840()) {
            throw new IOException("Connected USB device did not identify as Brother FAX-2840");
        }

        beginJob(transport, "Android FAX-2840", sink);
        boolean pageStarted = false;

        try (PdfRenderer renderer = new PdfRenderer(pdfFd)) {
            if (renderer.getPageCount() == 0) throw new IOException("PDF contains no pages");
            event(sink, "PDF pages=" + renderer.getPageCount()
                    + " splitMode=" + splitMode
                    + " (" + PageSplitSettings.labelForMode(splitMode) + ")");
            writePageHeader(transport, copies);

            for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                checkCancelled(cancelled);
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    if (splitMode == PageSplitSettings.MODE_FIT_PAGE) {
                        pageStarted = true;
                        event(sink, "page " + (pageIndex + 1) + ": fit-page");
                        writeRasterPage(page, transport, cancelled, sink, density);
                    } else {
                        ContentBounds bounds = detectContentBounds(page, sink);
                        int tileCols = columnsForMode(splitMode, bounds);
                        int tileRows = 1;
                        event(sink, "page " + (pageIndex + 1)
                                + ": content split tileCols=" + tileCols
                                + " tileRows=" + tileRows);

                        for (int tileCol = 0; tileCol < tileCols; tileCol++) {
                            checkCancelled(cancelled);
                            pageStarted = true;
                            ContentBounds segment = bounds.horizontalSegment(tileCol, tileCols);
                            event(sink, "page " + (pageIndex + 1)
                                    + ": tile " + (tileCol + 1) + "/" + tileCols
                                    + " src=" + segment.describe());
                            writeCroppedTile(page, segment, transport, cancelled, sink, density);
                        }
                    }
                }
                event(sink, "page " + (pageIndex + 1)
                        + ": sent bytes=" + transport.getBytesWritten());
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
        if (!transport.confirmsFax2840()) {
            throw new IOException("Connected USB device did not identify as Brother FAX-2840");
        }
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
                    for (int x = 600; x < A4_WIDTH_PX - 600; x++) {
                        mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                    }
                }
                if (y >= 600 && y < 1700) {
                    for (int x = 650; x < 690; x++) mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                    for (int x = A4_WIDTH_PX - 690; x < A4_WIDTH_PX - 650; x++) {
                        mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                    }
                }
                block.addLine(HbpCodec.encodeAbsoluteLine(mono));
                if ((y + 1) % 64 == 0) {
                    block.flush();
                    if ((y + 1) % 1024 == 0) {
                        event(sink, "direct-test: raster lines=" + (y + 1)
                                + " bytes=" + transport.getBytesWritten());
                    }
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
        transport.writeAscii("@PJL SET RAS1200MODE = FALSE\n"
                + "@PJL SET RESOLUTION = 600\n"
                + "@PJL SET ECONOMODE = OFF\n"
                + "@PJL SET SOURCETRAY = AUTO\n"
                + "@PJL SET MEDIATYPE = PLAIN\n"
                + "@PJL SET PAPER = A4\n"
                + "@PJL SET PAGEPROTECT = AUTO\n"
                + "@PJL SET ORIENTATION = PORTRAIT\n"
                + "@PJL ENTER LANGUAGE = PCL\n");
        transport.writeAscii("\033E");
        transport.writeAscii("\033&l" + copies + "X");
    }

    private static void writeRasterPage(PdfRenderer.Page page, Fax2840Usb transport,
                                        CancelCheck cancelled, DiagnosticSink sink, int density) throws IOException {
        transport.writeAscii("\033*b1030m");
        HbpCodec.BlockWriter block = new HbpCodec.BlockWriter(transport);
        int pageWidth = page.getWidth();
        int pageHeight = page.getHeight();

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

            page.render(bitmap, null, pageToStripeMatrix(pageWidth, pageHeight, startY),
                    PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

            rasterizeStripe(bitmap, startY, lineBytes, block, density);
            if ((startY / STRIPE_HEIGHT) % 16 == 0) {
                event(sink, "raster y=" + startY + " bytes=" + transport.getBytesWritten());
            }
        }
        transport.writeAscii("1030M\f");
    }

    private static void writeCroppedTile(PdfRenderer.Page page, ContentBounds source,
                                         Fax2840Usb transport, CancelCheck cancelled,
                                         DiagnosticSink sink, int density) throws IOException {
        transport.writeAscii("\033*b1030m");
        HbpCodec.BlockWriter block = new HbpCodec.BlockWriter(transport);

        float scale = Math.min(
                PRINTABLE_WIDTH_PX / source.width(),
                PRINTABLE_HEIGHT_PX / source.height());
        float renderedWidth = source.width() * scale;
        float renderedHeight = source.height() * scale;
        float destX = PRINTABLE_MARGIN_PX + (PRINTABLE_WIDTH_PX - renderedWidth) / 2f;
        float destY = PRINTABLE_MARGIN_PX;

        event(sink, "crop render src=" + source.describe()
                + " scale=" + scale
                + " out=" + Math.round(renderedWidth) + "x" + Math.round(renderedHeight)
                + " density=" + density);

        int lineBytes = (A4_WIDTH_PX + 7) / 8;
        for (int startY = 0; startY < A4_HEIGHT_PX; startY += STRIPE_HEIGHT) {
            checkCancelled(cancelled);
            int stripeHeight = Math.min(STRIPE_HEIGHT, A4_HEIGHT_PX - startY);
            Bitmap bitmap = Bitmap.createBitmap(A4_WIDTH_PX, stripeHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);

            Matrix matrix = new Matrix();
            matrix.setValues(new float[]{
                    scale, 0f, destX - source.left * scale,
                    0f, scale, destY - source.top * scale - startY,
                    0f, 0f, 1f
            });

            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            rasterizeStripe(bitmap, startY, lineBytes, block, density);

            if ((startY / STRIPE_HEIGHT) % 16 == 0) {
                event(sink, "crop raster y=" + startY + " bytes=" + transport.getBytesWritten());
            }
        }

        transport.writeAscii("1030M\f");
    }

    private static void rasterizeStripe(Bitmap bitmap, int startY, int lineBytes,
                                        HbpCodec.BlockWriter block, int density) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();

        for (int y = 0; y < height; y++) {
            byte[] mono = new byte[lineBytes];
            int rowOffset = y * width;
            int globalY = startY + y;

            for (int x = 0; x < width; x++) {
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
    }

    private static ContentBounds detectContentBounds(PdfRenderer.Page page, DiagnosticSink sink) {
        int pageWidth = page.getWidth();
        int pageHeight = page.getHeight();

        float scanScale = Math.min(
                AUTO_SCAN_MAX_PX / (float)pageWidth,
                AUTO_SCAN_MAX_PX / (float)pageHeight);
        scanScale = Math.min(scanScale, 2.0f);

        int scanWidth = Math.max(1, Math.round(pageWidth * scanScale));
        int scanHeight = Math.max(1, Math.round(pageHeight * scanScale));

        Bitmap scan = Bitmap.createBitmap(scanWidth, scanHeight, Bitmap.Config.ARGB_8888);
        scan.eraseColor(Color.WHITE);
        Matrix matrix = new Matrix();
        matrix.setScale(scanWidth / (float)pageWidth, scanHeight / (float)pageHeight);
        page.render(scan, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

        int[] pixels = new int[scanWidth * scanHeight];
        scan.getPixels(pixels, 0, scanWidth, 0, 0, scanWidth, scanHeight);
        scan.recycle();

        int minX = scanWidth;
        int minY = scanHeight;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < scanHeight; y++) {
            int row = y * scanWidth;
            for (int x = 0; x < scanWidth; x++) {
                int argb = pixels[row + x];
                int a = Color.alpha(argb);
                int r = Color.red(argb);
                int g = Color.green(argb);
                int b = Color.blue(argb);
                int lum = (77 * r + 150 * g + 29 * b) >> 8;
                if (a < 255) lum = (lum * a + 255 * (255 - a)) / 255;

                if (lum < AUTO_WHITE_THRESHOLD) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            ContentBounds full = new ContentBounds(0f, 0f, pageWidth, pageHeight);
            event(sink, "contentBounds=none; using full page");
            return full;
        }

        minX = Math.max(0, minX - AUTO_SCAN_PADDING_PX);
        minY = Math.max(0, minY - AUTO_SCAN_PADDING_PX);
        maxX = Math.min(scanWidth - 1, maxX + AUTO_SCAN_PADDING_PX);
        maxY = Math.min(scanHeight - 1, maxY + AUTO_SCAN_PADDING_PX);

        float left = minX * pageWidth / (float)scanWidth;
        float top = minY * pageHeight / (float)scanHeight;
        float right = (maxX + 1) * pageWidth / (float)scanWidth;
        float bottom = (maxY + 1) * pageHeight / (float)scanHeight;

        ContentBounds result = new ContentBounds(
                Math.max(0f, left),
                Math.max(0f, top),
                Math.min(pageWidth, right),
                Math.min(pageHeight, bottom));

        event(sink, "contentBounds=" + result.describe()
                + " page=" + pageWidth + "x" + pageHeight
                + " occupancy="
                + Math.round(100f * result.width() * result.height() / (pageWidth * (float)pageHeight))
                + "%");

        return result;
    }

    private static int columnsForMode(int splitMode, ContentBounds bounds) {
        switch (splitMode) {
            case PageSplitSettings.MODE_HORIZONTAL_2:
                return 2;
            case PageSplitSettings.MODE_HORIZONTAL_3:
                return 3;
            case PageSplitSettings.MODE_HORIZONTAL_4:
                return 4;
            case PageSplitSettings.MODE_AUTO_CONTENT:
            default:
                float aspect = bounds.width() / Math.max(1f, bounds.height());
                int auto = (int)Math.ceil(aspect / AUTO_TARGET_SEGMENT_ASPECT);
                return Math.max(1, Math.min(AUTO_MAX_COLUMNS, auto));
        }
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
        if (cancelled != null && cancelled.isCancelled()) {
            throw new CancellationException("Print job cancelled");
        }
    }

    private static final class ContentBounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        ContentBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left + 1f, right);
            this.bottom = Math.max(top + 1f, bottom);
        }

        float width() { return right - left; }
        float height() { return bottom - top; }

        ContentBounds horizontalSegment(int index, int count) {
            int safeCount = Math.max(1, count);
            int safeIndex = Math.max(0, Math.min(safeCount - 1, index));
            float segmentWidth = width() / safeCount;
            float segmentLeft = left + safeIndex * segmentWidth;
            float segmentRight = safeIndex == safeCount - 1 ? right : segmentLeft + segmentWidth;
            return new ContentBounds(segmentLeft, top, segmentRight, bottom);
        }

        String describe() {
            return Math.round(left) + "," + Math.round(top) + "-"
                    + Math.round(right) + "," + Math.round(bottom)
                    + " (" + Math.round(width()) + "x" + Math.round(height()) + ")";
        }
    }
}
