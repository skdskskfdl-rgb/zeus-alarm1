package com.zeus.bossalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (AlarmScheduler.ACTION_ALARM.equals(a)) {
            String label = i.getStringExtra("label");
            AlarmService.start(c, label == null ? "보스 출현 임박" : label);
        } else {
            // 재부팅, 앱 업데이트, 정확한 알람 권한 변경 → 다시 예약
            AlarmScheduler.scheduleAll(c);
        }
    }
}
