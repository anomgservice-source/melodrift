package com.melodrift.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

@CapacitorPlugin(name = "MediaScanner", permissions = {
    @Permission(alias = "media", strings = {Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO}),
    @Permission(alias = "legacy", strings = {Manifest.permission.READ_EXTERNAL_STORAGE}),
    @Permission(alias = "legacy10", strings = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}),
    @Permission(alias = "notif", strings = {Manifest.permission.POST_NOTIFICATIONS})
})
public class MediaScannerPlugin extends Plugin {
    private PluginCall delCall;
    private PluginCall trashCall;

    @Override
    public void load() {
        PlaybackService.cb = new PlaybackService.Cb() {
            public void on(String a) {
                JSObject o = new JSObject();
                o.put("a", a);
                notifyListeners("media", o);
            }
        };
    }

    private String alias() { return Build.VERSION.SDK_INT >= 33 ? "media" : (Build.VERSION.SDK_INT >= 30 ? "legacy" : "legacy10"); }

    @PluginMethod
    public void scan(PluginCall call) {
        if (getPermissionState(alias()) != PermissionState.GRANTED) {
            requestPermissionForAlias(alias(), call, "permCb");
            return;
        }
        doScan(call);
    }

    @PermissionCallback
    private void permCb(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) doScan(call);
        else call.reject("denied");
    }

    private void doScan(PluginCall call) {
        JSArray audio = new JSArray();
        JSArray video = new JSArray();
        ContentResolver r = getContext().getContentResolver();
        String[] ap = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED};
        try (Cursor c = r.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ap,
                MediaStore.Audio.Media.DURATION + ">=30000", null, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext()) {
                JSObject o = new JSObject();
                o.put("id", c.getLong(0));
                o.put("title", c.getString(1));
                o.put("artist", c.getString(2));
                o.put("album", c.getString(3));
                o.put("path", c.getString(4));
                o.put("dur", c.getLong(5));
                o.put("added", c.getLong(6));
                audio.put(o);
            }
        } catch (Exception e) { }
        String[] vp = {MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.DATE_ADDED};
        try (Cursor c = r.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vp, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext()) {
                JSObject o = new JSObject();
                o.put("id", c.getLong(0));
                o.put("title", c.getString(1));
                o.put("path", c.getString(2));
                o.put("dur", c.getLong(3));
                o.put("h", c.getLong(4));
                o.put("added", c.getLong(5));
                video.put(o);
            }
        } catch (Exception e) { }
        JSObject res = new JSObject();
        res.put("audio", audio);
        res.put("video", video);
        call.resolve(res);
    }

    @PluginMethod
    public void getArt(PluginCall call) {
        if (Build.VERSION.SDK_INT < 29) { call.reject("na"); return; }
        long id = call.getData().optLong("id");
        boolean vid = call.getData().optBoolean("video", false);
        int sz = call.getData().optInt("size", 160);
        Uri base = vid ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        try {
            Bitmap b = getContext().getContentResolver().loadThumbnail(ContentUris.withAppendedId(base, id), new Size(sz, sz), null);
            ByteArrayOutputStream s = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 80, s);
            JSObject o = new JSObject();
            o.put("data", "data:image/jpeg;base64," + Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP));
            call.resolve(o);
        } catch (Exception e) { call.reject("none"); }
    }

    @PluginMethod
    public void notifPerm(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notif") != PermissionState.GRANTED) {
            requestPermissionForAlias("notif", call, "notifCb");
        } else call.resolve();
    }

    @PermissionCallback
    private void notifCb(PluginCall call) { call.resolve(); }

    @PluginMethod
    public void showNotif(PluginCall call) {
        Intent i = new Intent(getContext(), PlaybackService.class).setAction("SHOW");
        i.putExtra("title", call.getString("title", ""));
        i.putExtra("artist", call.getString("artist", ""));
        i.putExtra("playing", call.getBoolean("playing", false));
        i.putExtra("art", call.getString("art", ""));
        try {
            if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i);
            else getContext().startService(i);
            call.resolve();
        } catch (Exception e) { call.reject("notif"); }
    }

    @PluginMethod
    public void reindex(PluginCall call) {
        HashSet<String> known = new HashSet<String>();
        try {
            JSArray k = call.getArray("known");
            if (k != null) for (int i = 0; i < k.length(); i++) known.add(k.getString(i));
        } catch (Exception e) { }
        ArrayList<String> found = new ArrayList<String>();
        File root = Environment.getExternalStorageDirectory();
        String[] dirs = {"Download", "Music", "Movies", "Documents", "DCIM", "Recordings"};
        for (String d : dirs) walk(new File(root, d), known, found, 0);
        JSObject o = new JSObject();
        o.put("n", found.size());
        if (found.isEmpty()) { call.resolve(o); return; }
        final CountDownLatch l = new CountDownLatch(found.size());
        MediaScannerConnection.scanFile(getContext(), found.toArray(new String[0]), null,
            new MediaScannerConnection.OnScanCompletedListener() {
                public void onScanCompleted(String p, Uri u) { l.countDown(); }
            });
        try { l.await(6, TimeUnit.SECONDS); } catch (Exception e) { }
        call.resolve(o);
    }

    private void walk(File f, HashSet<String> known, ArrayList<String> found, int depth) {
        if (depth > 6 || !f.exists()) return;
        File[] a = f.listFiles();
        if (a == null) return;
        for (File x : a) {
            if (x.isDirectory()) {
                if (!x.getName().startsWith(".")) walk(x, known, found, depth + 1);
            } else {
                String n = x.getName().toLowerCase();
                if (n.matches(".*\\.(mp3|m4a|aac|wav|flac|ogg|opus|wma|mp4|mkv|3gp|webm|avi|mov)$")
                        && !known.contains(x.getAbsolutePath()) && found.size() < 3000) found.add(x.getAbsolutePath());
            }
        }
    }

    @PluginMethod
    public void deleteMedia(PluginCall call) {
        long id = call.getData().optLong("id");
        boolean vid = call.getData().optBoolean("video", false);
        Uri u = ContentUris.withAppendedId(vid ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                PendingIntent pi = MediaStore.createDeleteRequest(getContext().getContentResolver(), Collections.singletonList(u));
                delCall = call;
                getBridge().saveCall(call);
                getActivity().startIntentSenderForResult(pi.getIntentSender(), 4711, null, 0, 0, 0);
            } else {
                int n = getContext().getContentResolver().delete(u, null, null);
                JSObject o = new JSObject();
                o.put("ok", n > 0);
                call.resolve(o);
            }
        } catch (Exception e) { call.reject("del"); }
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        if (requestCode == 4712 && trashCall != null) {
            JSObject tr = new JSObject();
            tr.put("ok", resultCode == Activity.RESULT_OK);
            trashCall.resolve(tr);
            trashCall = null;
            return;
        }
        if (requestCode == 4711 && delCall != null) {
            JSObject o = new JSObject();
            o.put("ok", resultCode == Activity.RESULT_OK);
            delCall.resolve(o);
            delCall = null;
        }
    }

    @PluginMethod
    public void shareFile(PluginCall call) {
        long id = call.getData().optLong("id");
        boolean vid = call.getData().optBoolean("video", false);
        Uri u = ContentUris.withAppendedId(vid ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(vid ? "video/*" : "audio/*");
            i.putExtra(Intent.EXTRA_STREAM, u);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(Intent.createChooser(i, call.getString("title", "Melodrift")));
            call.resolve();
        } catch (Exception e) { call.reject("share"); }
    }

    @PluginMethod
    public void setRingtone(PluginCall call) {
        long id = call.getData().optLong("id");
        Uri u = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            android.content.Context c = getContext();
            JSObject o = new JSObject();
            if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(c)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                o.put("perm", true);
                call.resolve(o);
                return;
            }
            RingtoneManager.setActualDefaultRingtoneUri(c, RingtoneManager.TYPE_RINGTONE, u);
            o.put("ok", true);
            call.resolve(o);
        } catch (Exception e) { call.reject("ringtone"); }
    }

    @PluginMethod
    public void trashMedia(PluginCall call) {
        long id = call.getData().optLong("id");
        boolean vid = call.getData().optBoolean("video", false);
        boolean undo = call.getData().optBoolean("undo", false);
        Uri u = ContentUris.withAppendedId(vid ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                PendingIntent pi = MediaStore.createTrashRequest(getContext().getContentResolver(), Collections.singletonList(u), !undo);
                trashCall = call;
                getBridge().saveCall(call);
                getActivity().startIntentSenderForResult(pi.getIntentSender(), 4712, null, 0, 0, 0);
            } else {
                JSObject o = new JSObject();
                o.put("unsupported", true);
                call.resolve(o);
            }
        } catch (Exception e) { call.reject("trash"); }
    }

    @PluginMethod
    public void listTrashed(PluginCall call) {
        boolean vid = call.getData().optBoolean("video", false);
        JSArray arr = new JSArray();
        if (Build.VERSION.SDK_INT >= 30) {
            Uri base = vid ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Bundle b = new Bundle();
            b.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY);
            String[] proj = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.DATE_EXPIRES};
            try (Cursor c = getContext().getContentResolver().query(base, proj, b, null)) {
                while (c != null && c.moveToNext()) {
                    JSObject o = new JSObject();
                    o.put("id", c.getLong(0));
                    o.put("title", c.getString(1));
                    o.put("exp", c.getLong(2));
                    arr.put(o);
                }
            } catch (Exception e) { }
        }
        JSObject res = new JSObject();
        res.put("items", arr);
        call.resolve(res);
    }

    @PluginMethod
    public void hideNotif(PluginCall call) {
        try {
            getContext().startService(new Intent(getContext(), PlaybackService.class).setAction("HIDE"));
            call.resolve();
        } catch (Exception e) { call.reject("hide"); }
    }
}
