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

    printer = read("BrotherHbpPrinter.java")
    assert "tileCols" in printer or "writeCroppedTile" in printer, "multi-page tile rendering capability missing"

    print("v0.8 split-page regression checks passed")

if __name__ == "__main__":
    main()
