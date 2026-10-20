from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    preview = read("SharedPdfPreviewActivity.java")
    assert "トップへ戻る" in preview, "preview needs a visible home button"
    assert "Intent.FLAG_ACTIVITY_CLEAR_TOP" in preview, "home button must return to the existing top screen"

    main_activity = read("MainActivity.java")
    assert "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" in main_activity
    assert "text/csv" in main_activity
    assert "TableFileToPdf.convert" in main_activity, "Excel/CSV must convert directly inside the app"
    assert "PDF / Excel / CSVを開いて印刷" in main_activity

    converter = JAVA / "TableFileToPdf.java"
    assert converter.exists(), "direct table converter is missing"
    c = converter.read_text(encoding="utf-8")
    for token in ("ZipInputStream", "readCsv", "readXlsx", "PdfDocument", "columnGroups"):
        assert token in c, f"{token} missing from direct table converter"
    assert "MIN_FONT_SIZE_PT" in c, "converter must protect readable text size"

    manifest = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
    assert "text/csv" in manifest, "CSV share intent is missing"
    assert "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" in manifest, "XLSX share intent is missing"

    print("v1.3.7 direct Excel/CSV printing and home navigation checks passed")


if __name__ == "__main__":
    main()
