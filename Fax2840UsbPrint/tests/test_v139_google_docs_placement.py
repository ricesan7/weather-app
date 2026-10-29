from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    service = read("Fax2840PrintService.java")
    assert "new PrintAttributes.Margins(167, 167, 167, 167)" in service, (
        "print service must advertise the FAX-2840's real ~12pt margins"
    )
    assert "preparedPreview ? PageSplitSettings.MODE_FIT_PAGE" in service
    assert ": PageSplitSettings.MODE_AUTO_CONTENT" in service, (
        "direct app printing must not inherit a manual table split mode"
    )

    helper = JAVA / "DocumentPagePlacement.java"
    assert helper.exists(), "DocumentPagePlacement.java missing"

    with tempfile.TemporaryDirectory() as tmp:
        harness = Path(tmp) / "DocumentPagePlacementHarness.java"
        harness.write_text(
            """package jp.local.fax2840usb;

public final class DocumentPagePlacementHarness {
    private static void near(float actual, float expected, float tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + " actual=" + actual + " expected=" + expected);
        }
    }

    public static void main(String[] args) {
        DocumentPagePlacement.Result r =
                DocumentPagePlacement.fit(595f, 842f, 4960f, 7016f);

        near(r.offsetX, 0f, 2f, "A4 document must not receive an extra left margin");
        near(r.offsetY, 0f, 2f, "A4 document must not receive an extra top margin");
        near(r.renderedWidth, 4958.4f, 3f, "A4 document width must map to the sheet");
        near(r.renderedHeight, 7016f, 3f, "A4 document height must map to the sheet");
    }
}
""",
            encoding="utf-8",
        )
        subprocess.run(
            ["javac", "-d", tmp, str(helper), str(harness)],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", tmp, "jp.local.fax2840usb.DocumentPagePlacementHarness"],
            check=True,
        )

    printer = read("BrotherHbpPrinter.java")
    assert "DocumentPagePlacement.fit" in printer, (
        "direct document path must preserve whole-sheet geometry"
    )
    assert "preserveSheetGeometry" in printer, (
        "document placement must be isolated from the existing PDF fit-page path"
    )

    print("v1.3.9 Google Docs direct-print placement regression checks passed")


if __name__ == "__main__":
    main()
