package com.example.beathouse.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.beathouse.App;
import com.example.beathouse.activities.LoginActivity;
import com.example.beathouse.activities.MainActivity;
import com.example.beathouse.R;
import com.example.beathouse.models.Beat;

public class AudioPlaybackService extends Service implements App.AppLifecycleListener {
    private static final String TAG = "AudioPlaybackService";
    private static final String CHANNEL_ID = "audio_playback_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_PLAY = "com.example.beathouse.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.beathouse.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.example.beathouse.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.example.beathouse.ACTION_NEXT";
    public static final String ACTION_STOP = "com.example.beathouse.ACTION_STOP";

    private MediaSessionCompat mediaSession;
    private final IBinder binder = new AudioBinder();
    private Beat currentBeat;
    private boolean isPlaying = false;

    public class AudioBinder extends Binder {
        public AudioPlaybackService getService() {
            return AudioPlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaSession = new MediaSessionCompat(this, "BeatHouseMediaSession");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                handleAction(ACTION_PLAY);
            }

            @Override
            public void onPause() {
                handleAction(ACTION_PAUSE);
            }

            @Override
            public void onSkipToNext() {
                handleAction(ACTION_NEXT);
            }

            @Override
            public void onSkipToPrevious() {
                handleAction(ACTION_PREVIOUS);
            }

            @Override
            public void onStop() {
                handleAction(ACTION_STOP);
            }
        });
        App.setLifecycleListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent.getAction());
        }
        return START_STICKY;
    }

    private void handleAction(String action) {
        Log.d(TAG, "handleAction: " + action);
        if (ACTION_STOP.equals(action)) {
            stopNotification();
            stopSelf();
        }

        Intent broadcastIntent = new Intent(action);
        broadcastIntent.setPackage(getPackageName());
        sendBroadcast(broadcastIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Audio playback notifications");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public void updateState(Beat beat, boolean playing) {
        this.currentBeat = beat;
        this.isPlaying = playing;
        showNotification(beat, playing);
    }

    public void showNotification(Beat beat, boolean isPlaying) {
        if (beat == null) {
            stopNotification();
            return;
        }

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap icon = null;
        if (beat.hasCover() && beat.getCoverImage() != null) {
            try {
                byte[] decodedBytes = Base64.decode(beat.getCoverImage(), Base64.DEFAULT);
                icon = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            } catch (Exception e) {
                Log.e(TAG, "Error decoding cover for notification", e);
            }
        }
        if (icon == null) {
            icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_music_note);
        }

        // Update MediaSession
        updateMediaSession(beat, isPlaying, icon);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(beat.getTitle())
                .setContentText(beat.getUserName())
                .setLargeIcon(icon)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setSilent(true)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        // Add actions
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_skip_previous, "Previous",
                getPendingIntent(ACTION_PREVIOUS)));

        if (isPlaying) {
            builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_pause, "Pause",
                    getPendingIntent(ACTION_PAUSE)));
        } else {
            builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_play_arrow, "Play",
                    getPendingIntent(ACTION_PLAY)));
        }

        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_skip_next, "Next",
                getPendingIntent(ACTION_NEXT)));

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateMediaSession(Beat beat, boolean isPlaying, Bitmap icon) {
        if (mediaSession == null) return;

        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, beat.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, beat.getUserName())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon)
                .build());

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_STOP);

        stateBuilder.setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setActive(true);
    }

    private PendingIntent getPendingIntent(String action) {
        Intent intent = new Intent(this, AudioPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void stopNotification() {
        stopForeground(true);
        if (mediaSession != null) {
            mediaSession.setActive(false);
        }
    }

    @Override
    public void onAppBackgrounded() {
        // Notification stays
    }

    @Override
    public void onAppForegrounded() {
        // Notification stays
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.release();
        }
        App.setLifecycleListener(null);
    }
}
