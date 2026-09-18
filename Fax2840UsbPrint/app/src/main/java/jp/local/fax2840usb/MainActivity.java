package jp.local.fax2840usb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.IOException;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "jp.local.fax2840usb.USB_PERMISSION";
    private TextView status;
    private TextView diagnosticLog;
    private UsbManager usbManager;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice device = getUsbDeviceExtra(intent);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && device != null) inspectDevice(device);
            else status.setText("USBアクセスが許可されていません。\n再接続して権限を許可してください。");
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        buildUi();
        registerPermissionReceiver();
        handleUsbIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleUsbIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDiagnosticLog();
        UsbDevice device = Fax2840Usb.findAttached(this);
        if (device != null) ensurePermissionAndInspect(device);
        else status.setText("FAX-2840を待機中です。\nUSB OTG/Host接続でプリンターを接続してください。");
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(permissionReceiver); } catch (IllegalArgumentException ignored) {}
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad,pad,pad,pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Brother FAX-2840 USB Print v1.0");
        title.setTextSize(22f);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextSize(16f);
        status.setPadding(0,dp(20),0,dp(16));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView densityTitle = new TextView(this);
        densityTitle.setText("黒濃度");
        densityTitle.setTextSize(18f);
        densityTitle.setPadding(0,dp(4),0,0);
        root.addView(densityTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView densityValue = new TextView(this);
        densityValue.setTextSize(15f);
        int savedDensity = PrintQualitySettings.getDensity(this);
        densityValue.setText(formatDensity(savedDensity));
        root.addView(densityValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar densityBar = new SeekBar(this);
        densityBar.setMax(PrintQualitySettings.MAX_DENSITY - PrintQualitySettings.MIN_DENSITY);
        densityBar.setProgress(savedDensity - PrintQualitySettings.MIN_DENSITY);
        densityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + PrintQualitySettings.MIN_DENSITY;
                densityValue.setText(formatDensity(value));
                if (fromUser) PrintQualitySettings.setDensity(MainActivity.this, value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(densityBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView densityHint = new TextView(this);
        densityHint.setText("1 = 薄い   5 = 標準   10 = 濃い\n黒文字は読みやすさを保ち、画像・イラスト・グレーの濃さを主に調整します。");
        densityHint.setTextSize(13f);
        densityHint.setPadding(0,0,0,dp(12));
        root.addView(densityHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView splitTitle = new TextView(this);
        splitTitle.setText("ページ分割方式");
        splitTitle.setTextSize(18f);
        splitTitle.setPadding(0,dp(8),0,dp(4));
        root.addView(splitTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Spinner splitModeSpinner = new Spinner(this);
        ArrayAdapter<String> splitAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                PageSplitSettings.MODE_LABELS);
        splitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        splitModeSpinner.setAdapter(splitAdapter);

        int savedMode = PageSplitSettings.getMode(this);
        splitModeSpinner.setSelection(PageSplitSettings.indexOfMode(savedMode));
        splitModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                int safe = Math.max(0, Math.min(PageSplitSettings.MODES.length - 1, position));
                PageSplitSettings.setMode(MainActivity.this, PageSplitSettings.MODES[safe]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(splitModeSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView splitHint = new TextView(this);
        splitHint.setText("自動（表向け）: 白紙余白を除外し、表だけを読みやすい枚数へ自動分割します。\n横2〜4分割: 表の実データ範囲を指定枚数に分割します。\n1ページに収める: 通常のPDF印刷です。");
        splitHint.setTextSize(13f);
        splitHint.setPadding(0,0,0,dp(12));
        root.addView(splitHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button directTest = new Button(this);
        directTest.setText("FAX-2840直接テスト印刷");
        directTest.setOnClickListener(v -> runDirectTest());
        root.addView(directTest, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button printSettings = new Button(this);
        printSettings.setText("Androidの印刷設定を開く");
        printSettings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS)); }
            catch (RuntimeException e) { status.setText("印刷設定を開けませんでした: " + e.getMessage()); }
        });
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.topMargin = dp(10);
        root.addView(printSettings,p1);

        Button recheck = new Button(this);
        recheck.setText("FAX-2840を再チェック");
        recheck.setOnClickListener(v -> {
            UsbDevice d = Fax2840Usb.findAttached(this);
            if (d == null) status.setText("FAX-2840がUSBで検出されていません。");
            else ensurePermissionAndInspect(d);
        });
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p2.topMargin = dp(10);
        root.addView(recheck,p2);

        TextView logTitle = new TextView(this);
        logTitle.setText("最後の印刷診断ログ");
        logTitle.setTextSize(18f);
        logTitle.setPadding(0,dp(20),0,dp(8));
        root.addView(logTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        diagnosticLog = new TextView(this);
        diagnosticLog.setTextSize(13f);
        diagnosticLog.setTextIsSelectable(true);
        diagnosticLog.setPadding(dp(8),dp(8),dp(8),dp(8));
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(diagnosticLog, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        root.addView(logScroll, logParams);

        Button refreshLog = new Button(this);
        refreshLog.setText("診断ログを更新");
        refreshLog.setOnClickListener(v -> refreshDiagnosticLog());
        root.addView(refreshLog, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView page = new ScrollView(this);
        page.addView(root);
        setContentView(page);
    }

    private void runDirectTest() {
        UsbDevice device = Fax2840Usb.findAttached(this);
        if (device == null) {
            status.setText("FAX-2840がUSBで検出されていません。");
            return;
        }
        if (usbManager == null || !usbManager.hasPermission(device)) {
            ensurePermissionAndInspect(device);
            status.setText("USBアクセス許可後に、もう一度直接テスト印刷を押してください。");
            return;
        }
        status.setText("FAX-2840へ直接テストデータを送信中…");
        new Thread(() -> {
            PrintDiagnostics diag = new PrintDiagnostics(this, "DIRECT TEST");
            try (Fax2840Usb usb = Fax2840Usb.open(this, device)) {
                diag.add(String.format(Locale.US, "USB %04X:%04X", device.getVendorId(), device.getProductId()));
                diag.add("port(before)=" + Fax2840Usb.describePortStatus(usb.readPortStatus()));
                diag.add("IEEE1284=" + usb.readIeee1284DeviceId());
                BrotherHbpPrinter.printDiagnosticPage(usb, diag::add);
                diag.add("port(after)=" + Fax2840Usb.describePortStatus(usb.readPortStatus()));
                diag.add("RESULT=USB_SEND_COMPLETE bytes=" + usb.getBytesWritten());
                runOnUiThread(() -> status.setText("直接テストデータのUSB送信は完了しました。\nFAX本体が紙を出すか確認してください。"));
            } catch (IOException | RuntimeException e) {
                diag.add("RESULT=ERROR " + e.getClass().getSimpleName() + ": " + safeMessage(e));
                runOnUiThread(() -> status.setText("直接テスト印刷エラー: " + safeMessage(e)));
            } finally {
                runOnUiThread(this::refreshDiagnosticLog);
            }
        }, "fax2840-direct-test").start();
    }

    private void refreshDiagnosticLog() {
        if (diagnosticLog != null) diagnosticLog.setText(PrintDiagnostics.last(this));
    }

    private void registerPermissionReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(permissionReceiver, filter);
    }

    private void handleUsbIntent(Intent intent) {
        if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            UsbDevice d = getUsbDeviceExtra(intent);
            if (Fax2840Usb.isCandidate(d)) ensurePermissionAndInspect(d);
        }
    }

    private void ensurePermissionAndInspect(UsbDevice device) {
        if (usbManager == null || device == null) return;
        if (usbManager.hasPermission(device)) {
            inspectDevice(device);
            return;
        }
        Intent permissionIntent = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);
        usbManager.requestPermission(device, pi);
        status.setText("FAX-2840を検出しました。\nUSBアクセス許可を待っています…");
    }

    private void inspectDevice(UsbDevice device) {
        new Thread(() -> {
            String message;
            try (Fax2840Usb transport = Fax2840Usb.open(this, device)) {
                String id = transport.readIeee1284DeviceId();
                boolean ok = transport.confirmsFax2840();
                String port = Fax2840Usb.describePortStatus(transport.readPortStatus());
                message = ok
                        ? "FAX-2840を認識しました。\n\n" + String.format(Locale.US,"USB VID:PID = %04X:%04X\n",device.getVendorId(),device.getProductId())
                        + (id.isEmpty()?"IEEE-1284 ID: 取得できませんでした":"IEEE-1284 ID:\n"+id)
                        + "\nPORT STATUS: " + port
                        + "\n\n診断時は『FAX-2840直接テスト印刷』を押してください。"
                        : "USB機器は開けましたがFAX-2840として確認できませんでした。\n" + id;
            } catch (IOException e) {
                message = "USB診断エラー: " + e.getMessage();
            }
            final String text = message;
            runOnUiThread(() -> status.setText(text));
        }, "fax2840-diagnostic").start();
    }

    @SuppressWarnings("deprecation") private static UsbDevice getUsbDeviceExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private static String formatDensity(int density) {
        int d = PrintQualitySettings.clamp(density);
        String label = d == PrintQualitySettings.DEFAULT_DENSITY ? "（標準）" : "";
        return "黒濃度: " + d + label;
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
