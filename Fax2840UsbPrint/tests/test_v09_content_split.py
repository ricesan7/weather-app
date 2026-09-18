from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    settings = read("PageSplitSettings.java")
    for token in ("MODE_FIT_PAGE", "MODE_AUTO_CONTENT", "MODE_HORIZONTAL_2", "MODE_HORIZONTAL_3", "MODE_HORIZONTAL_4"):
        assert token in settings, f"{token} missing"
    assert "getMode" in settings and "setMode" in settings, "persistent split mode missing"

    main_activity = read("MainActivity.java")
    assert "ページ分割方式" in main_activity, "page split mode UI missing"
    assert "自動（表向け）" in main_activity, "auto table mode label missing"
    assert "PageSplitSettings.setMode" in main_activity, "mode selection must persist"

    service = read("Fax2840PrintService.java")
    assert "PageSplitSettings.getMode" in service, "PrintService must read page split mode"
    assert "splitMode=" in service, "diagnostics must record split mode"

    printer = read("BrotherHbpPrinter.java")
    assert "detectContentBounds" in printer, "content-bound detection missing"
    assert "ContentBounds" in printer, "content-bound model missing"
    assert "writeCroppedTile" in printer, "cropped tile rendering missing"
    assert "AUTO_SCAN_MAX_PX" in printer, "low-resolution content scan missing"
    assert "MODE_HORIZONTAL_4" in printer, "fixed horizontal split modes missing"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '0.9.0'" in build, "versionName must be 0.9.0"

    print("v0.9 content-crop split regression checks passed")

if __name__ == "__main__":
    main()
