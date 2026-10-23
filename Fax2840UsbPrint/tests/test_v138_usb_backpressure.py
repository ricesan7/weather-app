from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"


def main():
    usb = (JAVA / "Fax2840Usb.java").read_text(encoding="utf-8")

    timeout = re.search(r"USB_WRITE_TIMEOUT_MS\s*=\s*(\d+)", usb)
    retries = re.search(r"MAX_TIMEOUT_RETRIES\s*=\s*(\d+)", usb)
    assert timeout, "USB write timeout constant missing"
    assert retries, "USB timeout retry constant missing"
    assert int(timeout.group(1)) >= 30000, "printer backpressure must allow at least 30 seconds per USB write"
    assert int(retries.group(1)) >= 2, "transient USB timeout must be retried"

    assert "if (sent <= 0)" in usb, "USB timeout/backpressure must be retried before failing"
    assert "timeoutRetries++" in usb, "USB timeout retries must be counted"
    assert "continue;" in usb, "transient timeout must continue instead of failing immediately"

    print("v1.3.8 USB backpressure regression checks passed")


if __name__ == "__main__":
    main()
