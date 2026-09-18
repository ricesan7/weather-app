from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    ui = read("AppUi.java")
    assert "applySystemBarInsets" in ui, "system-bar inset helper missing"
    assert "setOnApplyWindowInsetsListener" in ui, "WindowInsets listener missing"
    assert "getSystemWindowInsetTop" in ui, "status-bar top inset must be applied"
    assert "configureSystemBars" in ui, "system-bar appearance helper missing"
    assert "LIGHT_STATUS_BAR" in ui, "light status bar icons must be requested"

    main_activity = read("MainActivity.java")
    assert "AppUi.applySystemBarInsets(root" in main_activity, "home screen must stay below status bar"
    assert "AppUi.configureSystemBars(this)" in main_activity, "home system-bar appearance missing"

    preview = read("SharedPdfPreviewActivity.java")
    assert "AppUi.applySystemBarInsets(root" in preview, "preview screen must stay below status bar"
    assert "AppUi.configureSystemBars(this)" in preview, "preview system-bar appearance missing"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '1.2.1'" in build, "versionName must be 1.2.1"

    print("v1.2.1 system-bar inset regression checks passed")

if __name__ == "__main__":
    main()
