from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


def main():
    diagnostics = JAVA / "PrintDiagnostics.java"
    assert diagnostics.exists(), "PrintDiagnostics.java must persist the last diagnostic session"

    main_activity = read("MainActivity.java")
    assert "FAX-2840直接テスト印刷" in main_activity, "direct diagnostic print button is missing"
    assert "最後の印刷診断ログ" in main_activity, "diagnostic log UI is missing"

    usb = read("Fax2840Usb.java")
    assert "readPortStatus" in usb, "USB Printer Class GET_PORT_STATUS support is missing"
    assert "getBytesWritten" in usb, "USB byte counter is missing"

    printer = read("BrotherHbpPrinter.java")
    assert "printDiagnosticPage" in printer, "direct HBP diagnostic page generator is missing"
    assert "DiagnosticSink" in printer, "HBP progress diagnostics callback is missing"

    service = read("Fax2840PrintService.java")
    assert "PrintDiagnostics" in service, "PrintService must persist diagnostic events"
    assert "getBytesWritten" in service, "PrintService must record bytes written"

    print("v0.4 diagnostics regression checks passed")


if __name__ == "__main__":
    main()
