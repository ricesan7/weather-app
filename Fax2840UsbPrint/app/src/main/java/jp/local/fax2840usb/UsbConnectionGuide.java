package jp.local.fax2840usb;

final class UsbConnectionGuide {
    static final String TITLE = "USB接続ガイド";
    static final String SUMMARY =
            "FAX-2840との接続には、ほとんどのAndroid端末でUSB OTG/USB Host対応の"
            + "変換アダプターまたは変換ケーブルが必要です。";

    static final String DETAILS =
            "【必要なもの】\n"
            + "・FAX-2840側: USB Type-B（プリンター側の四角い端子）\n"
            + "・一般的なプリンター用USBケーブル: USB Type-A ⇔ USB Type-B\n"
            + "・Android側: USB OTG / USB Host対応の変換アダプター\n"
            + "  例: USB Type-C → USB Type-Aメス OTG変換アダプター\n"
            + "・または、データ通信対応のUSB Type-C ⇔ USB Type-Bケーブルでも接続できます。\n"
            + "※充電専用ケーブルやOTG非対応アダプターでは認識できません。\n\n"
            + "【Android側の設定】\n"
            + "1. FAX-2840の電源を入れます。\n"
            + "2. 変換アダプターとUSBケーブルでAndroid端末とFAX-2840を接続します。\n"
            + "3. 端末に「OTG」「USB OTG」「USB Host」などの設定がある場合は有効にします。\n"
            + "   設定場所はメーカーにより異なり、「設定 → 接続設定」「その他の接続」などにあります。\n"
            + "   端末によっては設定項目がなく、接続すると自動でUSB Hostが有効になります。\n"
            + "4. USBアクセス許可の画面が表示されたら、このアプリへのアクセスを許可します。\n"
            + "5. アプリ上部のプリンター状態が「接続済み」になれば準備完了です。\n\n"
            + "【認識しない場合】\n"
            + "・OTG / USB Host設定を一度OFF→ONにします。\n"
            + "・FAX-2840の電源を入れた状態でUSBを抜き差しします。\n"
            + "・「詳細設定・診断」→「FAX-2840を再チェック」を押します。\n"
            + "・変換アダプターとケーブルがデータ通信対応か確認します。\n"
            + "・一部のAndroid端末はUSB Hostに対応していない場合があります。\n\n"
            + "【ケーブルについて】\n"
            + "FAX-2840はUSB 2.0接続です。安定性のため、できるだけ短いプリンター用"
            + "USB Type-A ⇔ USB Type-Bケーブルを使用し、USBハブを挟まず直接接続してください。";

    private UsbConnectionGuide() {}
}
