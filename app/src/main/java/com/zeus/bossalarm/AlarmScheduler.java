package com.zeus.bossalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AlarmScheduler {
    static final String PREFS = "alarm";
    static final String ACTION_ALARM = "com.zeus.bossalarm.ALARM";

    private AlarmScheduler() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void saveSettings(Context c, String mode, int repeat) {
        if (mode == null || mode.isEmpty()) mode = "both";
        prefs(c).edit().putString("mode", mode).putInt("repeat", repeat).apply();
    }

    static void saveList(Context c, String json) {
        prefs(c).edit().putString("list", json == null ? "[]" : json).apply();
    }

    private static PendingIntent pending(Context c, int idx, String label, int flags) {
        Intent it = new Intent(c, AlarmReceiver.class);
        it.setAction(ACTION_ALARM);
        if (label != null) it.putExtra("label", label);
        return PendingIntent.getBroadcast(c, 1000 + idx, it, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static synchronized void scheduleAll(Context c) {
        SharedPreferences p = prefs(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // 이전 예약 취소
        int old = p.getInt("count", 0);
        for (int i = 0; i < old; i++) {
            PendingIntent pi = pending(c, i, null, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }

        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent show = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long now = System.currentTimeMillis();
        int n = 0;
        try {
            JSONArray arr = new JSONArray(p.getString("list", "[]"));
            for (int i = 0; i < arr.length() && n < 100; i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.getLong("at");
                if (at <= now) continue;
                String label = o.optString("label", "보스 출현 임박");
                PendingIntent pi = pending(c, n, label, PendingIntent.FLAG_UPDATE_CURRENT);
                if (exact) {
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
                n++;
            }
        } catch (Exception ignored) {
        }
        p.edit().putInt("count", n).apply();
    }
}
