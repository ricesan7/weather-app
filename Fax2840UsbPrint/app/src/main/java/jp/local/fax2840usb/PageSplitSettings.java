package jp.local.fax2840usb;

import android.content.Context;
import android.content.SharedPreferences;

final class PageSplitSettings {
    // v0.8 legacy zoom presets are kept so older preferences remain readable.
    static final int[] ZOOM_LEVELS = {100, 150, 200, 250, 300};
    static final int DEFAULT_ZOOM_PERCENT = 100;

    static final int MODE_FIT_PAGE = 0;
    static final int MODE_AUTO_CONTENT = 1;
    static final int MODE_HORIZONTAL_2 = 2;
    static final int MODE_HORIZONTAL_3 = 3;
    static final int MODE_HORIZONTAL_4 = 4;
    static final int DEFAULT_MODE = MODE_AUTO_CONTENT;

    static final int[] MODES = {
            MODE_AUTO_CONTENT,
            MODE_HORIZONTAL_2,
            MODE_HORIZONTAL_3,
            MODE_HORIZONTAL_4,
            MODE_FIT_PAGE
    };

    static final String[] MODE_LABELS = {
            "自動（表向け）",
            "横2分割",
            "横3分割",
            "横4分割",
            "1ページに収める"
    };

    private static final String PREFS = "fax2840_page_layout";
    private static final String KEY_ZOOM = "split_zoom_percent";
    private static final String KEY_MODE = "split_mode";

    private PageSplitSettings() {}

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
        for (int value : MODES) if (value == mode) return mode;
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
        int index = indexOfMode(mode);
        return MODE_LABELS[index];
    }

    static int getZoomPercent(Context context) {
        if (context == null) return DEFAULT_ZOOM_PERCENT;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return normalizeZoom(prefs.getInt(KEY_ZOOM, DEFAULT_ZOOM_PERCENT));
    }

    static void setZoomPercent(Context context, int zoomPercent) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_ZOOM, normalizeZoom(zoomPercent))
                .apply();
    }

    static int normalizeZoom(int zoomPercent) {
        int best = ZOOM_LEVELS[0];
        int bestDistance = Math.abs(zoomPercent - best);
        for (int value : ZOOM_LEVELS) {
            int distance = Math.abs(zoomPercent - value);
            if (distance < bestDistance) {
                best = value;
                bestDistance = distance;
            }
        }
        return best;
    }

    static int indexOfZoom(int zoomPercent) {
        int normalized = normalizeZoom(zoomPercent);
        for (int i = 0; i < ZOOM_LEVELS.length; i++) {
            if (ZOOM_LEVELS[i] == normalized) return i;
        }
        return 0;
    }
}
