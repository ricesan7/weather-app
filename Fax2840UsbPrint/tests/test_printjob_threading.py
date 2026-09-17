from pathlib import Path

src = Path("Fax2840UsbPrint/app/src/main/java/jp/local/fax2840usb/Fax2840PrintService.java").read_text(encoding="utf-8")

# Android PrintJob / PrintDocument APIs are main-thread only.
# The service must not pass PrintJob itself into a background worker.
assert 'new Thread(() -> runPrintJob(printJob)' not in src, (
    'PrintJob is being used from a background thread; capture document/copies/start state on the main thread first'
)

# Cancellation checks executed by BrotherHbpPrinter must not call PrintJob methods off-main-thread.
assert 'printJob::isCancelled' not in src, (
    'Background raster/USB code still calls PrintJob.isCancelled(); use a thread-safe cancellation flag instead'
)

# Worker code should post final state transitions back to the main thread.
assert 'new Handler(Looper.getMainLooper())' in src or 'mainHandler' in src, (
    'No main-thread dispatcher found for complete/fail/cancel state transitions'
)

print('PrintJob thread-safety checks passed')
