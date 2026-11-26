package com.melodrift.app;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Capacitor bridge for Media3. This is the native engine API for phase 1.
 * HTML player wiring is intentionally kept separate until its legacy
 * HTMLAudioElement dependencies are migrated and tested.
 */
@CapacitorPlugin(name = "Media3Player")
public class Media3BridgePlugin extends Plugin {
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private Player.Listener playerListener;

    @Override
    public void load() {
        SessionToken token = new SessionToken(
            getContext(),
            new android.content.ComponentName(getContext(), Media3PlaybackService.class)
        );
        controllerFuture = new MediaController.Builder(getContext(), token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                playerListener = new Player.Listener() {
                    @Override public void onEvents(Player player, Player.Events events) {
                        emitState();
                    }
                };
                controller.addListener(playerListener);
                emitState();
            } catch (Exception e) {
                notifyListeners("playerError", error("connect", e));
            }
        }, command -> new Handler(Looper.getMainLooper()).post(command));
    }

    private JSObject error(String operation, Exception e) {
        JSObject out = new JSObject();
        out.put("operation", operation);
        out.put("message", e == null ? "unknown" : String.valueOf(e.getMessage()));
        return out;
    }

    private MediaController requireController(PluginCall call) {
        if (controller == null) {
            call.reject("Media3 controller is not connected yet. Retry shortly.");
            return null;
        }
        return controller;
    }

    private void emitState() {
        if (controller == null) return;
        JSObject state = new JSObject();
        state.put("isPlaying", controller.isPlaying());
        state.put("playWhenReady", controller.getPlayWhenReady());
        state.put("position", controller.getCurrentPosition());
        state.put("duration", controller.getDuration());
        state.put("currentIndex", controller.getCurrentMediaItemIndex());
        state.put("mediaCount", controller.getMediaItemCount());
        state.put("playbackState", controller.getPlaybackState());
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null && item.mediaMetadata != null) {
            state.put("title", item.mediaMetadata.title == null ? "" : item.mediaMetadata.title.toString());
            state.put("artist", item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString());
        }
        notifyListeners("playerState", state);
    }

    @PluginMethod
    public void loadQueue(PluginCall call) {
        MediaController c = requireController(call);
        if (c == null) return;
        JSArray input = call.getArray("items");
        if (input == null || input.length() == 0) {
            call.reject("items must contain at least one track");
            return;
        }
        try {
            List<MediaItem> items = new ArrayList<>();
            for (int i = 0; i < input.length(); i++) {
                org.json.JSONObject row = input.getJSONObject(i);
                String uriText = row.optString("uri", "");
                if (uriText.isEmpty() && row.has("id")) {
                    uriText = "content://media/external/audio/media/" + row.optLong("id");
                }
                if (uriText.isEmpty()) throw new IllegalArgumentException("Track " + i + " has no uri or MediaStore id");
                MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(row.optString("title", "Unknown title"))
                    .setArtist(row.optString("artist", "Unknown artist"))
                    .setAlbumTitle(row.optString("album", ""))
                    .build();
                items.add(new MediaItem.Builder().setUri(Uri.parse(uriText)).setMediaMetadata(metadata).build());
            }
            int startIndex = Math.max(0, Math.min(call.getData().optInt("startIndex", 0), items.size() - 1));
            c.setMediaItems(items, startIndex, 0L);
            c.prepare();
            Boolean autoplay = call.getBoolean("autoplay", true);
            if (Boolean.TRUE.equals(autoplay)) c.play(); else c.pause();
            call.resolve();
        } catch (Exception e) {
            call.reject("Unable to load Media3 queue: " + e.getMessage(), e);
        }
    }

    @PluginMethod public void play(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        c.play(); call.resolve();
    }
    @PluginMethod public void pause(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        c.pause(); call.resolve();
    }
    @PluginMethod public void next(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        c.seekToNextMediaItem(); call.resolve();
    }
    @PluginMethod public void previous(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        c.seekToPreviousMediaItem(); call.resolve();
    }
    @PluginMethod public void seekTo(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        c.seekTo(Math.max(0L, call.getData().optLong("position", 0L))); call.resolve();
    }
    @PluginMethod public void setVolume(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        float volume = (float) call.getData().optDouble("volume", 1.0);
        c.setVolume(Math.max(0f, Math.min(1f, volume)));
        call.resolve();
    }
    @PluginMethod public void setSpeed(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        float speed = (float) call.getData().optDouble("speed", 1.0);
        if (speed < 0.25f || speed > 3f) { call.reject("speed must be between 0.25 and 3"); return; }
        c.setPlaybackSpeed(speed); call.resolve();
    }
    @PluginMethod public void stop(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        c.pause(); c.clearMediaItems(); c.stop(); call.resolve();
    }
    @PluginMethod public void getState(PluginCall call) {
        MediaController c = requireController(call); if (c == null) return;
        JSObject state = new JSObject();
        state.put("isPlaying", c.isPlaying());
        state.put("playWhenReady", c.getPlayWhenReady());
        state.put("position", c.getCurrentPosition());
        state.put("duration", c.getDuration());
        state.put("currentIndex", c.getCurrentMediaItemIndex());
        state.put("mediaCount", c.getMediaItemCount());
        state.put("playbackState", c.getPlaybackState());
        call.resolve(state);
    }

    @Override
    protected void handleOnDestroy() {
        if (controller != null && playerListener != null) controller.removeListener(playerListener);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        controller = null;
        super.handleOnDestroy();
    }
}
