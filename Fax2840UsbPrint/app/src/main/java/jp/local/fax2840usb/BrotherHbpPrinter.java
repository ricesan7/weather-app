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
    private static final int A4_WIDTH_PX = 4960;
    private static final int A4_HEIGHT_PX = 7016;
    private static final int STRIPE_HEIGHT = 64;
    private static final int BLACK_THRESHOLD = 192;
    private BrotherHbpPrinter() {}
    static void printPdf(ParcelFileDescriptor pdfFd, int copies, Fax2840Usb transport, CancelCheck cancelled) throws IOException {
        copies = Math.max(1, copies);
        if (!transport.confirmsFax2840()) throw new IOException("Connected USB device did not identify as Brother FAX-2840");
        transport.write(new byte[128]);
        transport.writeAscii("\033%-12345X@PJL\n");
        transport.writeAscii("@PJL JOB NAME=\"Android FAX-2840\"\n");
        boolean pageStarted = false;
        try (PdfRenderer renderer = new PdfRenderer(pdfFd)) {
            if (renderer.getPageCount() == 0) throw new IOException("PDF contains no pages");
            writePageHeader(transport, copies);
            for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                checkCancelled(cancelled);
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) { pageStarted = true; writeRasterPage(page, transport, cancelled); }
            }
        } finally {
            try {
                transport.writeAscii("\033%-12345X@PJL\n");
                transport.writeAscii("@PJL EOJ NAME=\"Android FAX-2840\"\n");
                transport.writeAscii("\033%-12345X\n");
            } catch (IOException endError) { if (pageStarted) throw endError; }
        }
    }
    private static void writePageHeader(Fax2840Usb transport, int copies) throws IOException {
        transport.writeAscii("\033%-12345X@PJL\n");
        transport.writeAscii("@PJL SET RAS1200MODE = FALSE\n@PJL SET RESOLUTION = 600\n@PJL SET ECONOMODE = OFF\n@PJL SET SOURCETRAY = AUTO\n@PJL SET MEDIATYPE = PLAIN\n@PJL SET PAPER = A4\n@PJL SET PAGEPROTECT = AUTO\n@PJL SET ORIENTATION = PORTRAIT\n@PJL ENTER LANGUAGE = PCL\n");
        transport.writeAscii("\033E");
        transport.writeAscii("\033&l" + copies + "X");
    }
    private static void writeRasterPage(PdfRenderer.Page page, Fax2840Usb transport, CancelCheck cancelled) throws IOException {
        transport.writeAscii("\033*b1030m");
        HbpCodec.BlockWriter block = new HbpCodec.BlockWriter(transport);
        int pageWidth = page.getWidth(), pageHeight = page.getHeight();
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
                for (int x = 0; x < A4_WIDTH_PX; x++) {
                    int argb = pixels[rowOffset + x];
                    int a = Color.alpha(argb), r = Color.red(argb), g = Color.green(argb), b = Color.blue(argb);
                    int lum = (77 * r + 150 * g + 29 * b) >> 8;
                    if (a < 255) lum = (lum * a + 255 * (255 - a)) / 255;
                    if (lum < BLACK_THRESHOLD) mono[x >> 3] |= (byte)(0x80 >> (x & 7));
                }
                block.addLine(HbpCodec.encodeAbsoluteLine(mono));
            }
            block.flush();
        }
        transport.writeAscii("1030M\f");
    }
    private static Matrix pageToStripeMatrix(int pdfWidth, int pdfHeight, int startY) {
        Matrix matrix = new Matrix();
        if (pdfWidth <= pdfHeight) {
            float sx = A4_WIDTH_PX / (float)pdfWidth, sy = A4_HEIGHT_PX / (float)pdfHeight;
            matrix.setValues(new float[]{sx,0f,0f,0f,sy,-startY,0f,0f,1f});
        } else {
            float sx = A4_WIDTH_PX / (float)pdfHeight, sy = A4_HEIGHT_PX / (float)pdfWidth;
            matrix.setValues(new float[]{0f,-sx,A4_WIDTH_PX,sy,0f,-startY,0f,0f,1f});
        }
        return matrix;
    }
    private static void checkCancelled(CancelCheck cancelled) { if (cancelled != null && cancelled.isCancelled()) throw new CancellationException("Print job cancelled"); }
}
