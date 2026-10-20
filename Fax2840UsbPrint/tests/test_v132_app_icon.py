from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
ICON = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "fax2840_app_icon.png"
BUILD = ROOT / "app" / "build.gradle"


def assert_valid_png(path):
    data = path.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n"), "launcher icon is not a PNG"

    offset = 8
    saw_ihdr = False
    saw_iend = False
    while offset < len(data):
        assert offset + 12 <= len(data), "truncated PNG chunk header"
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_data = data[offset + 8:offset + 8 + length]
        crc_pos = offset + 8 + length
        assert crc_pos + 4 <= len(data), "truncated PNG chunk"
        expected_crc = struct.unpack(">I", data[crc_pos:crc_pos + 4])[0]
        actual_crc = zlib.crc32(chunk_type)
        actual_crc = zlib.crc32(chunk_data, actual_crc) & 0xffffffff
        assert actual_crc == expected_crc, f"invalid PNG CRC in {chunk_type!r}"

        if chunk_type == b"IHDR":
            saw_ihdr = True
        if chunk_type == b"IEND":
            saw_iend = True
            break
        offset = crc_pos + 4

    assert saw_ihdr and saw_iend, "PNG is missing required chunks"


def main():
    manifest = MANIFEST.read_text(encoding="utf-8")
    assert 'android:icon="@drawable/fax2840_app_icon"' in manifest, "launcher icon is not configured"
    assert 'android:roundIcon="@drawable/fax2840_app_icon"' in manifest, "round launcher icon is not configured"
    assert ICON.exists(), "FAX-2840 custom launcher icon resource is missing"
    assert_valid_png(ICON)

    build = BUILD.read_text(encoding="utf-8")
    assert "versionName '1.3.7'" in build, "versionName must be 1.3.7"

    print("v1.3.7 app icon regression checks passed")


if __name__ == "__main__":
    main()
