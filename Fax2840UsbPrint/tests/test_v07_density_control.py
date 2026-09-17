from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    settings = JAVA / "PrintQualitySettings.java"
    assert settings.exists(), "PrintQualitySettings.java missing"
    s = settings.read_text(encoding="utf-8")
    assert "DEFAULT_DENSITY = 5" in s, "default density must be 5"
    assert "MIN_DENSITY = 1" in s and "MAX_DENSITY = 10" in s, "density range must be 1..10"
    assert "getDensity" in s and "setDensity" in s, "persistent density getters/setters missing"

    main = read("MainActivity.java")
    assert "SeekBar" in main, "density SeekBar missing"
    assert "黒濃度" in main, "density UI label missing"
    assert "PrintQualitySettings.setDensity" in main, "density changes must persist"

    svc = read("Fax2840PrintService.java")
    assert "PrintQualitySettings.getDensity" in svc, "PrintService must read saved density"
    assert "density=" in svc, "diagnostic log must record density"

    printer = read("BrotherHbpPrinter.java")
    assert "shouldPrintBlack(int luminance, int x, int y, int density)" in printer, "density-aware dithering missing"
    assert "densityOffset" in printer, "density must influence dither luminance"
    assert "clampDensity" in printer, "density input must be clamped"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '0.7.0'" in build, "versionName must be 0.7.0"

    print("v0.7 density-control regression checks passed")

if __name__ == "__main__":
    main()
