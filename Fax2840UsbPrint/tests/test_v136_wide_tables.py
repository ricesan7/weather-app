from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    helper = JAVA / "AutoPageLayout.java"
    settings = read("PageSplitSettings.java")

    assert "MODE_HORIZONTAL_6" in settings, "6-way horizontal split mode is missing"
    assert "MODE_HORIZONTAL_8" in settings, "8-way horizontal split mode is missing"
    assert '"横6分割"' in settings and '"横8分割"' in settings, "6/8 split labels are missing"

    with tempfile.TemporaryDirectory() as tmp:
        harness = Path(tmp) / "WideTableHarness.java"
        harness.write_text(
            """package jp.local.fax2840usb;

public final class WideTableHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int cols = AutoPageLayout.columnsForAuto(
                6400f, 1200f, 6200f, 1000f, 842f);
        check(cols >= 6,
                "very wide multi-column sheets must split into at least 6 readable pages");

        int capped = AutoPageLayout.columnsForAuto(
                12000f, 1200f, 11800f, 1000f, 842f);
        check(capped == 8,
                "automatic wide-table split should allow up to 8 horizontal pages");
    }
}
""",
            encoding="utf-8",
        )
        subprocess.run(["javac", "-d", tmp, str(helper), str(harness)], check=True)
        subprocess.run(["java", "-cp", tmp, "jp.local.fax2840usb.WideTableHarness"], check=True)

    split = read("SplitPdfGenerator.java")
    printer = read("BrotherHbpPrinter.java")
    for token in ("MODE_HORIZONTAL_6", "MODE_HORIZONTAL_8"):
        assert token in split, f"{token} missing from preview splitter"
        assert token in printer, f"{token} missing from direct print splitter"

    main_activity = read("MainActivity.java")
    assert "列数が多い表" in main_activity, "UI must explain wide-table split modes"

    print("v1.3.6 wide-table readability regression checks passed")


if __name__ == "__main__":
    main()
