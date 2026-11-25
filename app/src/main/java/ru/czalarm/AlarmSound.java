package ru.czalarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;

final class AlarmSound {
    private static final Uri SYSTEM_DEFAULT = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    private static final String FALLBACK_TITLE = "Мелодия CZAlarm";
    private static final AudioAttributes ALARM_AUDIO = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private final Context context;
    private final Uri fallback;
    private MediaPlayer player;

    AlarmSound(Context context) {
        this.context = context.getApplicationContext();
        fallback = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.alarm_tone);
    }

    static Uri defaultUri() {
        return SYSTEM_DEFAULT;
    }

    static Uri selected(Context context) {
        String value = prefs(context).getString("ringtone", null);
        return value == null ? SYSTEM_DEFAULT : Uri.parse(value);
    }

    static void select(Context context, Uri uri) {
        prefs(context).edit().putString("ringtone", uri.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("sound", Context.MODE_PRIVATE);
    }

    static String title(Context context) {
        Uri uri = selected(context);
        boolean useDefault = SYSTEM_DEFAULT.equals(uri);
        if (useDefault) uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM);
        if (uri == null) return FALLBACK_TITLE;
        String title = uri.isHierarchical() ? uri.getQueryParameter("title") : null;
        if (title == null || title.trim().isEmpty()) {
            try {
                Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
                if (ringtone != null) {
                    try {
                        title = ringtone.getTitle(context);
                    } finally {
                        ringtone.stop();
                    }
                }
            } catch (SecurityException ignored) {
                // A tone provider can become unavailable after a selection.
            }
        }
        if (title == null || title.trim().isEmpty()) return useDefault ? "Системная мелодия" : "Выбранная мелодия";
        return useDefault ? "По умолчанию · " + title : title;
    }

    void play(boolean repeat, Runnable finished) {
        play(selected(context), repeat, finished);
    }

    private void play(Uri uri, boolean repeat, Runnable finished) {
        stop();
        MediaPlayer media = new MediaPlayer();
        player = media;
        try {
            media.setAudioAttributes(ALARM_AUDIO);
            media.setDataSource(context, uri);
            media.setLooping(repeat);
            media.setOnPreparedListener(prepared -> {
                if (player == prepared) prepared.start();
            });
            media.setOnCompletionListener(completed -> {
                if (player == completed) finish(finished);
            });
            media.setOnErrorListener((failed, what, extra) -> {
                if (player == failed) recover(uri, repeat, finished);
                return true;
            });
            media.prepareAsync();
        } catch (IOException | RuntimeException error) {
            Log.w("CZAlarm", "Cannot play selected alarm sound", error);
            recover(uri, repeat, finished);
        }
    }

    private void recover(Uri uri, boolean repeat, Runnable finished) {
        if (fallback.equals(uri)) {
            finish(finished);
        } else {
            play(fallback, repeat, finished);
        }
    }

    private void finish(Runnable finished) {
        stop();
        if (finished != null) finished.run();
    }

    void stop() {
        if (player == null) return;
        player.release();
        player = null;
    }
}
