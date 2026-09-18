package jp.local.fax2840usb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
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
    private static final int REQUEST_OPEN_PDF = 1001;

    private TextView status;
    private TextView connectionBadge;
    private TextView diagnosticLog;
    private LinearLayout advancedContainer;
    private UsbManager usbManager;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice device = getUsbDeviceExtra(intent);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && device != null) inspectDevice(device);
            else {
                AppUi.styleConnectionBadge(connectionBadge, "未接続", false);
                status.setText("USBアクセスが許可されていません。\n再接続して権限を許可してください。");
            }
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
        if (device != null) {
            AppUi.styleConnectionBadge(connectionBadge, "確認中", false);
            ensurePermissionAndInspect(device);
        } else {
            AppUi.styleConnectionBadge(connectionBadge, "未接続", false);
            status.setText("FAX-2840を待機中です。\nUSB OTG/Host接続でプリンターを接続してください。");
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(permissionReceiver); } catch (IllegalArgumentException ignored) {}
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_PDF || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        Intent preview = new Intent(this, SharedPdfPreviewActivity.class);
        preview.setData(uri);
        preview.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(preview);
    }

    private void buildUi() {
        AppUi.configureSystemBars(this);

        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(18), pad, dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(AppUi.COLOR_BACKGROUND);

        TextView title = new TextView(this);
        title.setText("FAX-2840 USB Print");
        AppUi.styleTitle(title);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Brother FAX-2840  •  USB印刷  •  v1.2.1");
        AppUi.styleBody(subtitle);
        subtitle.setPadding(0, dp(2), 0, dp(4));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout connectionCard = AppUi.card(this);
        LinearLayout connectionHeader = new LinearLayout(this);
        connectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        connectionHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView connectionTitle = new TextView(this);
        connectionTitle.setText("プリンター状態");
        AppUi.styleSectionTitle(connectionTitle);
        LinearLayout.LayoutParams connectionTitleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        connectionHeader.addView(connectionTitle, connectionTitleParams);

        connectionBadge = new TextView(this);
        AppUi.styleConnectionBadge(connectionBadge, "確認中", false);
        connectionHeader.addView(connectionBadge);
        connectionCard.addView(connectionHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        AppUi.styleBody(status);
        status.setPadding(0, dp(10), 0, 0);
        connectionCard.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AppUi.addCard(root, connectionCard);

        LinearLayout actionCard = AppUi.card(this);

        TextView actionTitle = new TextView(this);
        actionTitle.setText("印刷");
        AppUi.styleSectionTitle(actionTitle);
        actionCard.addView(actionTitle);

        TextView actionHint = new TextView(this);
        actionHint.setText("PDFを選ぶと、分割プレビューを確認してから印刷できます。GoogleスプレッドシートからPDF共有した場合も同じ画面に入ります。");
        AppUi.styleBody(actionHint);
        actionHint.setPadding(0, dp(6), 0, dp(12));
        actionCard.addView(actionHint);

        Button openPdf = new Button(this);
        openPdf.setText("PDFを開いて印刷");
        AppUi.stylePrimaryButton(openPdf);
        openPdf.setOnClickListener(v -> openPdfPicker());
        actionCard.addView(openPdf, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AppUi.addCard(root, actionCard);

        LinearLayout settingsCard = AppUi.card(this);

        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("印刷設定");
        AppUi.styleSectionTitle(settingsTitle);
        settingsCard.addView(settingsTitle);

        TextView densityTitle = new TextView(this);
        densityTitle.setText("黒濃度");
        densityTitle.setTextColor(AppUi.COLOR_TEXT);
        densityTitle.setTextSize(15f);
        densityTitle.setPadding(0, dp(14), 0, 0);
        settingsCard.addView(densityTitle);

        TextView densityValue = new TextView(this);
        int savedDensity = PrintQualitySettings.getDensity(this);
        densityValue.setText(formatDensity(savedDensity));
        AppUi.styleBody(densityValue);
        settingsCard.addView(densityValue);

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
        settingsCard.addView(densityBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView densityHint = new TextView(this);
        densityHint.setText("1 = 薄い   5 = 標準   10 = 濃い");
        AppUi.styleBody(densityHint);
        settingsCard.addView(densityHint);

        TextView splitTitle = new TextView(this);
        splitTitle.setText("ページ分割方式");
        splitTitle.setTextColor(AppUi.COLOR_TEXT);
        splitTitle.setTextSize(15f);
        splitTitle.setPadding(0, dp(16), 0, dp(4));
        settingsCard.addView(splitTitle);

        Spinner splitModeSpinner = new Spinner(this);
        ArrayAdapter<String> splitAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, PageSplitSettings.MODE_LABELS);
        splitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        splitModeSpinner.setAdapter(splitAdapter);

        int savedMode = PageSplitSettings.getMode(this);
        splitModeSpinner.setSelection(PageSplitSettings.indexOfMode(savedMode));
        splitModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int safe = Math.max(0, Math.min(PageSplitSettings.MODES.length - 1, position));
                PageSplitSettings.setMode(MainActivity.this, PageSplitSettings.MODES[safe]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        settingsCard.addView(splitModeSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView splitHint = new TextView(this);
        splitHint.setText("Googleスプレッドシートは「自動（表向け）」がおすすめです。");
        AppUi.styleBody(splitHint);
        splitHint.setPadding(0, dp(2), 0, 0);
        settingsCard.addView(splitHint);

        TextView orientationTitle = new TextView(this);
        orientationTitle.setText("印刷向き");
        orientationTitle.setTextColor(AppUi.COLOR_TEXT);
        orientationTitle.setTextSize(15f);
        orientationTitle.setPadding(0, dp(16), 0, dp(4));
        settingsCard.addView(orientationTitle);

        Spinner orientationSpinner = new Spinner(this);
        ArrayAdapter<String> orientationAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, PrintOrientationSettings.MODE_LABELS);
        orientationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        orientationSpinner.setAdapter(orientationAdapter);

        int savedOrientation = PrintOrientationSettings.getMode(this);
        orientationSpinner.setSelection(PrintOrientationSettings.indexOfMode(savedOrientation));
        orientationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int safe = Math.max(0, Math.min(
                        PrintOrientationSettings.MODES.length - 1, position));
                PrintOrientationSettings.setMode(
                        MainActivity.this, PrintOrientationSettings.MODES[safe]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        settingsCard.addView(orientationSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView orientationHint = new TextView(this);
        orientationHint.setText("横長の表では「横向き」を選択すると、プレビューPDF自体をA4横向きで作成します。");
        AppUi.styleBody(orientationHint);
        settingsCard.addView(orientationHint);

        AppUi.addCard(root, settingsCard);

        Button advancedToggle = new Button(this);
        advancedToggle.setText("詳細設定・診断");
        AppUi.styleSecondaryButton(advancedToggle);
        LinearLayout.LayoutParams advancedToggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        advancedToggleParams.topMargin = dp(12);
        root.addView(advancedToggle, advancedToggleParams);

        advancedContainer = new LinearLayout(this);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);
        root.addView(advancedContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout advancedCard = AppUi.card(this);

        TextView advancedTitle = new TextView(this);
        advancedTitle.setText("詳細設定・診断");
        AppUi.styleSectionTitle(advancedTitle);
        advancedCard.addView(advancedTitle);

        TextView advancedHelp = new TextView(this);
        advancedHelp.setText("通常は操作不要です。接続確認やトラブル時に使用します。");
        AppUi.styleBody(advancedHelp);
        advancedHelp.setPadding(0, dp(4), 0, dp(8));
        advancedCard.addView(advancedHelp);

        Button recheck = new Button(this);
        recheck.setText("FAX-2840を再チェック");
        AppUi.styleSecondaryButton(recheck);
        recheck.setOnClickListener(v -> {
            UsbDevice d = Fax2840Usb.findAttached(this);
            if (d == null) {
                AppUi.styleConnectionBadge(connectionBadge, "未接続", false);
                status.setText("FAX-2840がUSBで検出されていません。");
            } else ensurePermissionAndInspect(d);
        });
        advancedCard.addView(recheck, buttonParams(0));

        Button directTest = new Button(this);
        directTest.setText("FAX-2840直接テスト印刷");
        AppUi.styleSecondaryButton(directTest);
        directTest.setOnClickListener(v -> runDirectTest());
        advancedCard.addView(directTest, buttonParams(8));

        Button printSettings = new Button(this);
        printSettings.setText("Androidの印刷設定を開く");
        AppUi.styleSecondaryButton(printSettings);
        printSettings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS)); }
            catch (RuntimeException e) { status.setText("印刷設定を開けませんでした: " + e.getMessage()); }
        });
        advancedCard.addView(printSettings, buttonParams(8));

        TextView logTitle = new TextView(this);
        logTitle.setText("最後の印刷診断ログ");
        logTitle.setTextColor(AppUi.COLOR_TEXT);
        logTitle.setTextSize(15f);
        logTitle.setPadding(0, dp(16), 0, dp(6));
        advancedCard.addView(logTitle);

        diagnosticLog = new TextView(this);
        diagnosticLog.setTextSize(12f);
        diagnosticLog.setTextColor(AppUi.COLOR_TEXT);
        diagnosticLog.setTextIsSelectable(true);
        diagnosticLog.setPadding(dp(10), dp(10), dp(10), dp(10));
        diagnosticLog.setBackground(AppUi.rounded(this,
                AppUi.COLOR_BACKGROUND, 10, AppUi.COLOR_BORDER, 1));

        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(diagnosticLog, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        advancedCard.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        Button refreshLog = new Button(this);
        refreshLog.setText("診断ログを更新");
        AppUi.styleSecondaryButton(refreshLog);
        refreshLog.setOnClickListener(v -> refreshDiagnosticLog());
        advancedCard.addView(refreshLog, buttonParams(8));

        AppUi.addCard(advancedContainer, advancedCard);

        advancedToggle.setOnClickListener(v -> {
            boolean open = advancedContainer.getVisibility() == View.VISIBLE;
            advancedContainer.setVisibility(open ? View.GONE : View.VISIBLE);
            advancedToggle.setText(open ? "詳細設定・診断" : "詳細設定・診断を閉じる");
        });

        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(AppUi.COLOR_BACKGROUND);
        page.addView(root);
        setContentView(page);
        AppUi.applySystemBarInsets(root, 18, 18, 18, 28);
    }

    private LinearLayout.LayoutParams buttonParams(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        @SuppressWarnings("deprecation")
        Intent picker = intent;
        startActivityForResult(picker, REQUEST_OPEN_PDF);
    }

    private void runDirectTest() {
        UsbDevice device = Fax2840Usb.findAttached(this);
        if (device == null) {
            AppUi.styleConnectionBadge(connectionBadge, "未接続", false);
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
        AppUi.styleConnectionBadge(connectionBadge, "権限確認中", false);
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
            boolean connected = false;
            try (Fax2840Usb transport = Fax2840Usb.open(this, device)) {
                String id = transport.readIeee1284DeviceId();
                boolean ok = transport.confirmsFax2840();
                connected = ok;
                String port = Fax2840Usb.describePortStatus(transport.readPortStatus());
                message = ok
                        ? "FAX-2840を認識しました。\n"
                        + String.format(Locale.US, "USB VID:PID = %04X:%04X\n", device.getVendorId(), device.getProductId())
                        + (id.isEmpty() ? "IEEE-1284 ID: 取得できませんでした" : "IEEE-1284 ID:\n" + id)
                        + "\nPORT STATUS: " + port
                        : "USB機器は開けましたがFAX-2840として確認できませんでした。\n" + id;
            } catch (IOException e) {
                message = "USB診断エラー: " + e.getMessage();
            }
            final String text = message;
            final boolean isConnected = connected;
            runOnUiThread(() -> {
                AppUi.styleConnectionBadge(connectionBadge,
                        isConnected ? "接続済み" : "確認が必要", isConnected);
                status.setText(text);
            });
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
        return AppUi.dp(this, value);
    }
}
