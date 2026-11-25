package ru.czalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RingService extends Service {
    static final String CHANNEL = "ringing";
    static final String SNOOZE = "ru.czalarm.SNOOZE";
    static final String RING_CHANGED = "ru.czalarm.RING_CHANGED";
    private static final int NOTIFICATION_ID = 1;
    private static final long RING_LENGTH_MS = 55_000;
    private static RingService running;

    private final Map<String, Alarm> ringing = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::endRing;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            resolve(true);
        }
    };

    private String session;
    private AlarmSound sound;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private boolean watchingScreen;

    static String session() {
        return running == null ? null : running.session;
    }

    static Alarm current() {
        if (running == null || running.ringing.isEmpty()) return null;
        return running.ringing.values().iterator().next();
    }

    static boolean hasMultiple() {
        return running != null && running.ringing.size() > 1;
    }

    static void snooze(String session) {
        if (session != null && session.equals(session())) running.resolve(true);
    }

    static void dismiss(String session) {
        if (session != null && session.equals(session())) running.resolve(false);
    }

    static void remove(String id) {
        if (running == null || running.ringing.remove(id) == null) return;
        if (running.ringing.isEmpty()) {
            running.endRing();
        } else {
            running.updateNotification();
            running.notifyScreen();
        }
    }

    static Intent screen(Context context, String session) {
        return new Intent(context, RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("session", session);
    }

    static NotificationChannel ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        if (channel != null) return channel;
        channel = new NotificationChannel(CHANNEL, "Сигнал будильника", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
        return channel;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (session == null) {
            session = UUID.randomUUID().toString();
            running = this;
        }
        Alarm alarm = intent == null ? null
                : AlarmStore.find(new AlarmStore(this).read(), intent.getStringExtra("id"));
        if (alarm != null && alarm.enabled) ringing.put(alarm.id, alarm);

        ensureChannel(this);
        startForeground(NOTIFICATION_ID, notification());
        if (ringing.isEmpty()) {
            endRing();
            return START_NOT_STICKY;
        }
        if (!watchingScreen) {
            beginRing();
        }
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, RING_LENGTH_MS);
        notifyScreen();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Uri sessionUri = Uri.parse("czalarm://ring/" + session);
        PendingIntent show = PendingIntent.getActivity(this, 0, screen(this, session).setData(sessionUri),
                PendingIntent.FLAG_IMMUTABLE);
        Intent snoozeIntent = new Intent(this, AlarmReceiver.class).setAction(SNOOZE)
                .setData(sessionUri).putExtra("session", session);
        PendingIntent snooze = PendingIntent.getBroadcast(this, 0, snoozeIntent, PendingIntent.FLAG_IMMUTABLE);
        Alarm alarm = current();
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(hasMultiple() || alarm == null ? "Будильник" : alarm.label)
                .setContentText("Будильник звонит")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(show)
                .setFullScreenIntent(show, true)
                .addAction(new Notification.Action.Builder(null, "Отложить", snooze).build());
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    private void beginRing() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenOff, filter);
        }
        watchingScreen = true;

        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CZAlarm:ring");
        wakeLock.acquire(2 * RING_LENGTH_MS);
        sound = new AlarmSound(this);
        sound.play(true, null);

        vibrator = Build.VERSION.SDK_INT >= 31 ? getSystemService(VibratorManager.class).getDefaultVibrator()
                : getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            AudioAttributes audio = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 350, 550, 350, 1800}, 0), audio);
        }
    }

    private void resolve(boolean snooze) {
        new AlarmScheduler(this).resolve(ringing.keySet(), snooze);
        endRing();
    }

    private void notifyScreen() {
        sendBroadcast(new Intent(RING_CHANGED).setPackage(getPackageName()));
    }

    private void endRing() {
        if (running == this) running = null;
        session = null;
        ringing.clear();
        cleanup();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        notifyScreen();
    }

    private void cleanup() {
        handler.removeCallbacks(timeout);
        if (watchingScreen) unregisterReceiver(screenOff);
        watchingScreen = false;
        if (sound != null) {
            sound.stop();
            sound = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public void onDestroy() {
        if (running == this) {
            running = null;
            notifyScreen();
        }
        cleanup();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
