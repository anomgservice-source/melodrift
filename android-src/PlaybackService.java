package com.melodrift.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;

public class PlaybackService extends Service {
    public interface Cb { void on(String a); }
    public static Cb cb;
    static final int ID = 7;
    static final String CH = "melodrift_playback";

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private PendingIntent act(String a) {
        Intent i = new Intent(this, PlaybackService.class).setAction(a);
        int fl = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return Build.VERSION.SDK_INT >= 26 ? PendingIntent.getForegroundService(this, a.hashCode(), i, fl)
            : PendingIntent.getService(this, a.hashCode(), i, fl);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (a == null) { stopSelf(); return START_NOT_STICKY; }
        if (a.equals("HIDE")) { stopForeground(true); stopSelf(); return START_NOT_STICKY; }
        if (a.equals("PREV") || a.equals("PLAY") || a.equals("NEXT")) {
            if (cb != null) cb.on(a);
            return START_NOT_STICKY;
        }
        if (a.equals("SHOW")) show(intent);
        return START_NOT_STICKY;
    }

    private void show(Intent in) {
        String title = in.getStringExtra("title");
        String artist = in.getStringExtra("artist");
        String art = in.getStringExtra("art");
        boolean playing = in.getBooleanExtra("playing", false);
        Bitmap bmp = null;
        try {
            if (art != null && art.contains(",")) {
                byte[] b = Base64.decode(art.substring(art.indexOf(',') + 1), Base64.DEFAULT);
                bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
            }
        } catch (Exception e) { }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CH, "Lecture", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(bmp)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Prev", act("PREV"))
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pause" : "Play", act("PLAY"))
            .addAction(android.R.drawable.ic_media_next, "Next", act("NEXT"))
            .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2));
        Intent li = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (li != null) b.setContentIntent(PendingIntent.getActivity(this, 0, li, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(ID, n);
        if (!playing) stopForeground(false);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
}
