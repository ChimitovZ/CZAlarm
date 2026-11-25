package ru.czalarm;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.InsetDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalTime;

import static ru.czalarm.Ui.*;

final class AlarmEditor {
    private final MainActivity context;
    private final AlarmScheduler scheduler;
    private final Alarm original;
    private int hour;
    private int minute;
    private int days;

    AlarmEditor(MainActivity context, AlarmScheduler scheduler, Alarm original) {
        this.context = context;
        this.scheduler = scheduler;
        this.original = original;
        LocalTime suggested = LocalTime.now().plusMinutes(2);
        hour = original == null ? suggested.getHour() : original.hour;
        minute = original == null ? suggested.getMinute() : original.minute;
        days = original == null ? 0 : original.days;
    }

    void show() {
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout form = column(context);
        form.setFocusableInTouchMode(true);
        padding(form, 19, 17, 19, 13);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        form.addView(text(context, original == null ? "Новый будильник" : "Изменить будильник",
                23, INK, "sans-serif-medium"));
        space(form, 11);

        TextView timeButton = button(context, time(hour, minute), SAGE, PINE, 15);
        timeButton.setTextSize(36);
        timeButton.setContentDescription("Время будильника " + time(hour, minute));
        timeButton.setOnClickListener(view ->
                new TimePickerDialog(context, (picker, h, m) -> {
                    hour = h;
                    minute = m;
                    timeButton.setText(time(hour, minute));
                    timeButton.setContentDescription("Время будильника " + time(hour, minute));
                }, hour, minute, true).show());
        form.addView(timeButton, -1, -2);
        space(form, 13);

        EditText name = new EditText(context);
        name.setTextColor(INK);
        name.setHintTextColor(MUTED);
        name.setTextSize(16);
        name.setSingleLine();
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(36)});
        name.setHint("Название");
        if (original != null) name.setText(original.label);
        form.addView(name, -1, -2);
        space(form, 11);

        TextView dayLabel = text(context, "Дни недели", 15, INK, "sans-serif-medium");
        form.addView(dayLabel);
        space(form, 4);
        LinearLayout week = new LinearLayout(context);
        form.addView(week, -1, -2);
        TextView repeatSummary = text(context, dayLabel(days), 13, MUTED, "sans-serif");
        for (int i = 0; i < DAYS.length; i++) {
            int flag = 1 << i;
            TextView day = button(context, DAYS[i], SAGE, PINE, 9);
            day.setTextSize(13);
            padding(day, 0, 0, 0, 0);
            day.setMinHeight(dp(context, 44));
            day.setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            styleDay(day, flag);
            day.setOnClickListener(view -> {
                days ^= flag;
                styleDay(day, flag);
                repeatSummary.setText(dayLabel(days));
            });
            LinearLayout.LayoutParams width = new LinearLayout.LayoutParams(0, dp(context, 44), 1);
            if (i != 0) width.setMarginStart(dp(context, 3));
            week.addView(day, width);
        }
        space(form, 2);
        form.addView(repeatSummary);
        space(form, 11);

        TextView intervalLabel = text(context, "Интервал повтора, мин", 15, INK, "sans-serif-medium");
        form.addView(intervalLabel);
        EditText interval = new EditText(context);
        interval.setId(View.generateViewId());
        intervalLabel.setLabelFor(interval.getId());
        interval.setTextColor(INK);
        interval.setTextSize(20);
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        interval.setText(String.valueOf(original == null ? 10 : original.interval));
        form.addView(interval, dp(context, 98), -2);
        space(form, 12);

        TextView save = button(context, "Сохранить", PINE, WHITE, 14);
        form.addView(save, -1, -2);
        save.setOnClickListener(view -> {
            String raw = interval.getText().toString();
            int minutes;
            try {
                minutes = Integer.parseInt(raw);
            } catch (NumberFormatException invalid) {
                minutes = 0;
            }
            if (minutes < 1 || minutes > 120) {
                interval.setError("От 1 до 120 минут");
                interval.requestFocus();
                return;
            }
            String label = name.getText().toString().trim();
            if (label.isEmpty()) label = "Будильник";
            Alarm alarm = original == null ? Alarm.create(label, hour, minute, days, minutes)
                    : new Alarm(original.id, label, hour, minute, days, minutes, original.enabled);
            scheduler.update(alarm);
            dialog.dismiss();
        });
        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.END);
        if (original != null) {
            TextView delete = button(context, "Удалить", Color.TRANSPARENT, 0xff86453b, 10);
            footer.addView(delete, new LinearLayout.LayoutParams(0, -2, 1));
            delete.setOnClickListener(view -> new AlertDialog.Builder(context)
                    .setTitle("Удалить будильник?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (confirm, which) -> {
                        scheduler.delete(original.id);
                        dialog.dismiss();
                    }).show());
        }
        TextView cancel = button(context, "Отмена", Color.TRANSPARENT, MUTED, 10);
        footer.addView(cancel, -2, -2);
        cancel.setOnClickListener(view -> dialog.dismiss());
        form.addView(footer, -1, -2);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new InsetDrawable(shape(context, PAPER, 22, LINE),
                    dp(context, 10)));
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        form.requestFocus();
    }

    private void styleDay(TextView day, int flag) {
        boolean active = (days & flag) != 0;
        surface(day, active ? PINE : CARD, 9, active ? 0 : LINE, true);
        day.setTextColor(active ? WHITE : INK);
        day.setSelected(active);
    }
}
