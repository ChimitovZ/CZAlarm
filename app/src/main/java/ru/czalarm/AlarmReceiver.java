package ru.czalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (RingService.SNOOZE.equals(action)) {
            RingService.snooze(intent.getStringExtra("session"));
            return;
        }
        AlarmScheduler scheduler = new AlarmScheduler(context);
        if (AlarmScheduler.FIRE.equals(action)) {
            String id = intent.getStringExtra("id");
            if (scheduler.fire(id, intent.getLongExtra("at", 0))) {
                context.startForegroundService(new Intent(context, RingService.class).putExtra("id", id));
            }
        } else {
            scheduler.restore(!Intent.ACTION_BOOT_COMPLETED.equals(action));
        }
    }
}
