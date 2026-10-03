from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    helper = JAVA / "AutoPageLayout.java"
    assert helper.exists(), "AutoPageLayout.java missing"

    with tempfile.TemporaryDirectory() as tmp:
        harness = Path(tmp) / "AutoPageLayoutHarness.java"
        harness.write_text(
            """package jp.local.fax2840usb;

public final class AutoPageLayoutHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(AutoPageLayout.columnsForAuto(595f, 842f, 500f, 50f, 595f) == 1,
                "sparse A4 content must not be cropped and enlarged");

        check(AutoPageLayout.columnsForAuto(595f, 842f, 550f, 130f, 595f) >= 2,
                "wide table occupying meaningful page area should still split");

        check(AutoPageLayout.columnsForAuto(1600f, 700f, 1500f, 500f, 842f) >= 2,
                "oversized wide content should split instead of shrinking too far");

        check(!AutoPageLayout.shouldUseLandscape(595f, 842f, 500f, 50f, 1),
                "single-column sparse portrait page must keep portrait orientation");

        check(AutoPageLayout.shouldUseLandscape(842f, 595f, 700f, 300f, 1),
                "single-column landscape page must keep landscape orientation");

        float limited = AutoPageLayout.limitUpscale(9f, 4.1667f);
        check(limited <= 4.1668f,
                "automatic single-page rendering must not enlarge beyond native physical scale");
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
            ["java", "-cp", tmp, "jp.local.fax2840usb.AutoPageLayoutHarness"],
            check=True,
        )

    split = read("SplitPdfGenerator.java")
    assert "AutoPageLayout.columnsForAuto" in split, "preview generator must use auto layout policy"
    assert "preserveFullPage" in split, "preview generator must preserve sparse single-column page"

    printer = read("BrotherHbpPrinter.java")
    assert "AutoPageLayout.columnsForAuto" in printer, "direct print path must use auto layout policy"
    assert "preserveFullPage" in printer, "direct print path must preserve sparse single-column page"

    print("v1.3.1 auto layout regression checks passed")


if __name__ == "__main__":
    main()
