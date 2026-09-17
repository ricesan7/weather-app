from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    settings = JAVA / "PageSplitSettings.java"
    assert settings.exists(), "PageSplitSettings.java missing"
    s = settings.read_text(encoding="utf-8")
    assert "ZOOM_LEVELS" in s, "zoom presets missing"
    for value in ("100", "150", "200", "250", "300"):
        assert value in s, f"zoom preset {value}% missing"
    assert "getZoomPercent" in s and "setZoomPercent" in s, "persistent zoom getter/setter missing"

    main_activity = read("MainActivity.java")
    assert "分割拡大" in main_activity, "split/zoom UI missing"
    assert "PageSplitSettings.setZoomPercent" in main_activity, "zoom changes must persist"

    service = read("Fax2840PrintService.java")
    assert "PageSplitSettings.getZoomPercent" in service, "PrintService must read zoom setting"
    assert "zoom=" in service, "diagnostics must record zoom"

    printer = read("BrotherHbpPrinter.java")
    assert "writeRasterTile" in printer, "tile renderer missing"
    assert "tileCols" in printer and "tileRows" in printer, "tile grid calculation missing"
    assert "zoomPercent" in printer, "printer must receive zoom percentage"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '0.8.0'" in build, "versionName must be 0.8.0"

    print("v0.8 split-page regression checks passed")

if __name__ == "__main__":
    main()
