package ru.czalarm;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

final class Alarm {
    static final int WEEKDAYS = 0b0011111;

    final String id;
    String label;
    int hour;
    int minute;
    int days;
    int interval;
    boolean enabled;
    LocalDate firedDate;
    long snoozeAt;
    long armedAt;

    Alarm(String id, String label, int hour, int minute, int days, int interval, boolean enabled) {
        this.id = id;
        this.label = label;
        this.hour = hour;
        this.minute = minute;
        this.days = days;
        this.interval = interval;
        this.enabled = enabled;
    }

    static Alarm create(String label, int hour, int minute, int days, int interval) {
        return new Alarm(UUID.randomUUID().toString(), label, hour, minute, days, interval, true);
    }

    long next(long now, ZoneId zone) {
        if (!enabled) return 0;
        long regular = nextRegular(now, zone);
        if (snoozeAt == 0 || (regular != 0 && regular < snoozeAt)) return regular;
        return snoozeAt;
    }

    void startRinging(long scheduled, long now, ZoneId zone) {
        if (scheduled != snoozeAt) firedDate = dayOf(scheduled, zone);
        // Keep a repeat scheduled even when nobody touches the ringing screen.
        postpone(Math.max(scheduled, now));
    }

    void postpone(long now) {
        snoozeAt = now + interval * 60_000L;
    }

    void dismissToday(long now, ZoneId zone) {
        snoozeAt = 0;
        firedDate = dayOf(now, zone);
        if (days == 0) enabled = false;
    }

    private long nextRegular(long now, ZoneId zone) {
        if (days == 0 && firedDate != null) return 0;
        LocalDate today = dayOf(now, zone);
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate day = today.plusDays(offset);
            int flag = 1 << (day.getDayOfWeek().getValue() - 1);
            if (day.equals(firedDate) || (days != 0 && (days & flag) == 0)) continue;
            long time = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
            if (time > now) return time;
        }
        return 0;
    }

    private static LocalDate dayOf(long time, ZoneId zone) {
        return Instant.ofEpochMilli(time).atZone(zone).toLocalDate();
    }
}
