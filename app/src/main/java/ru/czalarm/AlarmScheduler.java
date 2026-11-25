package ru.czalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;

final class AlarmScheduler {
    static final String FIRE = "ru.czalarm.FIRE";
    static final String CHANGED = "ru.czalarm.CHANGED";

    private final Context context;
    private final AlarmManager manager;
    private final AlarmStore store;

    AlarmScheduler(Context context) {
        this.context = context;
        manager = context.getSystemService(AlarmManager.class);
        store = new AlarmStore(context);
    }

    boolean canSchedule() {
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms();
    }

    List<Alarm> load() {
        return store.read();
    }

    void restore(boolean rearm) {
        List<Alarm> alarms = load();
        boolean changed = false;
        boolean allowed = canSchedule();
        for (Alarm alarm : alarms) {
            boolean needsAlarm = alarm.enabled && allowed && (alarm.armedAt == 0
                    || operation(alarm.id, alarm.armedAt, PendingIntent.FLAG_NO_CREATE) == null);
            if (rearm || needsAlarm) {
                long previous = alarm.armedAt;
                arm(alarm);
                changed |= previous != alarm.armedAt;
            }
        }
        if (changed) save(alarms);
    }

    void update(Alarm updated) {
        List<Alarm> alarms = load();
        Alarm previous = AlarmStore.find(alarms, updated.id);
        if (previous == null) {
            alarms.add(updated);
        } else {
            updated.armedAt = previous.armedAt;
            updated.firedDate = previous.firedDate;
            updated.snoozeAt = previous.snoozeAt;
            boolean scheduleEdited = previous.hour != updated.hour || previous.minute != updated.minute
                    || previous.days != updated.days || previous.enabled != updated.enabled;
            if (scheduleEdited) {
                updated.snoozeAt = 0;
                if (updated.enabled) updated.firedDate = null;
                RingService.remove(updated.id);
            }
            alarms.set(alarms.indexOf(previous), updated);
        }
        arm(updated);
        save(alarms);
    }

    void delete(String id) {
        List<Alarm> alarms = load();
        Alarm alarm = AlarmStore.find(alarms, id);
        if (alarm == null) return;
        cancel(alarm);
        alarms.remove(alarm);
        save(alarms);
        RingService.remove(id);
    }

    boolean fire(String id, long at) {
        List<Alarm> alarms = load();
        Alarm alarm = AlarmStore.find(alarms, id);
        if (alarm == null || !alarm.enabled || at == 0 || alarm.armedAt != at) return false;
        alarm.startRinging(at, System.currentTimeMillis(), ZoneId.systemDefault());
        arm(alarm);
        save(alarms);
        return true;
    }

    void resolve(Set<String> ids, boolean snooze) {
        List<Alarm> alarms = load();
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        for (Alarm alarm : alarms) {
            if (!alarm.enabled || !ids.contains(alarm.id)) continue;
            if (snooze) {
                alarm.postpone(now);
            } else {
                alarm.dismissToday(now, zone);
            }
            arm(alarm);
        }
        save(alarms);
    }

    private void arm(Alarm alarm) {
        long now = System.currentTimeMillis();
        long next = alarm.next(now, ZoneId.systemDefault());
        cancel(alarm);
        if (next == 0 || !canSchedule()) return;
        PendingIntent operation = operation(alarm.id, next, 0);
        Intent show = new Intent(context, MainActivity.class);
        PendingIntent showIntent = PendingIntent.getActivity(context, 0, show, PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(Math.max(now + 250, next), showIntent),
                    operation);
            alarm.armedAt = next;
        } catch (SecurityException revoked) {
            operation.cancel();
        }
    }

    private void cancel(Alarm alarm) {
        if (alarm.armedAt == 0) return;
        PendingIntent previous = operation(alarm.id, alarm.armedAt, PendingIntent.FLAG_NO_CREATE);
        if (previous != null) {
            manager.cancel(previous);
            previous.cancel();
        }
        alarm.armedAt = 0;
    }

    private PendingIntent operation(String id, long at, int flags) {
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(FIRE)
                .setData(Uri.parse("czalarm://fire/" + id + "/" + at))
                .putExtra("id", id).putExtra("at", at);
        return PendingIntent.getBroadcast(context, 0, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private void save(List<Alarm> alarms) {
        store.write(alarms);
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }
}
