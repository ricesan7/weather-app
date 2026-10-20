from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def main():
    settings = (JAVA / "PrintQualitySettings.java").read_text(encoding="utf-8")
    assert "MAX_DENSITY = 15" in settings, "density range must extend to 15"

    curve = JAVA / "PrintDensityCurve.java"
    assert curve.exists(), "PrintDensityCurve.java missing"

    with tempfile.TemporaryDirectory() as tmp:
        harness = Path(tmp) / "DensityHarness.java"
        harness.write_text(
            """
package jp.local.fax2840usb;

public final class DensityHarness {
    public static void main(String[] args) {
        int normal = PrintDensityCurve.adjustedLuminance(235, 5);
        int density10 = PrintDensityCurve.adjustedLuminance(235, 10);
        int density12 = PrintDensityCurve.adjustedLuminance(235, 12);
        int density15 = PrintDensityCurve.adjustedLuminance(235, 15);

        if (!(normal > density10)) throw new AssertionError("10 must be darker than 5");
        if (!(density10 > density12)) throw new AssertionError("12 must be darker than 10");
        if (!(density12 > density15)) throw new AssertionError("15 must be darker than 12");
        if (density15 > 100) throw new AssertionError("15 must strongly preserve pale colored cells");

        if (PrintDensityCurve.adjustedLuminance(255, 15) != 255) {
            throw new AssertionError("pure white must remain white");
        }
        if (PrintDensityCurve.adjustedLuminance(20, 15) != 20) {
            throw new AssertionError("solid black/dark text must not be crushed further");
        }
    }
}
""",
            encoding="utf-8",
        )
        subprocess.run(
            [
                "javac", "-d", tmp,
                str(curve),
                str(harness),
            ],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", tmp, "jp.local.fax2840usb.DensityHarness"],
            check=True,
        )

    main_activity = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
    assert '15 = 色付きセル優先' in main_activity, "density UI must explain level 15"

    printer = (JAVA / "BrotherHbpPrinter.java").read_text(encoding="utf-8")
    assert "PrintDensityCurve.adjustedLuminance" in printer, "printer must use the stronger density curve"

    print("v1.3.5 extended density curve regression checks passed")


if __name__ == "__main__":
    main()
