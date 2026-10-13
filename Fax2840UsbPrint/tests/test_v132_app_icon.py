from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
ICON = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "fax2840_app_icon.png"
BUILD = ROOT / "app" / "build.gradle"


def main():
    manifest = MANIFEST.read_text(encoding="utf-8")
    assert 'android:icon="@drawable/fax2840_app_icon"' in manifest, "launcher icon is not configured"
    assert 'android:roundIcon="@drawable/fax2840_app_icon"' in manifest, "round launcher icon is not configured"
    assert ICON.exists(), "FAX-2840 custom launcher icon resource is missing"

    build = BUILD.read_text(encoding="utf-8")
    assert "versionName '1.3.2'" in build, "versionName must be 1.3.2"

    print("v1.3.2 app icon regression checks passed")


if __name__ == "__main__":
    main()
