package jp.local.fax2840usb;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppUi {
    static final int COLOR_PRIMARY = Color.rgb(0, 91, 170);
    static final int COLOR_PRIMARY_SOFT = Color.rgb(232, 242, 252);
    static final int COLOR_BACKGROUND = Color.rgb(246, 248, 251);
    static final int COLOR_SURFACE = Color.WHITE;
    static final int COLOR_TEXT = Color.rgb(31, 41, 55);
    static final int COLOR_MUTED = Color.rgb(107, 114, 128);
    static final int COLOR_BORDER = Color.rgb(226, 232, 240);
    static final int COLOR_SUCCESS = Color.rgb(22, 163, 74);
    static final int COLOR_NEUTRAL = Color.rgb(100, 116, 139);

    private AppUi() {}

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(rounded(context, COLOR_SURFACE, 16, COLOR_BORDER, 1));
        card.setElevation(dp(context, 2));
        return card;
    }

    static void addCard(LinearLayout parent, View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(parent.getContext(), 12);
        parent.addView(card, lp);
    }

    static void styleTitle(TextView view) {
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(25f);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    static void styleSectionTitle(TextView view) {
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(18f);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    static void styleBody(TextView view) {
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(14f);
        view.setLineSpacing(0f, 1.15f);
    }

    static void stylePrimaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(button.getContext(), 52));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
    }

    static void styleSecondaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(COLOR_PRIMARY);
        button.setTextSize(15f);
        button.setMinHeight(dp(button.getContext(), 48));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY_SOFT));
    }

    static void styleConnectionBadge(TextView badge, String label, boolean connected) {
        badge.setText(label);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(13f);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setPadding(dp(badge.getContext(), 12), dp(badge.getContext(), 6),
                dp(badge.getContext(), 12), dp(badge.getContext(), 6));
        badge.setBackground(rounded(badge.getContext(),
                connected ? COLOR_SUCCESS : COLOR_NEUTRAL, 999, Color.TRANSPARENT, 0));
    }


    static void configureSystemBars(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(appearance, appearance);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    static void applySystemBarInsets(View view, int leftDp, int topDp, int rightDp, int bottomDp) {
        Context context = view.getContext();
        final int baseLeft = dp(context, leftDp);
        final int baseTop = dp(context, topDp);
        final int baseRight = dp(context, rightDp);
        final int baseBottom = dp(context, bottomDp);

        view.setPadding(baseLeft, baseTop, baseRight, baseBottom);
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int leftInset;
            int topInset;
            int rightInset;
            int bottomInset;

            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                leftInset = bars.left;
                topInset = bars.top;
                rightInset = bars.right;
                bottomInset = bars.bottom;
            } else {
                leftInset = insets.getSystemWindowInsetLeft();
                topInset = insets.getSystemWindowInsetTop();
                rightInset = insets.getSystemWindowInsetRight();
                bottomInset = insets.getSystemWindowInsetBottom();
            }

            v.setPadding(
                    baseLeft + leftInset,
                    baseTop + topInset,
                    baseRight + rightInset,
                    baseBottom + bottomInset);
            return insets;
        });
        view.requestApplyInsets();
    }

    static GradientDrawable rounded(Context context, int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), stroke);
        return drawable;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
