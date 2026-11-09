package com.zeus.bossalarm;

import android.content.Context;
import android.webkit.JavascriptInterface;

/** 웹 화면(index.html)에서 window.Android 로 부르는 함수들. */
public class Bridge {
    private final Context ctx;

    Bridge(Context c) {
        ctx = c.getApplicationContext();
    }

    /** 앞으로 울릴 알람 목록(JSON)과 알람 방식/반복 설정을 받아 전부 다시 예약해요. */
    @JavascriptInterface
    public void sync(String json, String mode, int repeat) {
        AlarmScheduler.saveSettings(ctx, mode, repeat);
        AlarmScheduler.saveList(ctx, json);
        AlarmScheduler.scheduleAll(ctx);
    }

    @JavascriptInterface
    public void test(String mode, int repeat) {
        AlarmScheduler.saveSettings(ctx, mode, repeat);
        AlarmService.start(ctx, "🔔 알람 테스트");
    }

    @JavascriptInterface
    public int scheduledCount() {
        return ctx.getSharedPreferences(AlarmScheduler.PREFS, Context.MODE_PRIVATE).getInt("count", 0);
    }
}
