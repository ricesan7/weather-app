package jp.local.fax2840usb;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class SplitPdfGenerator {
    private static final int A4_WIDTH_PT = 595;
    private static final int A4_HEIGHT_PT = 842;
    private static final int OUTPUT_WIDTH_PX = 2480;
    private static final int OUTPUT_HEIGHT_PX = 3508;
    private static final int AUTO_SCAN_MAX_PX = 1024;
    private static final int AUTO_WHITE_THRESHOLD = 246;
    private static final int AUTO_SCAN_PADDING_PX = 6;
    private static final float AUTO_TARGET_SEGMENT_ASPECT = 3.6f;
    private static final int AUTO_MAX_COLUMNS = 4;

    static final class Result {
        final File file;
        final int pageCount;

        Result(File file, int pageCount) {
            this.file = file;
            this.pageCount = pageCount;
        }
    }

    private SplitPdfGenerator() {}

    static Result generate(File sourcePdf, File outputPdf, int splitMode) throws IOException {
        splitMode = PageSplitSettings.normalizeMode(splitMode);
        int outputPages = 0;

        try (ParcelFileDescriptor sourceFd = ParcelFileDescriptor.open(
                     sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(sourceFd);
             PdfDocument document = new PdfDocument()) {

            for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    ContentBounds bounds = splitMode == PageSplitSettings.MODE_FIT_PAGE
                            ? new ContentBounds(0f, 0f, page.getWidth(), page.getHeight())
                            : detectContentBounds(page);

                    int columns = columnsForMode(splitMode, bounds);
                    for (int col = 0; col < columns; col++) {
                        ContentBounds segment = columns == 1
                                ? bounds
                                : bounds.horizontalSegment(col, columns);

                        Bitmap bitmap = renderSegment(page, segment);
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                                A4_WIDTH_PT, A4_HEIGHT_PT, ++outputPages).create();
                        PdfDocument.Page outPage = document.startPage(pageInfo);
                        Canvas canvas = outPage.getCanvas();
                        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                        canvas.drawColor(Color.WHITE);
                        canvas.drawBitmap(bitmap, null,
                                new RectF(0f, 0f, A4_WIDTH_PT, A4_HEIGHT_PT), paint);
                        document.finishPage(outPage);
                        bitmap.recycle();
                    }
                }
            }

            try (FileOutputStream out = new FileOutputStream(outputPdf, false)) {
                document.writeTo(out);
                out.flush();
            }
        }

        if (outputPages <= 0) throw new IOException("PDF contains no printable pages");
        return new Result(outputPdf, outputPages);
    }

    private static Bitmap renderSegment(PdfRenderer.Page page, ContentBounds source) {
        Bitmap bitmap = Bitmap.createBitmap(
                OUTPUT_WIDTH_PX, OUTPUT_HEIGHT_PX, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        float scale = Math.min(
                OUTPUT_WIDTH_PX / source.width(),
                OUTPUT_HEIGHT_PX / source.height());
        float renderedWidth = source.width() * scale;
        float renderedHeight = source.height() * scale;
        float dx = (OUTPUT_WIDTH_PX - renderedWidth) / 2f;
        float dy = (OUTPUT_HEIGHT_PX - renderedHeight) / 2f;

        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{
                scale, 0f, dx - source.left * scale,
                0f, scale, dy - source.top * scale,
                0f, 0f, 1f
        });
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        return bitmap;
    }

    private static ContentBounds detectContentBounds(PdfRenderer.Page page) {
        int pageWidth = page.getWidth();
        int pageHeight = page.getHeight();
        float scale = Math.min(
                AUTO_SCAN_MAX_PX / (float)pageWidth,
                AUTO_SCAN_MAX_PX / (float)pageHeight);
        scale = Math.min(scale, 2f);

        int scanWidth = Math.max(1, Math.round(pageWidth * scale));
        int scanHeight = Math.max(1, Math.round(pageHeight * scale));

        Bitmap scan = Bitmap.createBitmap(scanWidth, scanHeight, Bitmap.Config.ARGB_8888);
        scan.eraseColor(Color.WHITE);
        Matrix matrix = new Matrix();
        matrix.setScale(scanWidth / (float)pageWidth, scanHeight / (float)pageHeight);
        page.render(scan, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

        int[] pixels = new int[scanWidth * scanHeight];
        scan.getPixels(pixels, 0, scanWidth, 0, 0, scanWidth, scanHeight);
        scan.recycle();

        int minX = scanWidth, minY = scanHeight, maxX = -1, maxY = -1;
        for (int y = 0; y < scanHeight; y++) {
            int row = y * scanWidth;
            for (int x = 0; x < scanWidth; x++) {
                int argb = pixels[row + x];
                int a = Color.alpha(argb);
                int r = Color.red(argb), g = Color.green(argb), b = Color.blue(argb);
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
            return new ContentBounds(0f, 0f, pageWidth, pageHeight);
        }

        minX = Math.max(0, minX - AUTO_SCAN_PADDING_PX);
        minY = Math.max(0, minY - AUTO_SCAN_PADDING_PX);
        maxX = Math.min(scanWidth - 1, maxX + AUTO_SCAN_PADDING_PX);
        maxY = Math.min(scanHeight - 1, maxY + AUTO_SCAN_PADDING_PX);

        return new ContentBounds(
                minX * pageWidth / (float)scanWidth,
                minY * pageHeight / (float)scanHeight,
                (maxX + 1) * pageWidth / (float)scanWidth,
                (maxY + 1) * pageHeight / (float)scanHeight);
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
                float aspect = bounds.width() / Math.max(1f, bounds.height());
                int auto = (int)Math.ceil(aspect / AUTO_TARGET_SEGMENT_ASPECT);
                return Math.max(1, Math.min(AUTO_MAX_COLUMNS, auto));
            case PageSplitSettings.MODE_FIT_PAGE:
            default:
                return 1;
        }
    }

    static final class ContentBounds {
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
            float w = width() / safeCount;
            float l = left + safeIndex * w;
            float r = safeIndex == safeCount - 1 ? right : l + w;
            return new ContentBounds(l, top, r, bottom);
        }
    }
}
