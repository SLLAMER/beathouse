package com.example.beathouse;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.adapters.BeatsAdapter;
import com.example.beathouse.models.Beat;

public class PlayerActivity extends BaseActivity {
    private static final String TAG = "PlayerActivity";

    private ImageView ivCover;
    private TextView tvTitle, tvProducer, tvCurrentTime, tvTotalTime;
    private ImageButton btnPlay, btnPrevious, btnNext, btnClose;
    private SeekBar seekBar;

    private Beat currentBeat;
    private BeatsAdapter beatsAdapter;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isPlaying = false;
    private boolean isSeekBarTracking = false;
    private boolean isProgressUpdateRunning = false;

    private static BeatsAdapter staticBeatsAdapter;

    public static void setBeatsAdapter(BeatsAdapter adapter) {
        staticBeatsAdapter = adapter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initViews();
        setupListeners();
        setupProgressUpdates();
        getBeatsAdapter();
        handleIntent(getIntent());
        applyGlassEffect();
    }

    private void applyGlassEffect() {
        // Blur removed from cover as per user request for clarity.
        // Keeping the method for potential future background glassmorphism.
    }

    private void getBeatsAdapter() {
        if (staticBeatsAdapter != null) {
            beatsAdapter = staticBeatsAdapter;
        } else {
            beatsAdapter = BeatsAdapter.getInstance();
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null || beatsAdapter == null) return;

        String beatId = intent.getStringExtra("BEAT_ID");
        boolean playing = intent.getBooleanExtra("IS_PLAYING", false);
        String beatTitle = intent.getStringExtra("BEAT_TITLE");
        String producerName = intent.getStringExtra("PRODUCER_NAME");
        String coverData = intent.getStringExtra("COVER_DATA");
        int currentPosition = intent.getIntExtra("CURRENT_POSITION", 0);
        int duration = intent.getIntExtra("DURATION", 0);

        // Берем актуальный beat из адаптера
        currentBeat = beatsAdapter.getCurrentlyPlayingBeat();
        if (currentBeat == null && beatId != null) {
            currentBeat = new Beat();
            currentBeat.setId(beatId);
            currentBeat.setTitle(beatTitle != null ? beatTitle : "Unknown");
            currentBeat.setUserName(producerName != null ? producerName : "Unknown");
            if (coverData != null) currentBeat.setCoverImage(coverData);
        }

        isPlaying = playing;
        updateUI();
        updatePlayButton();

        // ✅ Синхронизация дорожки
        if (duration > 0 && seekBar != null) {
            seekBar.setMax(100);
            seekBar.setProgress((currentPosition * 100) / duration);
            tvCurrentTime.setText(formatTime(currentPosition));
            tvTotalTime.setText(formatTime(duration));
        }

        if (isPlaying) startProgressUpdates();
    }

    private void initViews() {
        ivCover = findViewById(R.id.ivCover);
        tvTitle = findViewById(R.id.tvTitle);
        tvProducer = findViewById(R.id.tvProducer);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        btnPlay = findViewById(R.id.btnPlay);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        btnClose = findViewById(R.id.btnClose);
        seekBar = findViewById(R.id.seekBar);
        progressHandler = new Handler(Looper.getMainLooper());
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.slide_down);
        });

        btnPlay.setOnClickListener(v -> togglePlayback());

        btnPrevious.setOnClickListener(v -> {
            if (beatsAdapter != null) {
                beatsAdapter.safePlayPreviousBeat();
                syncState();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (beatsAdapter != null) {
                beatsAdapter.safePlayNextBeat();
                syncState();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarTracking = true;
                stopProgressUpdates();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeekBarTracking = false;
                if (beatsAdapter != null) {
                    int duration = beatsAdapter.getPlaybackDuration();
                    if (duration > 0) {
                        int pos = (seekBar.getProgress() * duration) / 100;
                        beatsAdapter.seekTo(pos);
                    }
                    if (isPlaying) startProgressUpdates();
                }
            }
        });
    }

    private void togglePlayback() {
        if (beatsAdapter == null || currentBeat == null) return;

        if (beatsAdapter.isPlaying()) {
            // ПАУЗА
            beatsAdapter.pausePlayback();
            isPlaying = false;
            updatePlayButton();
            stopProgressUpdates();
        } else {
            // ВОСПРОИЗВЕДЕНИЕ - используем playBeatAudio
            int pos = beatsAdapter.findBeatPosition(currentBeat.getId());
            if (pos >= 0) {
                beatsAdapter.playBeatAudio(currentBeat, pos, null);
                isPlaying = true;
                updatePlayButton();
                startProgressUpdates();
            }
        }
    }


    private void syncState() {
        if (beatsAdapter == null) return;
        new Handler().postDelayed(() -> {
            currentBeat = beatsAdapter.getCurrentlyPlayingBeat();
            isPlaying = beatsAdapter.isPlaying();
            updateUI();
            updatePlayButton();
            if (isPlaying) startProgressUpdates();
        }, 300);
    }

    private void setupProgressUpdates() {
        progressRunnable = () -> {
            if (isProgressUpdateRunning && !isSeekBarTracking && beatsAdapter != null && beatsAdapter.isPlaying()) {
                int pos = beatsAdapter.getCurrentPlaybackPosition();
                int dur = beatsAdapter.getPlaybackDuration();
                if (dur > 0) {
                    seekBar.setProgress((pos * 100) / dur);
                    tvCurrentTime.setText(formatTime(pos));
                    tvTotalTime.setText(formatTime(dur));
                }
            }
            if (isProgressUpdateRunning) {
                progressHandler.postDelayed(progressRunnable, 1000);
            }
        };
    }

    private void startProgressUpdates() {
        if (!isProgressUpdateRunning) {
            isProgressUpdateRunning = true;
            progressHandler.post(progressRunnable);
        }
    }

    private void stopProgressUpdates() {
        isProgressUpdateRunning = false;
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void updateUI() {
        if (currentBeat == null) return;
        tvTitle.setText(currentBeat.getTitle());
        tvProducer.setText("by " + currentBeat.getUserName());
        if (currentBeat.hasCover() && currentBeat.getCoverImage() != null) {
            try {
                byte[] d = Base64.decode(currentBeat.getCoverImage(), Base64.DEFAULT);
                Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
                if (bm != null) { ivCover.setImageBitmap(bm); return; }
            } catch (Exception e) {}
        }
        ivCover.setImageResource(R.drawable.ic_music_note);
    }

    private void updatePlayButton() {
        btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
    }

    private String formatTime(int ms) {
        int sec = (ms / 1000) % 60;
        int min = (ms / 60000) % 60;
        return String.format("%d:%02d", min, sec);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (beatsAdapter != null) {
            currentBeat = beatsAdapter.getCurrentlyPlayingBeat();
            isPlaying = beatsAdapter.isPlaying();
            updateUI();
            updatePlayButton();
            if (isPlaying) startProgressUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopProgressUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdates();
        progressHandler.removeCallbacksAndMessages(null);
        // staticBeatsAdapter = null; // ✅ Не обнуляем, чтобы сохранить связь
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down);
    }
}