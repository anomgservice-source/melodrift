package com.melodrift.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {
    static OverlayService inst;
    static String title = "Melodrift";
    static boolean playing = false;
    private WindowManager wm;
    private LinearLayout root;
    private TextView tt, pp;
    private WindowManager.LayoutParams lp;
    private int sx, sy;
    private float tx, ty;

    @Override public IBinder onBind(Intent i) { return null; }

    public static void setInfo(String t, boolean p) {
        title = t == null ? "" : t;
        playing = p;
        final OverlayService s = inst;
        if (s != null) new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() { s.refresh(); } });
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (root == null) { try { build(); } catch (Exception e) { stopSelf(); } }
        return START_NOT_STICKY;
    }

    private TextView btn(String label, final String act, float d) {
        TextView b = new TextView(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(20);
        b.setPadding((int) (10 * d), (int) (4 * d), (int) (10 * d), (int) (4 * d));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (act.equals("X")) { stopSelf(); return; }
                if (PlaybackService.cb != null) PlaybackService.cb.on(act);
            }
        });
        return b;
    }

    private void build() {
        inst = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = 20; lp.y = 300;
        float d = getResources().getDisplayMetrics().density;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E6261A14")); bg.setCornerRadius(28 * d);
        root.setBackground(bg);
        root.setPadding((int) (14 * d), (int) (6 * d), (int) (8 * d), (int) (6 * d));
        tt = new TextView(this);
        tt.setTextColor(Color.WHITE); tt.setTextSize(13); tt.setMaxWidth((int) (110 * d));
        tt.setSingleLine(true); tt.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(tt);
        root.addView(btn("\u23EE", "PREV", d));
        pp = btn("\u25B6", "PLAY", d);
        root.addView(pp);
        root.addView(btn("\u23ED", "NEXT", d));
        root.addView(btn("\u2715", "X", d));
        root.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = lp.x; sy = lp.y; tx = e.getRawX(); ty = e.getRawY(); }
                else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    lp.x = sx + (int) (e.getRawX() - tx); lp.y = sy + (int) (e.getRawY() - ty);
                    wm.updateViewLayout(root, lp);
                }
                return true;
            }
        });
        wm.addView(root, lp);
        refresh();
    }

    private void refresh() {
        if (tt == null) return;
        tt.setText(title);
        pp.setText(playing ? "\u23F8" : "\u25B6");
    }

    @Override public void onDestroy() {
        try { if (root != null) wm.removeView(root); } catch (Exception e) { }
        root = null; inst = null;
        super.onDestroy();
    }
}
