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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "jp.local.fax2840usb.USB_PERMISSION";
    private TextView status;
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
        buildUi(); registerPermissionReceiver(); handleUsbIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleUsbIntent(intent); }
    @Override protected void onResume() {
        super.onResume();
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
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(pad,pad,pad,pad); root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this); title.setText("Brother FAX-2840 USB Print"); title.setTextSize(22f);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        status = new TextView(this); status.setTextSize(16f); status.setPadding(0,dp(24),0,dp(24));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button printSettings = new Button(this); printSettings.setText("Androidの印刷設定を開く");
        printSettings.setOnClickListener(v -> { try { startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS)); } catch (RuntimeException e) { status.setText("印刷設定を開けませんでした: " + e.getMessage()); } });
        root.addView(printSettings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button recheck = new Button(this); recheck.setText("FAX-2840を再チェック");
        recheck.setOnClickListener(v -> { UsbDevice d = Fax2840Usb.findAttached(this); if (d == null) status.setText("FAX-2840がUSBで検出されていません。"); else ensurePermissionAndInspect(d); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin = dp(12); root.addView(recheck,p);
        setContentView(root);
    }
    private void registerPermissionReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(permissionReceiver, filter);
    }
    private void handleUsbIntent(Intent intent) {
        if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            UsbDevice d = getUsbDeviceExtra(intent); if (Fax2840Usb.isCandidate(d)) ensurePermissionAndInspect(d);
        }
    }
    private void ensurePermissionAndInspect(UsbDevice device) {
        if (usbManager == null || device == null) return;
        if (usbManager.hasPermission(device)) { inspectDevice(device); return; }
        Intent permissionIntent = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT; if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);
        usbManager.requestPermission(device, pi);
        status.setText("FAX-2840を検出しました。\nUSBアクセス許可を待っています…");
    }
    private void inspectDevice(UsbDevice device) {
        new Thread(() -> {
            String message;
            try (Fax2840Usb transport = Fax2840Usb.open(this, device)) {
                String id = transport.readIeee1284DeviceId(); boolean ok = transport.confirmsFax2840();
                message = ok ? "FAX-2840を認識しました。\n\n" + String.format(Locale.US,"USB VID:PID = %04X:%04X\n",device.getVendorId(),device.getProductId()) + (id.isEmpty()?"IEEE-1284 ID: 取得できませんでした":"IEEE-1284 ID:\n"+id) + "\n\n次に『Androidの印刷設定を開く』から\nFAX-2840 USB Print を有効にしてください。" : "USB機器は開けましたがFAX-2840として確認できませんでした。\n" + id;
            } catch (IOException e) { message = "USB診断エラー: " + e.getMessage(); }
            final String text = message; runOnUiThread(() -> status.setText(text));
        }, "fax2840-diagnostic").start();
    }
    @SuppressWarnings("deprecation") private static UsbDevice getUsbDeviceExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
