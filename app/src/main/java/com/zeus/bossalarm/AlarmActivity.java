package com.zeus.bossalarm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 잠금화면 위에 뜨는 알람 창. */
public class AlarmActivity extends Activity {
    static final String ACTION_UPDATE = "com.zeus.bossalarm.UPDATE";
    static final String ACTION_CLOSE = "com.zeus.bossalarm.CLOSE";

    private TextView who;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (ACTION_CLOSE.equals(i.getAction())) {
                finish();
            } else {
                refresh();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(0x1a, 0x10, 0x0e));
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("⏰ 보스 출현 임박!");
        title.setTextColor(Color.rgb(0xf1, 0xd7, 0x9a));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        who = new TextView(this);
        who.setTextColor(Color.rgb(0xec, 0xe6, 0xda));
        who.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        who.setGravity(Gravity.CENTER);
        who.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(24), 0, dp(40));
        root.addView(who, lp);

        Button ok = new Button(this);
        ok.setText("확인 (알람 끄기)");
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        ok.setTextColor(Color.rgb(0x1a, 0x14, 0x08));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0xd8, 0xb4, 0x6a));
        bg.setCornerRadius(dp(14));
        ok.setBackground(bg);
        ok.setOnClickListener(v -> {
            AlarmService.stop(this);
            finish();
        });
        root.addView(ok, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)));

        setContentView(root);
        refresh();

        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_UPDATE);
        f.addAction(ACTION_CLOSE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AlarmService.running) finish();
        else refresh();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void refresh() {
        if (who != null) who.setText(AlarmService.joined());
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
