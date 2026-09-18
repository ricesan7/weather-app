package jp.local.fax2840usb;

import android.content.Context;
import android.content.SharedPreferences;

final class PrintOrientationSettings {
    static final int MODE_AUTO = 0;
    static final int MODE_PORTRAIT = 1;
    static final int MODE_LANDSCAPE = 2;
    static final int DEFAULT_MODE = MODE_AUTO;

    static final int[] MODES = {
            MODE_AUTO,
            MODE_PORTRAIT,
            MODE_LANDSCAPE
    };

    static final String[] MODE_LABELS = {
            "自動",
            "縦向き",
            "横向き"
    };

    private static final String PREFS = "fax2840_print_orientation";
    private static final String KEY_MODE = "orientation_mode";

    private PrintOrientationSettings() {}

    static int getMode(Context context) {
        if (context == null) return DEFAULT_MODE;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return normalizeMode(prefs.getInt(KEY_MODE, DEFAULT_MODE));
    }

    static void setMode(Context context, int mode) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MODE, normalizeMode(mode))
                .apply();
    }

    static int normalizeMode(int mode) {
        for (int value : MODES) {
            if (value == mode) return value;
        }
        return DEFAULT_MODE;
    }

    static int indexOfMode(int mode) {
        int normalized = normalizeMode(mode);
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i] == normalized) return i;
        }
        return 0;
    }

    static String labelForMode(int mode) {
        return MODE_LABELS[indexOfMode(mode)];
    }
}
