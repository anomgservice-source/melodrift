package com.melodrift.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
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

@CapacitorPlugin(name = "MediaScanner", permissions = {
    @Permission(alias = "media", strings = {Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO}),
    @Permission(alias = "legacy", strings = {Manifest.permission.READ_EXTERNAL_STORAGE})
})
public class MediaScannerPlugin extends Plugin {
    private String alias() { return Build.VERSION.SDK_INT >= 33 ? "media" : "legacy"; }

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
        String[] vp = {MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.DATE_ADDED};
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
}
