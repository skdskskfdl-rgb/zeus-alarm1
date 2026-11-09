package com.zeus.bossalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.ArrayList;

/** 알람이 울리는 동안 소리/진동을 내는 서비스. */
public class AlarmService extends Service {
    static final String CHANNEL = "boss_alarm_ring";
    static final String CHANNEL_MISSED = "boss_alarm_missed";
    static final String ACTION_STOP = "com.zeus.bossalarm.STOP";
    static final int NOTI_RING = 1;
    static final int NOTI_MISSED = 2;
    static volatile boolean running = false;
    static final ArrayList<String> labels = new ArrayList<>();

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context c, String label) {
        Intent i = new Intent(c, AlarmService.class);
        i.putExtra("label", label);
        c.startForegroundService(i);
    }

    static void stop(Context c) {
        Intent i = new Intent(c, AlarmService.class);
        i.setAction(ACTION_STOP);
        c.startService(i);
    }

    static String joined() {
        synchronized (labels) {
            return labels.isEmpty() ? "보스 출현 임박" : String.join("\n", labels);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannels(this);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            // 포그라운드 시작 의무를 지키기 위해 잠깐 올렸다가 바로 정리
            if (!running) {
                startFg();
            }
            stopAll(false);
            return START_NOT_STICKY;
        }
        String label = intent == null ? null : intent.getStringExtra("label");
        if (label == null) label = "보스 출현 임박";
        synchronized (labels) {
            labels.add(label);
        }
        running = true;
        startFg();
        acquireWake();
        startRinging();
        openScreen();
        return START_NOT_STICKY;
    }

    private void startFg() {
        Notification n = buildRingNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTI_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTI_RING, n);
        }
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "보스 알람 (울림)", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("보스 출현 전 울리는 알람");
            ch.setSound(null, null); // 소리·진동은 앱이 직접 냄
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.setBypassDnd(true);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_MISSED) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_MISSED, "보스 알람 (지난 알림)", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildRingNotification() {
        String text = joined();
        Intent stopI = new Intent(this, AlarmService.class);
        stopI.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent act = new Intent(this, AlarmActivity.class);
        act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent full = PendingIntent.getActivity(this, 2, act,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Action stopAction = new Notification.Action.Builder(
                (Icon) null, "확인 (알람 끄기)", stopPi).build();

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("⏰ 보스 출현 임박!")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setDeleteIntent(stopPi)
                .addAction(stopAction)
                .build();
    }

    private void acquireWake() {
        try {
            if (wakeLock == null) {
                PowerManager pm = getSystemService(PowerManager.class);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bossalarm:ring");
            }
            if (!wakeLock.isHeld()) wakeLock.acquire(4 * 60 * 1000L);
        } catch (Exception ignored) {
        }
    }

    private void openScreen() {
        try {
            Intent act = new Intent(this, AlarmActivity.class);
            act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(act);
        } catch (Exception ignored) {
            // 백그라운드 실행 제한 시 전체화면 알림/알림창으로 대신 표시됨
        }
        // 화면에 떠 있는 알람 창 내용 갱신
        sendBroadcast(new Intent(AlarmActivity.ACTION_UPDATE).setPackage(getPackageName()));
    }

    private void startRinging() {
        SharedPreferences p = AlarmScheduler.prefs(this);
        String mode = p.getString("mode", "both");
        boolean untilConfirm = p.getInt("repeat", 1) == 1;

        stopMedia();

        if (!"vibe".equals(mode)) {
            try {
                Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                player.setDataSource(this, u);
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception e) {
                if (player != null) {
                    try { player.release(); } catch (Exception ignored) { }
                }
                player = null;
            }
        }

        if (!"sound".equals(mode)) {
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    VibratorManager vm = getSystemService(VibratorManager.class);
                    vibrator = vm.getDefaultVibrator();
                } else {
                    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                }
                long[] pattern = {0, 600, 300, 600, 300, 600, 900};
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, untilConfirm ? 0 : -1);
                if (Build.VERSION.SDK_INT >= 33) {
                    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
                } else {
                    vibrator.vibrate(effect, new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM).build());
                }
            } catch (Exception ignored) {
            }
        }

        handler.removeCallbacksAndMessages(null);
        // 확인할 때까지: 최대 3분 울림 / 한 번만: 약 8초
        long dur = untilConfirm ? 3 * 60 * 1000L : 8000L;
        handler.postDelayed(() -> stopAll(true), dur);
    }

    private void stopMedia() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) { }
            vibrator = null;
        }
    }

    private void stopAll(boolean timedOut) {
        handler.removeCallbacksAndMessages(null);
        stopMedia();
        String text = joined();
        synchronized (labels) {
            labels.clear();
        }
        running = false;
        if (timedOut) postMissed(text);
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        sendBroadcast(new Intent(AlarmActivity.ACTION_CLOSE).setPackage(getPackageName()));
    }

    /** 확인 없이 끝난 알람은 알림창에 남겨 둬요. */
    private void postMissed(String text) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 3, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(this, CHANNEL_MISSED)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("⚔️ 보스 출현 알림")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(NOTI_MISSED, n);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopMedia();
        running = false;
        super.onDestroy();
    }
}
