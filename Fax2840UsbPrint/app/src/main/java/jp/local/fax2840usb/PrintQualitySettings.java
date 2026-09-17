package jp.local.fax2840usb;

import android.content.Context;
import android.content.SharedPreferences;

final class PrintQualitySettings {
    static final int MIN_DENSITY = 1;
    static final int MAX_DENSITY = 10;
    static final int DEFAULT_DENSITY = 5;

    private static final String PREFS = "fax2840_print_quality";
    private static final String KEY_DENSITY = "black_density";

    private PrintQualitySettings() {}

    static int getDensity(Context context) {
        if (context == null) return DEFAULT_DENSITY;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return clamp(prefs.getInt(KEY_DENSITY, DEFAULT_DENSITY));
    }

    static void setDensity(Context context, int density) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_DENSITY, clamp(density))
                .apply();
    }

    static int clamp(int density) {
        return Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, density));
    }
}
