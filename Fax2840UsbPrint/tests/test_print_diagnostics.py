from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/jp/local/fax2840usb/Fax2840PrintService.java"
MAIN = ROOT / "app/src/main/java/jp/local/fax2840usb/MainActivity.java"
DIAG = ROOT / "app/src/main/java/jp/local/fax2840usb/PrintDiagnostics.java"


def require(text: str, needle: str, where: str):
    if needle not in text:
        raise AssertionError(f"missing {needle!r} in {where}")


def main():
    if not DIAG.exists():
        raise AssertionError("PrintDiagnostics.java is missing")

    diag = DIAG.read_text(encoding="utf-8")
    service = SERVICE.read_text(encoding="utf-8")
    main_activity = MAIN.read_text(encoding="utf-8")

    for stage in [
        'JOB_QUEUED',
        'JOB_STARTED',
        'USB_OPEN',
        'HBP_BEGIN',
        'SUCCESS',
        'FAILURE',
    ]:
        require(service, stage, "Fax2840PrintService.java")

    require(service, "PrintDiagnostics.record", "Fax2840PrintService.java")
    require(diag, "SharedPreferences", "PrintDiagnostics.java")
    require(diag, "read", "PrintDiagnostics.java")
    require(main_activity, "最終印刷診断", "MainActivity.java")
    require(main_activity, "PrintDiagnostics.read", "MainActivity.java")


if __name__ == "__main__":
    main()
