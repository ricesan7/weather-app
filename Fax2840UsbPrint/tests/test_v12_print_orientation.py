from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    settings = JAVA / "PrintOrientationSettings.java"
    assert settings.exists(), "PrintOrientationSettings.java missing"
    s = settings.read_text(encoding="utf-8")
    for token in ("MODE_AUTO", "MODE_PORTRAIT", "MODE_LANDSCAPE", "getMode", "setMode", "MODE_LABELS"):
        assert token in s, f"{token} missing from orientation settings"

    generator = read("SplitPdfGenerator.java")
    for token in ("orientationMode", "resolveOrientation", "MODE_LANDSCAPE", "A4_LANDSCAPE_WIDTH_PT", "A4_LANDSCAPE_HEIGHT_PT"):
        assert token in generator, f"{token} missing from split PDF generator"
    assert "resultOrientationMode" in generator, "resolved orientation must be returned with prepared PDF"

    preview = read("SharedPdfPreviewActivity.java")
    assert "印刷向き" in preview, "orientation selector missing from preview UI"
    assert "PrintOrientationSettings.setMode" in preview, "preview orientation selection must persist"
    assert "PrintOrientationSettings.getMode" in preview, "preview must read saved orientation"
    assert "MediaSize.ISO_A4.asLandscape()" in preview, "Android print preview must start in landscape when selected"
    assert "preparedOrientationMode" in preview, "prepared PDF orientation must drive Android print attributes"

    main_activity = read("MainActivity.java")
    assert "印刷向き" in main_activity, "orientation selector missing from home settings"
    assert "PrintOrientationSettings.setMode" in main_activity, "home orientation selection must persist"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '1.2.0'" in build, "versionName must be 1.2.0"

    print("v1.2 print orientation regression checks passed")

if __name__ == "__main__":
    main()
