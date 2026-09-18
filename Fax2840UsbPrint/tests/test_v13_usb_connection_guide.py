from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def read(name):
    return (JAVA / name).read_text(encoding="utf-8")

def main():
    guide = JAVA / "UsbConnectionGuide.java"
    assert guide.exists(), "UsbConnectionGuide.java missing"
    g = guide.read_text(encoding="utf-8")
    for token in (
        "USB接続ガイド",
        "OTG",
        "USB Host",
        "USB Type-A",
        "USB Type-B",
        "変換アダプター",
        "データ通信対応",
        "接続済み",
    ):
        assert token in g, f"{token} missing from USB guide"

    main_activity = read("MainActivity.java")
    assert "USB接続ガイド" in main_activity, "USB guide card missing"
    assert "接続方法を見る" in main_activity, "USB guide expand button missing"
    assert "usbGuideContainer.setVisibility(View.GONE)" in main_activity, "USB guide should start collapsed"
    assert "UsbConnectionGuide.DETAILS" in main_activity, "USB guide details not wired to UI"

    build = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "versionName '1.3.0'" in build, "versionName must be 1.3.0"

    print("v1.3 USB connection guide regression checks passed")

if __name__ == "__main__":
    main()
