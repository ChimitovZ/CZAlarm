package ru.czalarm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Ui {
    static final int PAPER = 0xfff5f2eb;
    static final int CARD = 0xfffcfaf5;
    static final int INK = 0xff202721;
    static final int MUTED = 0xff5d655b;
    static final int PINE = 0xff24382f;
    static final int SAGE = 0xffe7ebdf;
    static final int LINE = 0xffe5e3d8;
    static final int WHITE = 0xfffdfbf5;
    static final String[] DAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
    static final Locale RU = Locale.forLanguageTag("ru-RU");

    private Ui() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context context, int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static void surface(View view, int color, int radius, int stroke, boolean touch) {
        Context context = view.getContext();
        GradientDrawable background = shape(context, color, radius, stroke);
        if (touch) {
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x222e4537), background,
                    shape(context, Color.WHITE, radius, 0)));
        } else {
            view.setBackground(background);
        }
    }

    static void attach(Activity activity, View content) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        content.setBackgroundColor(PAPER);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        activity.setContentView(content);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (controller != null) controller.setSystemBarsAppearance(lightBars, lightBars);
        }
        content.requestApplyInsets();
    }

    static void watch(Context context, BroadcastReceiver receiver, String... actions) {
        IntentFilter filter = new IntentFilter();
        for (String action : actions) filter.addAction(action);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static TextView text(Context context, String text, float size, int color, String family) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return view;
    }

    static void space(LinearLayout layout, int height) {
        layout.addView(new View(layout.getContext()), 1, dp(layout.getContext(), height));
    }

    static void padding(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext();
        view.setPadding(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
    }

    static TextView button(Context context, String label, int fill, int color, int radius) {
        TextView button = text(context, label, 16, color, "sans-serif-medium");
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 48));
        padding(button, 12, 8, 12, 8);
        surface(button, fill, radius, 0, true);
        button.setFocusable(true);
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return button;
    }

    static FrameLayout.LayoutParams centered(Context context, int height, int gravity) {
        int width = Math.min(context.getResources().getConfiguration().screenWidthDp, 500);
        return new FrameLayout.LayoutParams(dp(context, width), height, gravity | Gravity.CENTER_HORIZONTAL);
    }

    static String time(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    static String date(LocalDate day) {
        return day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU));
    }

    static String dayLabel(int days) {
        if (days == 0) return "Один раз";
        if (days == 127) return "Каждый день";
        if (days == Alarm.WEEKDAYS) return "Будни";
        if (days == 96) return "Выходные";
        StringBuilder label = new StringBuilder();
        for (int start = 0; start < DAYS.length; start++) {
            if ((days & (1 << start)) == 0) continue;
            int end = start;
            while (end + 1 < DAYS.length && (days & (1 << (end + 1))) != 0) end++;
            if (label.length() > 0) label.append(", ");
            label.append(DAYS[start]);
            if (end > start) label.append(end == start + 1 ? ", " : "–").append(DAYS[end]);
            start = end;
        }
        return label.toString();
    }

    @SuppressLint("ViewConstructor") // Icons are created in code with an explicit glyph, color and size.
    static final class Icon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean plus;
        private final int size;

        Icon(Context context, boolean plus, int color, int size) {
            super(context);
            this.plus = plus;
            this.size = size;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(1.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float scale = dp(getContext(), size) / 24f;
            canvas.save();
            canvas.translate(getWidth() / 2f - 12 * scale, getHeight() / 2f - 12 * scale);
            canvas.scale(scale, scale);
            if (plus) {
                canvas.drawLine(12, 5, 12, 19, paint);
                canvas.drawLine(5, 12, 19, 12, paint);
            } else {
                canvas.drawArc(6, 9, 18, 21, 180, 180, false, paint);
                canvas.drawLine(2, 15, 22, 15, paint);
                canvas.drawLine(7, 19, 17, 19, paint);
                canvas.drawLine(12, 3, 12, 5, paint);
                canvas.drawLine(5, 6, 6.6f, 7.6f, paint);
                canvas.drawLine(19, 6, 17.4f, 7.6f, paint);
            }
            canvas.restore();
        }
    }
}
