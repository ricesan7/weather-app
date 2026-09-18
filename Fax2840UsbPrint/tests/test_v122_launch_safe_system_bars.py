from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    ui = read("AppUi.java")
    assert "WindowInsetsController" not in ui, "API 30 WindowInsetsController must not be referenced"
    assert "WindowInsets.Type" not in ui, "API 30 WindowInsets.Type must not be referenced"
    assert "getSystemWindowInsetTop" in ui, "legacy API20 inset path missing"
    assert "setOnApplyWindowInsetsListener" in ui, "inset listener missing"
    assert "SYSTEM_UI_FLAG_LIGHT_STATUS_BAR" in ui, "status bar icon appearance missing"

    main_activity = read("MainActivity.java")
    assert "AppUi.applySystemBarInsets(root" in main_activity, "home inset application missing"

    preview = read("SharedPdfPreviewActivity.java")
    assert "AppUi.applySystemBarInsets(root" in preview, "preview inset application missing"

    print("v1.2.2 launch-safe system bar regression checks passed")

if __name__ == "__main__":
    main()
