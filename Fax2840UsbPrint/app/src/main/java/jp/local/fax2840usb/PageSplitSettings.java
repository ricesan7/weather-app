package jp.local.fax2840usb;

import android.content.Context;
import android.content.SharedPreferences;

final class PageSplitSettings {
    static final int[] ZOOM_LEVELS = {100, 150, 200, 250, 300};
    static final int DEFAULT_ZOOM_PERCENT = 100;

    private static final String PREFS = "fax2840_page_layout";
    private static final String KEY_ZOOM = "split_zoom_percent";

    private PageSplitSettings() {}

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
