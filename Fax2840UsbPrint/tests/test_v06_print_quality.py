from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    printer = read("BrotherHbpPrinter.java")
    assert "PRINTABLE_MARGIN_PX = 100" in printer, "FAX-2840 12pt/600dpi printable margin must be 100px"
    assert "PRINTABLE_WIDTH_PX" in printer and "PRINTABLE_HEIGHT_PX" in printer, "printable-area dimensions missing"
    assert "BAYER_8X8" in printer, "8x8 ordered dithering matrix missing"
    assert "shouldPrintBlack" in printer, "ordered dithering decision helper missing"
    assert "brightenLuminance" in printer, "mid-tone lightening helper missing"
    assert "BLACK_THRESHOLD" not in printer, "fixed threshold must be removed for grayscale illustrations"
    assert "PRINTABLE_MARGIN_PX" in printer and "pageToStripeMatrix" in printer, "render transform must account for printable margins"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '0.6.0'" in build, "versionName must be 0.6.0"

    main_activity = read("MainActivity.java")
    assert "v0.6" in main_activity, "diagnostic UI must identify v0.6"

    print("v0.6 printable-area and dithering regression checks passed")

if __name__ == "__main__":
    main()
