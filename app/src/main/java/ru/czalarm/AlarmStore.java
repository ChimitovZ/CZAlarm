package ru.czalarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class AlarmStore {
    private final SharedPreferences prefs;

    AlarmStore(Context context) {
        prefs = context.createDeviceProtectedStorageContext()
                .getSharedPreferences("alarms", Context.MODE_PRIVATE);
    }

    List<Alarm> read() {
        String saved = prefs.getString("list", null);
        List<Alarm> alarms = new ArrayList<>();
        if (saved == null) {
            alarms.add(new Alarm("home", "Из дома", 8, 45, 0b0001111, 10, true));
            alarms.add(new Alarm("office", "В офис", 8, 0, 0b0010000, 10, true));
            write(alarms);
            return alarms;
        }
        try {
            JSONArray list = new JSONArray(saved);
            for (int i = 0; i < list.length(); i++) {
                JSONObject row = list.getJSONObject(i);
                Alarm alarm = new Alarm(row.getString("id"), row.getString("label"),
                        row.getInt("hour"), row.getInt("minute"), row.getInt("days"),
                        row.getInt("interval"), row.getBoolean("enabled"));
                if (row.has("firedDate")) alarm.firedDate = LocalDate.parse(row.getString("firedDate"));
                alarm.snoozeAt = row.optLong("snoozeAt");
                alarm.armedAt = row.optLong("armedAt");
                alarms.add(alarm);
            }
            return alarms;
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot read saved alarms", e);
        }
    }

    void write(List<Alarm> alarms) {
        JSONArray list = new JSONArray();
        try {
            for (Alarm alarm : alarms) {
                JSONObject row = new JSONObject()
                        .put("id", alarm.id).put("label", alarm.label)
                        .put("hour", alarm.hour).put("minute", alarm.minute).put("days", alarm.days)
                        .put("interval", alarm.interval).put("enabled", alarm.enabled)
                        .put("snoozeAt", alarm.snoozeAt).put("armedAt", alarm.armedAt);
                if (alarm.firedDate != null) row.put("firedDate", alarm.firedDate.toString());
                list.put(row);
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot serialize alarms", e);
        }
        prefs.edit().putString("list", list.toString()).apply();
    }

    static Alarm find(List<Alarm> alarms, String id) {
        for (Alarm alarm : alarms) if (alarm.id.equals(id)) return alarm;
        return null;
    }
}
