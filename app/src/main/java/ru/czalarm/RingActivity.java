package ru.czalarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.time.LocalDate;

import static ru.czalarm.Ui.*;

public final class RingActivity extends Activity {
    private String session;
    private TextView label;
    private TextView interval;
    private AlertDialog confirmation;

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        session = getIntent().getStringExtra("session");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT < 27) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        FrameLayout screen = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        screen.addView(scroll, centered(this, -1, Gravity.CENTER));
        LinearLayout body = column(this);
        body.setGravity(Gravity.CENTER);
        padding(body, 22, 20, 22, 20);
        scroll.addView(body, -1, -2);

        Icon sun = new Icon(this, false, PINE, 31);
        surface(sun, SAGE, 28, 0, false);
        body.addView(sun, dp(this, 54), dp(this, 54));
        space(body, 19);
        label = text(this, "", 20, INK, "sans-serif-medium");
        label.setGravity(Gravity.CENTER);
        body.addView(label, -1, -2);
        space(body, 3);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(PINE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setLetterSpacing(-0.055f);
        clock.setIncludeFontPadding(false);
        clock.setGravity(Gravity.CENTER);
        clock.setMaxLines(1);
        clock.setHorizontallyScrolling(false);
        clock.setAutoSizeTextTypeUniformWithConfiguration(38, 88, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        body.addView(clock, -1, dp(this, 108));
        TextView day = text(this, date(LocalDate.now()), 15, MUTED, "sans-serif");
        day.setGravity(Gravity.CENTER);
        body.addView(day, -1, -2);
        space(body, 40);

        TextView snooze = button(this, "Отложить", PINE, WHITE, 19);
        snooze.setTextSize(23);
        snooze.setMinHeight(dp(this, 70));
        snooze.setOnClickListener(view -> snooze());
        body.addView(snooze, -1, -2);
        space(body, 6);
        interval = text(this, "", 14, MUTED, "sans-serif");
        interval.setGravity(Gravity.CENTER);
        body.addView(interval, -1, -2);
        space(body, 12);
        TextView dismiss = button(this, "Выключить", Color.TRANSPARENT, MUTED, 13);
        dismiss.setTextSize(15);
        dismiss.setOnClickListener(view -> confirmDismiss());
        body.addView(dismiss, -2, -2);
        attach(this, screen);
    }

    @Override
    protected void onStart() {
        super.onStart();
        watch(this, changes, RingService.RING_CHANGED);
        refresh();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(changes);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        session = intent.getStringExtra("session");
        refresh();
    }

    private void refresh() {
        Alarm alarm = RingService.current();
        if (alarm == null || session == null || !session.equals(RingService.session())) {
            close();
            return;
        }
        boolean multiple = RingService.hasMultiple();
        label.setText(multiple ? "Будильники звонят" : alarm.label);
        interval.setText(multiple ? "Каждый повторится через свой интервал" : "Через " + alarm.interval + " мин");
    }

    private void confirmDismiss() {
        confirmation = new AlertDialog.Builder(this)
                .setTitle("Выключить на сегодня?")
                .setMessage(RingService.hasMultiple() ? "Сегодня эти сигналы больше не повторятся."
                        : "Сегодня этот сигнал больше не повторится.")
                .setOnKeyListener((dialog, key, event) -> handleVolume(event))
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Выключить", (dialog, choice) -> {
                    RingService.dismiss(session);
                    close();
                }).show();
    }

    private void snooze() {
        RingService.snooze(session);
        close();
    }

    private void close() {
        if (confirmation != null) confirmation.dismiss();
        finishAndRemoveTask();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return handleVolume(event) || super.dispatchKeyEvent(event);
    }

    private boolean handleVolume(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) snooze();
            return true;
        }
        return false;
    }
}
