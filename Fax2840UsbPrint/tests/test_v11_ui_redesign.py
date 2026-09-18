from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    ui = JAVA / "AppUi.java"
    assert ui.exists(), "AppUi.java missing"
    u = ui.read_text(encoding="utf-8")
    for token in ("COLOR_PRIMARY", "card(", "stylePrimaryButton", "styleSecondaryButton"):
        assert token in u, f"{token} missing from AppUi"

    main_activity = read("MainActivity.java")
    assert "PDFを開いて印刷" in main_activity, "primary PDF open action missing"
    assert "Intent.ACTION_OPEN_DOCUMENT" in main_activity, "system PDF picker missing"
    assert "SharedPdfPreviewActivity" in main_activity, "picked PDF must open preview flow"
    assert "印刷設定" in main_activity, "print settings card missing"
    assert "詳細設定・診断" in main_activity, "advanced diagnostics toggle missing"
    assert "advancedContainer.setVisibility(View.GONE)" in main_activity, "advanced tools must start collapsed"
    assert "AppUi.stylePrimaryButton" in main_activity, "home CTA must use primary style"

    preview = read("SharedPdfPreviewActivity.java")
    assert "分割プレビュー" in preview, "preview title missing"
    assert "印刷へ進む" in preview, "bottom print CTA missing"
    assert "pageCard" in preview, "page-card preview layout missing"
    assert "AppUi.stylePrimaryButton" in preview, "preview CTA must use primary style"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '1.1.0'" in build, "versionName must be 1.1.0"

    print("v1.1 UI redesign regression checks passed")

if __name__ == "__main__":
    main()
