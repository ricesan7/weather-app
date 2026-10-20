from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    preview = read("SharedPdfPreviewActivity.java")

    assert "PreparedPdfPrintAdapter.DOCUMENT_MARKER" in preview
    assert "setMediaSize(" not in preview, (
        "prepared preview must not force Android media orientation; "
        "the prepared PDF already contains the final page orientation"
    )
    assert "ISO_A4.asLandscape()" not in preview, (
        "landscape must not be applied a second time in Android print attributes"
    )

    service = read("Fax2840PrintService.java")
    assert "preparedPreview" in service
    assert "PageSplitSettings.MODE_FIT_PAGE" in service

    print("v1.3.4 prepared-PDF orientation handoff regression checks passed")


if __name__ == "__main__":
    main()
