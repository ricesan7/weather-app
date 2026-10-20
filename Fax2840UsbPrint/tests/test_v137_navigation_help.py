from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "jp" / "local" / "fax2840usb"

def main():
    preview = (JAVA / "SharedPdfPreviewActivity.java").read_text(encoding="utf-8")
    assert "トップへ戻る" in preview
    assert "FLAG_ACTIVITY_CLEAR_TOP" in preview

    main_activity = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
    assert "PDF / Excel / CSVを開いて印刷" in main_activity
    assert "Excel（.xlsx）とCSVは直接読み込み" in main_activity

    print("v1.3.7 navigation and spreadsheet help checks passed")

if __name__ == "__main__":
    main()
