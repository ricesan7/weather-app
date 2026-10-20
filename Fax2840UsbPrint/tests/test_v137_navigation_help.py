from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    preview = read("SharedPdfPreviewActivity.java")
    assert 'setText("トップページに戻る")' in preview, "preview must have a home button"
    assert "MainActivity.class" in preview, "home button must return explicitly to MainActivity"
    assert "Intent.FLAG_ACTIVITY_CLEAR_TOP" in preview, "home navigation must clear preview stack"

    main = read("MainActivity.java")
    for token in (
        "Excel / CSVの印刷方法",
        "PDFとして保存",
        "PDFを開いて印刷",
        "1ページに収める",
        "横6/8分割",
    ):
        assert token in main, f"missing spreadsheet printing guidance: {token}"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '1.3.7'" in build, "versionName must be 1.3.7"

    print("v1.3.7 navigation/help regression checks passed")


if __name__ == "__main__":
    main()
