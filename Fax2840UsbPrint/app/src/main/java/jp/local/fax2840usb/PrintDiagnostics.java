package jp.local.fax2840usb;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class PrintDiagnostics {
    private static final String PREFS = "fax2840_diagnostics";
    private static final String KEY_LOG = "last_log";
    private final Context context;
    private final StringBuilder log = new StringBuilder();

    PrintDiagnostics(Context context, String title) {
        this.context = context.getApplicationContext();
        log.append("=== ").append(title).append(" ===\n");
        log.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(new Date())).append('\n');
        save();
    }

    synchronized void add(String message) {
        log.append(message).append('\n');
        save();
    }

    synchronized String text() { return log.toString(); }

    private void save() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LOG, log.toString()).apply();
    }

    static String last(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString(KEY_LOG, "診断ログはまだありません。");
    }
}
