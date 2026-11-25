package ru.czalarm;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ru.czalarm.Ui.*;

public final class MainActivity extends Activity {
    private static final String NOTIFICATION_ASKED = "notificationAsked";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTick = this::updateCountdown;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            render();
            showRingingAlarm();
        }
    };

    private AlarmScheduler scheduler;
    private ScrollView scroll;
    private LinearLayout content;
    private TextView countdown;
    private long countdownAt;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        scheduler = new AlarmScheduler(this);
        RingService.ensureChannel(this);
        FrameLayout screen = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        screen.addView(scroll, centered(this, -1, Gravity.TOP));
        content = column(this);
        padding(content, 22, 20, 22, 24);
        scroll.addView(content);
        attach(this, screen);
    }

    @Override
    protected void onStart() {
        super.onStart();
        watch(this, changes, AlarmScheduler.CHANGED, RingService.RING_CHANGED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduler.restore(false);
        render();
        showRingingAlarm();
        if (Build.VERSION.SDK_INT >= 33 && RingService.session() == null
                && !getSystemService(NotificationManager.class).areNotificationsEnabled()
                && !getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_ASKED, false)) {
            requestNotifications();
        }
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(countdownTick);
        unregisterReceiver(changes);
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) showRingingAlarm();
    }

    private void showRingingAlarm() {
        String session = RingService.session();
        if (session != null && hasWindowFocus()) startActivity(RingService.screen(this, session));
    }

    private void render() {
        handler.removeCallbacks(countdownTick);
        countdown = null;
        int scrollY = scroll.getScrollY();
        content.removeAllViews();
        LocalDate today = LocalDate.now();
        List<Alarm> alarms = scheduler.load();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Icon sun = new Icon(this, false, PINE, 23);
        surface(sun, SAGE, 20, 0, false);
        top.addView(sun, dp(this, 38), dp(this, 38));
        TextView date = text(this, date(today), 14, MUTED, "sans-serif");
        padding(date, 10, 0, 8, 0);
        top.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        TextView sound = button(this, "Звук", CARD, PINE, 15);
        sound.setContentDescription("Настройки звука будильника");
        sound.setOnClickListener(view -> startActivity(new Intent(this, SoundSettingsActivity.class)));
        top.addView(sound, -2, -2);
        content.addView(top, -1, -2);
        space(content, 13);
        content.addView(text(this, "Будильники", 35, INK, "sans-serif-medium"));

        TextView permission = permissionButton();
        if (permission != null) {
            space(content, 10);
            content.addView(permission, -1, -2);
        }

        space(content, 13);
        content.addView(hero(alarms, today), -1, -2);
        space(content, 12);
        content.addView(alarmListHeader(), -1, -2);
        space(content, 9);
        for (Alarm alarm : alarms) {
            content.addView(alarmRow(alarm), -1, -2);
            space(content, 8);
        }
        if (alarms.isEmpty()) {
            content.addView(text(this, "Нажмите +, чтобы добавить будильник", 15, MUTED, "sans-serif"));
        }
        space(content, 12);
        content.addView(week(alarms, today), -1, -2);
        if (scrollY > 0) scroll.post(() -> scroll.scrollTo(0, scrollY));
    }

    private LinearLayout alarmListHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(this, "Все будильники", 22, INK, "sans-serif-medium"),
                new LinearLayout.LayoutParams(0, -2, 1));
        Icon add = new Icon(this, true, WHITE, 26);
        surface(add, PINE, 24, 0, true);
        add.setContentDescription("Добавить будильник");
        add.setFocusable(true);
        add.setOnClickListener(view -> new AlarmEditor(this, scheduler, null).show());
        header.addView(add, dp(this, 48), dp(this, 48));
        return header;
    }

    private View hero(List<Alarm> alarms, LocalDate today) {
        Alarm next = null;
        long nextTime = Long.MAX_VALUE;
        for (Alarm alarm : alarms) {
            if (alarm.armedAt != 0 && alarm.armedAt < nextTime) {
                nextTime = alarm.armedAt;
                next = alarm;
            }
        }
        RelativeLayout hero = new RelativeLayout(this);
        surface(hero, PINE, 22, 0, false);
        hero.setClipToOutline(true);
        LinearLayout words = column(this);
        words.setId(View.generateViewId());
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.setMinimumHeight(dp(this, 158));
        padding(words, 19, 13, 19, 13);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.dawn);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setForeground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xd7112119, 0x7f14251b, 0x18304434}));
        RelativeLayout.LayoutParams backdrop = new RelativeLayout.LayoutParams(-1, 0);
        backdrop.addRule(RelativeLayout.ALIGN_TOP, words.getId());
        backdrop.addRule(RelativeLayout.ALIGN_BOTTOM, words.getId());
        hero.addView(image, backdrop);
        hero.addView(words, new RelativeLayout.LayoutParams(-1, -2));
        if (next == null) {
            words.addView(text(this, "Нет запланированных сигналов", 20, WHITE, "sans-serif-medium"));
        } else {
            ZonedDateTime when = Instant.ofEpochMilli(nextTime).atZone(ZoneId.systemDefault());
            String day = when.toLocalDate().equals(today) ? "Сегодня"
                    : when.toLocalDate().equals(today.plusDays(1)) ? "Завтра"
                    : date(when.toLocalDate());
            words.addView(text(this, "Ближайший будильник", 14, WHITE, "sans-serif"));
            TextView time = text(this, time(when.getHour(), when.getMinute()), 55, WHITE, "sans-serif-light");
            time.setIncludeFontPadding(false);
            time.setLetterSpacing(-0.04f);
            words.addView(time);
            space(words, 3);
            words.addView(text(this, day + " · " + next.label, 14, WHITE, "sans-serif-medium"));
            space(words, 8);
            countdownAt = nextTime;
            countdown = text(this, "", 14, PINE, "sans-serif-medium");
            surface(countdown, SAGE, 12, 0, false);
            padding(countdown, 10, 3, 10, 3);
            words.addView(countdown, -2, -2);
            updateCountdown();
            Alarm upcoming = next;
            hero.setOnClickListener(view -> new AlarmEditor(this, scheduler, upcoming).show());
            hero.setFocusable(true);
        }
        return hero;
    }

    private void updateCountdown() {
        if (countdown == null) return;
        long remaining = countdownAt - System.currentTimeMillis();
        if (remaining <= 0) {
            countdown.setText("Сейчас");
        } else if (remaining < 60_000) {
            countdown.setText(getString(R.string.countdown_seconds, (remaining + 999) / 1_000));
            handler.postDelayed(countdownTick, (remaining - 1) % 1_000 + 1);
        } else {
            long minutes = remaining / 60_000;
            long days = minutes / (24 * 60);
            long hours = (minutes / 60) % 24;
            StringBuilder text = new StringBuilder("Через");
            if (days > 0) text.append(' ').append(days).append(" д");
            if (hours > 0) text.append(' ').append(hours).append(" ч");
            if (minutes % 60 != 0) text.append(' ').append(minutes % 60).append(" мин");
            countdown.setText(text);
            handler.postDelayed(countdownTick, remaining % 60_000 + 1);
        }
    }

    private LinearLayout week(List<Alarm> alarms, LocalDate today) {
        LinearLayout week = new LinearLayout(this);
        week.setBaselineAligned(false);
        surface(week, CARD, 16, LINE, false);
        padding(week, 8, 6, 8, 6);
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);
        for (int i = 0; i < DAYS.length; i++) {
            LocalDate day = monday.plusDays(i);
            boolean selected = day.equals(today);
            boolean enabled = false;
            for (Alarm alarm : alarms) {
                if (alarm.enabled && !day.equals(alarm.firedDate) && (alarm.days & (1 << i)) != 0) {
                    enabled = true;
                    break;
                }
            }
            LinearLayout cell = column(this);
            cell.setGravity(Gravity.CENTER);
            padding(cell, 0, 6, 0, 6);
            if (selected) surface(cell, PINE, 12, 0, false);
            TextView weekday = text(this, DAYS[i], 12, selected ? WHITE : MUTED, "sans-serif");
            weekday.setGravity(Gravity.CENTER);
            cell.addView(weekday, -1, -2);
            TextView number = text(this, String.valueOf(day.getDayOfMonth()), 16,
                    selected ? WHITE : INK, "sans-serif-medium");
            number.setGravity(Gravity.CENTER);
            cell.addView(number, -1, -2);
            space(cell, 2);
            View dot = new View(this);
            surface(dot, enabled ? (selected ? WHITE : 0xff63816a) : Color.TRANSPARENT, 3, 0, false);
            cell.addView(dot, dp(this, 4), dp(this, 4));
            cell.setContentDescription(date(day) + (enabled ? ", по расписанию" : ""));
            cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            for (int j = 0; j < cell.getChildCount(); j++) {
                cell.getChildAt(j).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            LinearLayout.LayoutParams width = new LinearLayout.LayoutParams(0, -2, 1);
            width.setMargins(dp(this, 1), 0, dp(this, 1), 0);
            week.addView(cell, width);
        }
        return week;
    }

    private View alarmRow(Alarm alarm) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        surface(row, CARD, 16, LINE, false);
        padding(row, 1, 1, 6, 1);
        LinearLayout details = new LinearLayout(this);
        details.setGravity(Gravity.CENTER_VERTICAL);
        padding(details, 12, 10, 4, 10);
        surface(details, Color.TRANSPARENT, 15, 0, true);
        TextView time = text(this, time(alarm.hour, alarm.minute), 26, alarm.enabled ? INK : MUTED, "sans-serif");
        time.setLetterSpacing(-0.03f);
        time.setSingleLine();
        details.addView(time, -2, -2);
        LinearLayout labels = column(this);
        padding(labels, 12, 0, 0, 0);
        TextView name = text(this, alarm.label, 15, INK, "sans-serif-medium");
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name, -1, -2);
        labels.addView(text(this, dayLabel(alarm.days) + " · " + alarm.interval + " мин", 12, MUTED, "sans-serif"));
        details.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        details.setFocusable(true);
        details.setContentDescription("Изменить: " + alarm.label + ", " + time(alarm.hour, alarm.minute));
        details.setOnClickListener(view -> new AlarmEditor(this, scheduler, alarm).show());

        Switch enabled = new Switch(this);
        enabled.setChecked(alarm.enabled);
        enabled.setGravity(Gravity.CENTER);
        enabled.setMinWidth(dp(this, 48));
        enabled.setMinHeight(dp(this, 48));
        enabled.setThumbTintList(ColorStateList.valueOf(WHITE));
        enabled.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xff42604d, 0xffc1c6b9}));
        enabled.setContentDescription("Будильник «" + alarm.label + "»");
        enabled.setOnCheckedChangeListener((button, checked) -> {
            alarm.enabled = checked;
            scheduler.update(alarm);
        });
        row.addView(enabled, -2, -2);
        return row;
    }

    private TextView permissionButton() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        Uri app = Uri.parse("package:" + getPackageName());
        String label;
        Runnable request;
        if (!scheduler.canSchedule()) {
            label = "Разрешить точные будильники";
            request = () -> startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app));
        } else if (!manager.areNotificationsEnabled()) {
            label = "Разрешить уведомления будильника";
            request = this::requestNotifications;
        } else if (RingService.ensureChannel(this).getImportance() < NotificationManager.IMPORTANCE_HIGH) {
            label = "Включить показ сигнала на экране";
            request = () -> startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, RingService.CHANNEL));
        } else if (needsFullScreenPermission(manager)) {
            label = "Разрешить будильник поверх блокировки";
            request = () -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app));
        } else {
            return null;
        }
        TextView button = button(this, label, SAGE, PINE, 13);
        button.setOnClickListener(view -> request.run());
        return button;
    }

    private static boolean needsFullScreenPermission(NotificationManager manager) {
        return Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && (!getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_ASKED, false)
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            getPreferences(MODE_PRIVATE).edit().putBoolean(NOTIFICATION_ASKED, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        render();
    }
}
