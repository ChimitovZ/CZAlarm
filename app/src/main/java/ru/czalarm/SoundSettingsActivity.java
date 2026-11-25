package ru.czalarm;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import static ru.czalarm.Ui.*;

public final class SoundSettingsActivity extends Activity {
    private static final int PICK_MELODY = 1;
    private static final int ALARM_STREAM = AudioManager.STREAM_ALARM;
    private static final long PREVIEW_LENGTH_MS = 8_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable previewTimeout = this::stopPreview;
    private final BroadcastReceiver ringChanges = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RingService.session() != null) stopPreview();
        }
    };

    private AudioManager audio;
    private AlarmSound preview;
    private TextView melodyTitle;
    private TextView volumeValue;
    private TextView previewButton;
    private SeekBar slider;
    private boolean previewing;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        audio = getSystemService(AudioManager.class);
        preview = new AlarmSound(this);

        FrameLayout screen = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        screen.addView(scroll, centered(this, -1, Gravity.TOP));
        LinearLayout body = column(this);
        padding(body, 22, 20, 22, 24);
        scroll.addView(body);

        TextView back = button(this, "‹  Назад", CARD, PINE, 15);
        back.setOnClickListener(view -> finish());
        body.addView(back, -2, -2);
        space(body, 16);
        body.addView(text(this, "Звук будильника", 30, INK, "sans-serif-medium"));
        space(body, 16);
        body.addView(melodyPanel(), -1, -2);
        space(body, 12);
        body.addView(volumePanel(), -1, -2);
        attach(this, screen);
    }

    @Override
    protected void onStart() {
        super.onStart();
        watch(this, ringChanges, RingService.RING_CHANGED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        melodyTitle.setText(AlarmSound.title(this));
        updateVolume();
    }

    @Override
    protected void onPause() {
        stopPreview();
        super.onPause();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(ringChanges);
        super.onStop();
    }

    private LinearLayout melodyPanel() {
        LinearLayout panel = panel();
        panel.addView(text(this, "Мелодия", 15, MUTED, "sans-serif-medium"));
        space(panel, 4);
        melodyTitle = text(this, "", 21, INK, "sans-serif-medium");
        panel.addView(melodyTitle, -1, -2);
        space(panel, 13);
        TextView choose = button(this, "Выбрать мелодию", SAGE, PINE, 13);
        choose.setOnClickListener(view -> chooseMelody());
        panel.addView(choose, -1, -2);
        space(panel, 8);
        previewButton = button(this, "Прослушать", PINE, WHITE, 13);
        previewButton.setOnClickListener(view -> {
            if (previewing) {
                stopPreview();
            } else if (RingService.session() == null) {
                previewing = true;
                previewButton.setText("Остановить");
                handler.postDelayed(previewTimeout, PREVIEW_LENGTH_MS);
                preview.play(false, this::stopPreview);
            }
        });
        panel.addView(previewButton, -1, -2);
        return panel;
    }

    private LinearLayout volumePanel() {
        LinearLayout panel = panel();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(this, "Громкость", 18, INK, "sans-serif-medium"),
                new LinearLayout.LayoutParams(0, -2, 1));
        volumeValue = text(this, "", 16, PINE, "sans-serif-medium");
        heading.addView(volumeValue, -2, -2);
        panel.addView(heading, -1, -2);
        space(panel, 4);

        slider = new SeekBar(this);
        slider.setMin(Build.VERSION.SDK_INT >= 28 ? audio.getStreamMinVolume(ALARM_STREAM) : 0);
        slider.setMax(audio.getStreamMaxVolume(ALARM_STREAM));
        slider.setKeyProgressIncrement(1);
        slider.setContentDescription("Системная громкость будильников");
        slider.setProgressTintList(ColorStateList.valueOf(PINE));
        slider.setThumbTintList(ColorStateList.valueOf(PINE));
        slider.setEnabled(!audio.isVolumeFixed());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int step, boolean fromUser) {
                if (!fromUser) return;
                try {
                    audio.setStreamVolume(ALARM_STREAM, step, 0);
                } catch (SecurityException unavailable) {
                    Toast.makeText(SoundSettingsActivity.this, "Android ограничил изменение громкости",
                            Toast.LENGTH_SHORT).show();
                }
                updateVolume();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(slider, -1, dp(this, 48));
        panel.addView(text(this, "Общая громкость для будильников на телефоне.",
                14, MUTED, "sans-serif"));
        return panel;
    }

    private LinearLayout panel() {
        LinearLayout panel = column(this);
        surface(panel, CARD, 18, LINE, false);
        padding(panel, 16, 16, 16, 16);
        return panel;
    }

    private void updateVolume() {
        int level = audio.getStreamVolume(ALARM_STREAM);
        slider.setProgress(level);
        volumeValue.setText(getString(R.string.alarm_volume_level, level, slider.getMax()));
    }

    private void chooseMelody() {
        stopPreview();
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Мелодия будильника")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, AlarmSound.defaultUri())
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AlarmSound.selected(this));
        try {
            startActivityForResult(picker, PICK_MELODY);
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(this, "На телефоне недоступен выбор мелодии", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_MELODY || result != RESULT_OK || data == null) return;
        Uri uri = Build.VERSION.SDK_INT >= 33
                ? data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class)
                : data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) return;
        int flags = data.getFlags();
        if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
                && (flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException unsupported) {
                // Built-in alarm sounds remain readable through the system picker.
            }
        }
        AlarmSound.select(this, uri);
        melodyTitle.setText(AlarmSound.title(this));
    }

    private void stopPreview() {
        handler.removeCallbacks(previewTimeout);
        preview.stop();
        previewing = false;
        previewButton.setText("Прослушать");
    }
}
