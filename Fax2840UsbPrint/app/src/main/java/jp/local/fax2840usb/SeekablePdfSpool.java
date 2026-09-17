package jp.local.fax2840usb;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class SeekablePdfSpool implements Closeable {
    private static final int BUFFER_SIZE = 64 * 1024;

    private final File tempFile;
    private final ParcelFileDescriptor seekablePdf;
    private final long bytesCopied;

    private SeekablePdfSpool(File tempFile, ParcelFileDescriptor seekablePdf, long bytesCopied) {
        this.tempFile = tempFile;
        this.seekablePdf = seekablePdf;
        this.bytesCopied = bytesCopied;
    }

    static SeekablePdfSpool from(Context context, ParcelFileDescriptor source) throws IOException {
        if (context == null) throw new IOException("Context unavailable for PDF spool");
        if (source == null) throw new IOException("Source PDF descriptor is null");

        File temp = File.createTempFile("fax2840-spool-", ".pdf", context.getCacheDir());
        boolean success = false;
        long total = 0L;

        try {
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(source);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    total += n;
                }
                out.flush();
            }

            if (total <= 0L) throw new IOException("Print document was empty");

            ParcelFileDescriptor reopened = ParcelFileDescriptor.open(
                    temp,
                    ParcelFileDescriptor.MODE_READ_ONLY);
            if (reopened == null) throw new IOException("Could not reopen temporary PDF");

            SeekablePdfSpool result = new SeekablePdfSpool(temp, reopened, total);
            success = true;
            return result;
        } finally {
            if (!success && temp.exists()) temp.delete();
        }
    }

    ParcelFileDescriptor getSeekablePdf() {
        return seekablePdf;
    }

    long getBytesCopied() {
        return bytesCopied;
    }

    @Override public void close() {
        try { seekablePdf.close(); } catch (IOException ignored) {}
        if (tempFile.exists()) tempFile.delete();
    }
}
