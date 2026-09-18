from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    manifest = MANIFEST.read_text(encoding="utf-8")
    assert "SharedPdfPreviewActivity" in manifest, "PDF share receiver activity missing"
    assert "android.intent.action.SEND" in manifest, "ACTION_SEND filter missing"
    assert 'android:mimeType="application/pdf"' in manifest, "PDF MIME filter missing"

    generator = JAVA / "SplitPdfGenerator.java"
    assert generator.exists(), "SplitPdfGenerator.java missing"
    g = generator.read_text(encoding="utf-8")
    for token in ("generate", "detectContentBounds", "PdfDocument", "PdfRenderer", "horizontalSegment"):
        assert token in g, f"{token} missing from split PDF generator"

    preview = JAVA / "SharedPdfPreviewActivity.java"
    assert preview.exists(), "SharedPdfPreviewActivity.java missing"
    p = preview.read_text(encoding="utf-8")
    assert "分割プレビュー" in p, "preview UI label missing"
    assert "ImageView" in p and "renderPreparedPreview" in p, "page thumbnail preview missing"
    assert "PrintManager" in p and "PreparedPdfPrintAdapter" in p, "Android print preview handoff missing"

    adapter = JAVA / "PreparedPdfPrintAdapter.java"
    assert adapter.exists(), "PreparedPdfPrintAdapter.java missing"
    a = adapter.read_text(encoding="utf-8")
    assert "FAX2840_PREVIEW_READY" in a, "prepared PDF marker missing"
    assert "onLayout" in a and "onWrite" in a, "PrintDocumentAdapter implementation incomplete"

    service = read("Fax2840PrintService.java")
    assert "PreparedPdfPrintAdapter.DOCUMENT_MARKER" in service, "PrintService must recognize already-split PDFs"
    assert "preparedPreview" in service and "MODE_FIT_PAGE" in service, "prepared PDFs must bypass re-splitting"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '1.0.0'" in build, "versionName must be 1.0.0"

    print("v1.0 shared-PDF preview regression checks passed")

if __name__ == "__main__":
    main()
