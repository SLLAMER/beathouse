// BuyerBeatDetailActivity.java
package com.example.beathouse;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.models.Beat;
import com.example.beathouse.utils.AudioUtils;
import com.example.beathouse.utils.CartManager;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.io.File;

public class BuyerBeatDetailActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private ImageView ivCover;
    private TextView tvTitle, tvProducer, tvBpm, tvKey, tvGenre, tvPrice, tvDescription,
            tvLicense, tvCurrentTime, tvTotalTime;
    private ImageButton btnPlay, btnPrevious, btnNext;
    private SeekBar seekBar;
    private MaterialButton btnAddToCart, btnBuyNow;
    private ChipGroup chipGroupTags;
    private View progressBar;

    private FirestoreHelper firestoreHelper;
    private CartManager cartManager;
    private Beat currentBeat;
    private MediaPlayer mediaPlayer;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isPlaying = false;
    private boolean isSeekBarTracking = false;
    private static final String TAG = "BuyerBeatDetailActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_beat_detail);

        initViews();
        setupListeners();
        setupProgressUpdates();

        firestoreHelper = new FirestoreHelper();
        cartManager = new CartManager(this);
        progressHandler = new Handler(Looper.getMainLooper());

        String beatId = getIntent().getStringExtra("beat_id");
        if (beatId != null) {
            loadBeatData(beatId);
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivCover = findViewById(R.id.ivCover);
        tvTitle = findViewById(R.id.tvTitle);
        tvProducer = findViewById(R.id.tvProducer);
        tvBpm = findViewById(R.id.tvBpm);
        tvKey = findViewById(R.id.tvKey);
        tvGenre = findViewById(R.id.tvGenre);
        tvPrice = findViewById(R.id.tvPrice);
        tvDescription = findViewById(R.id.tvDescription);
        tvLicense = findViewById(R.id.tvLicense);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        btnPlay = findViewById(R.id.btnPlay);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        seekBar = findViewById(R.id.seekBar);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        btnBuyNow = findViewById(R.id.btnBuyNow);
        chipGroupTags = findViewById(R.id.chipGroupTags);
        progressBar = findViewById(R.id.progressBar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        btnPlay.setOnClickListener(v -> togglePlayback());

        btnAddToCart.setOnClickListener(v -> addToCart());

        btnBuyNow.setOnClickListener(v -> buyNow());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    int newPosition = (progress * mediaPlayer.getDuration()) / 100;
                    tvCurrentTime.setText(formatTime(newPosition));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarTracking = true;
                stopProgressUpdates();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeekBarTracking = false;
                if (mediaPlayer != null) {
                    int progress = seekBar.getProgress();
                    int newPosition = (progress * mediaPlayer.getDuration()) / 100;
                    mediaPlayer.seekTo(newPosition);
                    if (isPlaying) {
                        startProgressUpdates();
                    }
                }
            }
        });
    }

    private void loadBeatData(String beatId) {
        progressBar.setVisibility(View.VISIBLE);

        firestoreHelper.loadFullBeatAudio(beatId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                // Получаем бит из Firestore
                firestoreHelper.getBeatsByIds(java.util.Collections.singletonList(beatId),
                        new FirestoreHelper.FirestoreCallback() {
                            @Override
                            public void onSuccess(Object beatsResult) {
                                java.util.List<Beat> beats = (java.util.List<Beat>) beatsResult;
                                if (beats != null && !beats.isEmpty()) {
                                    currentBeat = beats.get(0);
                                    String audioBase64 = (String) result;
                                    if (audioBase64 != null) {
                                        currentBeat.setFullAudio(audioBase64);
                                    }
                                    runOnUiThread(() -> {
                                        progressBar.setVisibility(View.GONE);
                                        updateUI();
                                        prepareAudio();
                                    });
                                }
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(BuyerBeatDetailActivity.this,
                                            "Error loading beat", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BuyerBeatDetailActivity.this,
                            "Error loading audio", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateUI() {
        if (currentBeat == null) return;

        toolbar.setTitle(currentBeat.getTitle());
        tvTitle.setText(currentBeat.getTitle());
        tvProducer.setText("by " + currentBeat.getUserName());
        tvBpm.setText(currentBeat.getBpm() + " BPM");
        tvKey.setText(currentBeat.getKey());
        tvGenre.setText(currentBeat.getGenre());
        tvPrice.setText(currentBeat.getFormattedPrice());
        tvDescription.setText(currentBeat.getDescription() != null && !currentBeat.getDescription().isEmpty()
                ? currentBeat.getDescription() : "No description");
        tvLicense.setText(currentBeat.getLicenseTypeFormatted());

        // Загрузка обложки
        if (currentBeat.hasCover() && currentBeat.getCoverImage() != null) {
            loadCover(currentBeat.getCoverImage());
        }

        // Теги
        if (currentBeat.getTags() != null && !currentBeat.getTags().isEmpty()) {
            chipGroupTags.removeAllViews();
            for (String tag : currentBeat.getTags()) {
                Chip chip = new Chip(this);
                chip.setText("#" + tag);
                chip.setChipBackgroundColorResource(R.color.surface_variant);
                chip.setTextColor(getColor(R.color.on_surface_variant));
                chipGroupTags.addView(chip);
            }
            chipGroupTags.setVisibility(View.VISIBLE);
        } else {
            chipGroupTags.setVisibility(View.GONE);
        }

        // Кнопка корзины
        if (cartManager.isInCart(currentBeat.getId())) {
            btnAddToCart.setText("In Cart ✓");
            btnAddToCart.setEnabled(false);
        } else {
            btnAddToCart.setText("Add to Cart - " + currentBeat.getFormattedPrice());
            btnAddToCart.setEnabled(true);
        }
    }

    private void loadCover(String coverBase64) {
        try {
            byte[] decodedBytes = Base64.decode(coverBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            if (bitmap != null) {
                ivCover.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            ivCover.setImageResource(R.drawable.ic_music_note);
        }
    }

    private void prepareAudio() {
        if (currentBeat == null || !currentBeat.hasAudio()) return;

        try {
            File audioFile = AudioUtils.decodeBase64ToFile(this,
                    currentBeat.getFullAudio(), currentBeat.getId());

            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(mp -> {
                tvTotalTime.setText(formatTime(mp.getDuration()));
                seekBar.setMax(100);
                btnPlay.setEnabled(true);
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                seekBar.setProgress(0);
                tvCurrentTime.setText("0:00");
                stopProgressUpdates();
            });

        } catch (Exception e) {
            Log.e(TAG, "Error preparing audio: " + e.getMessage());
            Toast.makeText(this, "Error loading audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayback() {
        if (mediaPlayer == null) return;

        try {
            if (isPlaying) {
                mediaPlayer.pause();
                isPlaying = false;
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                stopProgressUpdates();
            } else {
                mediaPlayer.start();
                isPlaying = true;
                btnPlay.setImageResource(R.drawable.ic_pause);
                startProgressUpdates();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling playback: " + e.getMessage());
        }
    }

    private void addToCart() {
        if (currentBeat == null) return;

        if (cartManager.addToCart(currentBeat)) {
            btnAddToCart.setText("In Cart ✓");
            btnAddToCart.setEnabled(false);
            Toast.makeText(this, "Added to cart: " + currentBeat.getTitle(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Already in cart", Toast.LENGTH_SHORT).show();
        }
    }

    private void buyNow() {
        if (currentBeat == null) return;

        cartManager.addToCart(currentBeat);
        Intent intent = new Intent(this, BuyerCartActivity.class);
        startActivity(intent);
    }

    private void setupProgressUpdates() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying && !isSeekBarTracking) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();

                    if (duration > 0) {
                        int progress = (currentPosition * 100) / duration;
                        seekBar.setProgress(progress);
                        tvCurrentTime.setText(formatTime(currentPosition));
                    }
                }
                progressHandler.postDelayed(this, 500);
            }
        };
    }

    private void startProgressUpdates() {
        progressHandler.postDelayed(progressRunnable, 500);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlay.setImageResource(R.drawable.ic_play_arrow);
        }
        stopProgressUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
    }
}