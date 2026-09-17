from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    helper = JAVA / "SeekablePdfSpool.java"
    assert helper.exists(), "SeekablePdfSpool.java must copy non-seekable print data into a seekable temp PDF"

    helper_text = helper.read_text(encoding="utf-8")
    assert "File.createTempFile" in helper_text, "temp PDF creation missing"
    assert "ParcelFileDescriptor.open" in helper_text, "seekable ParcelFileDescriptor reopen missing"
    assert "MODE_READ_ONLY" in helper_text, "temp PDF must be reopened read-only"
    assert "getBytesCopied" in helper_text, "copy byte count must be exposed for diagnostics"
    assert "delete" in helper_text, "temp PDF cleanup missing"

    service = read("Fax2840PrintService.java")
    assert "SeekablePdfSpool" in service, "PrintService must spool incoming non-seekable PDF"
    assert "spool bytes=" in service, "PrintService must log copied PDF size"
    assert "getSeekablePdf" in service, "PrintService must pass seekable PDF to PdfRenderer path"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '0.5.0'" in build, "versionName must be 0.5.0"

    print("v0.5 seekable PDF regression checks passed")


if __name__ == "__main__":
    main()
