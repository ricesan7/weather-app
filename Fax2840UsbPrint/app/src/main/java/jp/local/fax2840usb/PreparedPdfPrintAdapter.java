package jp.local.fax2840usb;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

final class PreparedPdfPrintAdapter extends PrintDocumentAdapter {
    static final String DOCUMENT_MARKER = "FAX2840_PREVIEW_READY";
    private final File pdfFile;
    private final int pageCount;

    PreparedPdfPrintAdapter(File pdfFile, int pageCount) {
        this.pdfFile = pdfFile;
        this.pageCount = Math.max(1, pageCount);
    }

    @Override public void onLayout(PrintAttributes oldAttributes,
                                   PrintAttributes newAttributes,
                                   CancellationSignal cancellationSignal,
                                   LayoutResultCallback callback,
                                   android.os.Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }

        PrintDocumentInfo info = new PrintDocumentInfo.Builder(
                DOCUMENT_MARKER + ".pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build();
        callback.onLayoutFinished(info, true);
    }

    @Override public void onWrite(PageRange[] pages,
                                  ParcelFileDescriptor destination,
                                  CancellationSignal cancellationSignal,
                                  WriteResultCallback callback) {
        if (cancellationSignal.isCanceled()) {
            callback.onWriteCancelled();
            return;
        }

        try (FileInputStream in = new FileInputStream(pdfFile);
             FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    return;
                }
                out.write(buffer, 0, read);
            }
            out.flush();
            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
        } catch (IOException e) {
            callback.onWriteFailed(e.getMessage());
        }
    }
}
