package com.example.beathouse.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.R;
import com.example.beathouse.models.Beat;
import com.example.beathouse.utils.AudioUtils;
import com.example.beathouse.utils.CartManager;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.MiniPlayer;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeatsAdapter extends RecyclerView.Adapter<BeatsAdapter.BeatViewHolder> {

    private List<Beat> beatsList;
    private Context context;
    private MediaPlayer mediaPlayer;
    private int currentlyPlayingPosition = -1;
    private Map<String, String> audioCache;
    private Handler mainHandler;
    private MiniPlayer miniPlayer;
    private CartManager cartManager;
    private static final String TAG = "BeatsAdapter";
    private static BeatsAdapter staticInstance;

    // Состояние воспроизведения
    private int savedPosition = 0;
    private boolean wasPlaying = false;
    private boolean isMediaPlayerPreparing = false;

    // Слушатель изменений состояния воспроизведения
    public interface OnPlaybackStateChangeListener {
        void onPlaybackStateChanged(boolean isPlaying, Beat currentBeat);
    }

    private OnPlaybackStateChangeListener playbackStateChangeListener;

    public void setOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        this.playbackStateChangeListener = listener;
    }

    private void notifyPlaybackStateChanged() {
        if (playbackStateChangeListener != null) {
            boolean playing = isPlaying();
            Beat currentBeat = getCurrentlyPlayingBeat();
            playbackStateChangeListener.onPlaybackStateChanged(playing, currentBeat);
            Log.d(TAG, "Notified playback state change - Playing: " + playing);
        }
    }

    // Интерфейс для обработки кликов
    public interface OnBeatClickListener {
        void onBeatClick(Beat beat, int position);
        void onAddToCartClick(Beat beat);
        void onPlayPauseClick(Beat beat, int position);
    }

    private OnBeatClickListener beatClickListener;

    public BeatsAdapter(List<Beat> beatsList, Context context) {
        this.beatsList = beatsList;
        this.context = context;
        this.audioCache = new HashMap<>();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.mediaPlayer = new MediaPlayer();
        this.cartManager = new CartManager(context);
        setupMediaPlayerListeners();

        staticInstance = this;

        Log.d(TAG, "BeatsAdapter created with " + (beatsList != null ? beatsList.size() : 0) + " beats");
    }

    public static BeatsAdapter getInstance() {
        return staticInstance;
    }

    public void setOnBeatClickListener(OnBeatClickListener listener) {
        this.beatClickListener = listener;
    }

    public void setMiniPlayer(MiniPlayer miniPlayer) {
        this.miniPlayer = miniPlayer;
        Log.d(TAG, "MiniPlayer set in adapter");
    }

    @NonNull
    @Override
    public BeatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_beat, parent, false);
        return new BeatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BeatViewHolder holder, int position) {
        Beat beat = beatsList.get(position);

        holder.tvTitle.setText(beat.getTitle());
        holder.tvProducer.setText("by " + beat.getUserName());
        holder.tvBpm.setText(beat.getBpm() + " BPM");
        holder.tvKey.setText(beat.getKey() != null ? beat.getKey() : "Cmin");
        holder.tvGenre.setText(beat.getGenre() != null ? beat.getGenre() : "Hip-Hop");

        if (beat.getDescription() != null && !beat.getDescription().isEmpty()) {
            holder.tvDescription.setText(beat.getDescription());
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        // ✅ Отображаем минимальную цену (MP3+WAV)
        if (beat.isFree()) {
            holder.tvPrice.setText("FREE");
            holder.tvPrice.setBackgroundColor(ContextCompat.getColor(context, R.color.success));
        } else {
            double minPrice = beat.getPriceMp3Wav();
            holder.tvPrice.setText("$" + String.format("%.0f", minPrice));
            holder.tvPrice.setBackgroundColor(ContextCompat.getColor(context, R.color.primary));
        }

        // Загрузка обложки
        loadCoverImage(holder.ivCover, beat);

        updatePlayButtonState(holder, position);
        holder.btnPlay.setOnClickListener(v -> {
            if (beatClickListener != null) {
                beatClickListener.onPlayPauseClick(beat, position);
            }
            playBeatAudio(beat, position, holder);
        });

        updateCartButtonState(holder, beat);
        holder.btnAddToCart.setOnClickListener(v -> {
            handleCartButtonClick(beat, holder);
        });

        holder.itemView.setOnClickListener(v -> {
            if (beatClickListener != null) {
                beatClickListener.onBeatClick(beat, position);
            }
        });
    }

    private void loadCoverImage(ImageView imageView, Beat beat) {
        imageView.setImageResource(R.drawable.ic_music_note);

        if (beat.hasCover() && beat.getCoverImage() != null && !beat.getCoverImage().isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(beat.getCoverImage(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading cover from beat: " + e.getMessage());
            }
        }

        FirestoreHelper helper = new FirestoreHelper();
        helper.loadBeatCover(beat.getId(), new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                String coverData = (String) result;
                if (coverData != null && !coverData.isEmpty() && coverData.length() > 100) {
                    beat.setCoverImage(coverData);
                    try {
                        byte[] decodedBytes = Base64.decode(coverData, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding cover: " + e.getMessage());
                    }
                }
            }
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading cover from Firestore: " + error);
            }
        });
    }

    private void updateCartButtonState(BeatViewHolder holder, Beat beat) {
        boolean inCart = cartManager.isInCart(beat.getId());

        if (inCart) {
            holder.btnAddToCart.setImageResource(R.drawable.ic_cart_with_badge);
            holder.btnAddToCart.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_red_dark));
            holder.cartBadge.setVisibility(View.VISIBLE);
        } else {
            holder.btnAddToCart.setImageResource(R.drawable.ic_shopping_cart);
            holder.btnAddToCart.setColorFilter(ContextCompat.getColor(context, R.color.primary));
            holder.cartBadge.setVisibility(View.GONE);
        }
    }

    private void handleCartButtonClick(Beat beat, BeatViewHolder holder) {
        if (cartManager.isInCart(beat.getId())) {
            if (cartManager.removeFromCart(beat.getId())) {
                updateCartButtonState(holder, beat);
                showToast("Removed from cart");
            } else {
                showToast("Failed to remove from cart");
            }
        } else {
            // ✅ Добавляем с лицензией по умолчанию (MP3+WAV)
            if (cartManager.addToCartWithDefaultLicense(beat)) {
                updateCartButtonState(holder, beat);
                showToast("Added to cart");
            } else {
                showToast("Failed to add to cart");
            }
        }

        if (beatClickListener != null) {
            beatClickListener.onAddToCartClick(beat);
        }
    }

    // === ОСНОВНЫЕ МЕТОДЫ ВОСПРОИЗВЕДЕНИЯ ===

    public void playBeatAudio(Beat beat, int position, BeatViewHolder holder) {
        if (position == currentlyPlayingPosition && isPlaying()) {
            pausePlayback();
            return;
        }

        if (position == currentlyPlayingPosition && !isPlaying() && !isMediaPlayerPreparing) {
            resumePlayback();
            if (miniPlayer != null) {
                miniPlayer.updatePlayState(true);
            }
            return;
        }

        if (isMediaPlayerPreparing) {
            Log.w(TAG, "Already preparing, ignoring");
            return;
        }

        if (miniPlayer != null) {
            miniPlayer.show(beat);
            miniPlayer.updatePlayState(false);
        }

        forcePlayBeat(beat, position);
    }

    private void forcePlayBeat(Beat beat, int position) {
        savePlaybackState();
        stopPlaybackCompletely();

        currentlyPlayingPosition = position;
        isMediaPlayerPreparing = true;

        if (miniPlayer != null) {
            miniPlayer.show(beat);
            miniPlayer.updatePlayState(false);
        }

        notifyPlaybackStateChanged();
        notifyDataSetChanged();

        String audioData = null;

        if (audioCache.containsKey(beat.getId())) {
            audioData = audioCache.get(beat.getId());
        } else if (beat.hasAudio() && beat.getFullAudio() != null) {
            audioData = beat.getFullAudio();
        }

        if (audioData != null && !audioData.isEmpty()) {
            prepareAndPlayAudio(audioData, beat, position, null);
        } else {
            loadAudioFromServer(beat, position, null);
        }
    }

    public void playNextBeatAutomatically() {
        if (isMediaPlayerPreparing) return;
        if (beatsList.isEmpty()) return;

        int nextPosition;
        if (currentlyPlayingPosition == -1) {
            nextPosition = 0;
        } else if (currentlyPlayingPosition < beatsList.size() - 1) {
            nextPosition = currentlyPlayingPosition + 1;
        } else {
            nextPosition = 0;
        }

        Beat nextBeat = beatsList.get(nextPosition);
        forcePlayBeat(nextBeat, nextPosition);
    }

    public void playPreviousBeatAutomatically() {
        if (isMediaPlayerPreparing) return;
        if (beatsList.isEmpty()) return;

        int prevPosition;
        if (currentlyPlayingPosition == -1) {
            prevPosition = beatsList.size() - 1;
        } else if (currentlyPlayingPosition > 0) {
            prevPosition = currentlyPlayingPosition - 1;
        } else {
            prevPosition = beatsList.size() - 1;
        }

        Beat prevBeat = beatsList.get(prevPosition);
        forcePlayBeat(prevBeat, prevPosition);
    }

    public void safePlayNextBeat() {
        playNextBeatAutomatically();
    }

    public void safePlayPreviousBeat() {
        playPreviousBeatAutomatically();
    }

    private void prepareAndPlayAudio(String audioData, Beat beat, int position, BeatViewHolder holder) {
        if (holder != null) {
            updateButtonState(holder, "Preparing...", false);
        } else {
            notifyItemChanged(position);
        }

        new Thread(() -> {
            try {
                File audioFile = AudioUtils.decodeBase64ToFile(context, audioData, "beat_" + beat.getId());

                runOnUiThread(() -> {
                    if (audioFile != null && audioFile.exists()) {
                        playAudioFile(audioFile, position, holder, beat.getTitle());
                    } else {
                        isMediaPlayerPreparing = false;
                        showToast("Failed to prepare audio file");
                        safeResetPlayButtonState(holder);
                        if (miniPlayer != null) miniPlayer.updatePlayState(false);
                    }
                });

            } catch (Exception e) {
                isMediaPlayerPreparing = false;
                runOnUiThread(() -> {
                    showToast("Error preparing audio");
                    safeResetPlayButtonState(holder);
                    if (miniPlayer != null) miniPlayer.updatePlayState(false);
                });
            }
        }).start();
    }

    private void playAudioFile(File audioFile, int position, BeatViewHolder holder, String beatTitle) {
        try {
            isMediaPlayerPreparing = true;

            if (mediaPlayer != null) {
                try {
                    mediaPlayer.setOnPreparedListener(null);
                    mediaPlayer.setOnCompletionListener(null);
                    mediaPlayer.setOnErrorListener(null);
                    if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                    mediaPlayer.reset();
                } catch (Exception e) {
                    mediaPlayer = new MediaPlayer();
                }
            } else {
                mediaPlayer = new MediaPlayer();
            }

            setupMediaPlayerListeners();

            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepareAsync();

            mainHandler.postDelayed(() -> {
                if (isMediaPlayerPreparing) {
                    isMediaPlayerPreparing = false;
                    runOnUiThread(() -> {
                        showToast("Audio preparation timeout");
                        safeResetPlayButtonState(holder);
                        if (miniPlayer != null) miniPlayer.updatePlayState(false);
                        if (currentlyPlayingPosition != -1) safePlayNextBeat();
                    });
                }
            }, 15000);

        } catch (Exception e) {
            isMediaPlayerPreparing = false;
            runOnUiThread(() -> {
                showToast("Playback setup error");
                safeResetPlayButtonState(holder);
                currentlyPlayingPosition = -1;
                savedPosition = 0;
                wasPlaying = false;
                if (miniPlayer != null) miniPlayer.updatePlayState(false);
            });
        }
    }

    public void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
            } catch (Exception e) {}
        }

        isMediaPlayerPreparing = false;
        savePlaybackState();

        if (miniPlayer != null) miniPlayer.updatePlayState(false);
        notifyPlaybackStateChanged();
        notifyDataSetChanged();
    }

    public void resumePlayback() {
        if (mediaPlayer != null && currentlyPlayingPosition != -1 && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
                if (miniPlayer != null) miniPlayer.updatePlayState(true);
                notifyPlaybackStateChanged();
                notifyDataSetChanged();
            } catch (Exception e) {}
        }
    }

    private void stopPlaybackCompletely() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.reset();
            } catch (Exception e) {}
        }
        isMediaPlayerPreparing = false;
        savedPosition = 0;
        wasPlaying = false;
    }

    public void playBeatAtPosition(int position) {
        if (position >= 0 && position < beatsList.size()) {
            Beat beat = beatsList.get(position);
            currentlyPlayingPosition = position;
            notifyDataSetChanged();
            forcePlayBeat(beat, position);
            if (miniPlayer != null) {
                miniPlayer.updateCurrentBeat(beat);
                miniPlayer.updatePlayState(true);
            }
        }
    }

    private void updatePlayButtonState(BeatViewHolder holder, int position) {
        if (holder != null && holder.btnPlay != null) {
            if (position == currentlyPlayingPosition && isPlaying()) {
                holder.btnPlay.setIconResource(R.drawable.ic_pause);
                holder.btnPlay.setText("Pause");
            } else {
                holder.btnPlay.setIconResource(R.drawable.ic_play_arrow);
                holder.btnPlay.setText("Play");
            }
            holder.btnPlay.setEnabled(true);
        }
    }

    private void updateButtonState(BeatViewHolder holder, String text, boolean enabled) {
        if (holder != null && holder.btnPlay != null) {
            runOnUiThread(() -> {
                holder.btnPlay.setText(text);
                holder.btnPlay.setEnabled(enabled);
            });
        }
    }

    private void safeResetPlayButtonState(BeatViewHolder holder) {
        runOnUiThread(() -> {
            if (holder != null && holder.btnPlay != null) {
                updateButtonState(holder, "Play", true);
                holder.btnPlay.setIconResource(R.drawable.ic_play_arrow);
            } else {
                notifyDataSetChanged();
            }
        });
    }

    public void savePlaybackState() {
        if (mediaPlayer != null && currentlyPlayingPosition != -1) {
            try {
                savedPosition = mediaPlayer.getCurrentPosition();
                wasPlaying = mediaPlayer.isPlaying();
            } catch (Exception e) {}
        }
    }

    private void setupMediaPlayerListeners() {
        mediaPlayer.setOnCompletionListener(mp -> {
            runOnUiThread(() -> {
                isMediaPlayerPreparing = false;
                playNextBeatAutomatically();
            });
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            isMediaPlayerPreparing = false;
            if (currentlyPlayingPosition != -1) {
                try {
                    mp.start();
                    safeUpdatePlayButtonState(null, currentlyPlayingPosition);
                    if (miniPlayer != null) {
                        Beat currentBeat = getCurrentlyPlayingBeat();
                        if (currentBeat != null) {
                            miniPlayer.show(currentBeat);
                            miniPlayer.updatePlayState(true);
                        }
                    }
                    notifyPlaybackStateChanged();
                } catch (Exception e) {
                    isMediaPlayerPreparing = false;
                    showToast("Error starting playback");
                }
            } else {
                isMediaPlayerPreparing = false;
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            runOnUiThread(() -> {
                isMediaPlayerPreparing = false;
                showToast("Playback error occurred");
                currentlyPlayingPosition = -1;
                savedPosition = 0;
                wasPlaying = false;
                notifyDataSetChanged();
                if (miniPlayer != null) miniPlayer.updatePlayState(false);
            });
            return true;
        });
    }

    private void loadAudioFromServer(Beat beat, int position, BeatViewHolder holder) {
        if (holder != null) {
            updateButtonState(holder, "Downloading...", false);
        } else {
            notifyItemChanged(position);
        }

        FirestoreHelper firestoreHelper = new FirestoreHelper();
        firestoreHelper.loadBeatAudioSmart(beat.getId(), new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                String audioData = (String) result;
                if (audioData != null && !audioData.isEmpty()) {
                    audioCache.put(beat.getId(), audioData);
                    beat.setFullAudio(audioData);
                    prepareAndPlayAudio(audioData, beat, position, holder);
                } else {
                    isMediaPlayerPreparing = false;
                    runOnUiThread(() -> {
                        showToast("Audio not available on server");
                        safeResetPlayButtonState(holder);
                    });
                }
            }

            @Override
            public void onError(String error) {
                isMediaPlayerPreparing = false;
                runOnUiThread(() -> {
                    showToast("Error loading audio from server");
                    safeResetPlayButtonState(holder);
                });
            }
        });
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public Beat getCurrentlyPlayingBeat() {
        if (currentlyPlayingPosition >= 0 && currentlyPlayingPosition < beatsList.size()) {
            return beatsList.get(currentlyPlayingPosition);
        }
        return null;
    }

    public int getCurrentPlaybackPosition() {
        if (mediaPlayer != null && currentlyPlayingPosition != -1 && !isMediaPlayerPreparing) {
            try {
                return Math.max(mediaPlayer.getCurrentPosition(), 0);
            } catch (Exception e) {}
        }
        return 0;
    }

    public int getPlaybackDuration() {
        if (mediaPlayer != null && currentlyPlayingPosition != -1 && !isMediaPlayerPreparing) {
            try {
                return Math.max(mediaPlayer.getDuration(), 0);
            } catch (Exception e) {}
        }
        return 0;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null && currentlyPlayingPosition != -1) {
            try {
                mediaPlayer.seekTo(position);
            } catch (Exception e) {}
        }
    }

    public void updateBeatsList(List<Beat> newBeats) {
        if (newBeats != null) {
            stopPlaybackCompletely();
            audioCache.clear();
            this.beatsList.clear();
            this.beatsList.addAll(newBeats);
            notifyDataSetChanged();
        }
    }

    public void clearAudioCache() {
        audioCache.clear();
        AudioUtils.clearTempAudioFiles(context);
    }

    public void releaseMediaPlayer() {
        stopPlaybackCompletely();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {}
            mediaPlayer = null;
        }
        if (miniPlayer != null) {
            miniPlayer.release();
        }
        audioCache.clear();
        currentlyPlayingPosition = -1;
        isMediaPlayerPreparing = false;
    }

    @Override
    public int getItemCount() {
        return beatsList != null ? beatsList.size() : 0;
    }

    private void runOnUiThread(Runnable action) {
        mainHandler.post(action);
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private void safeUpdatePlayButtonState(BeatViewHolder holder, int position) {
        runOnUiThread(() -> {
            if (holder != null && holder.btnPlay != null) {
                updatePlayButtonState(holder, position);
            } else {
                notifyItemChanged(position);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull BeatViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.getAdapterPosition() != currentlyPlayingPosition) {
            updateButtonState(holder, "Play", true);
            holder.btnPlay.setIconResource(R.drawable.ic_play_arrow);
        }
    }

    public int findBeatPosition(String beatId) {
        if (beatsList == null || beatId == null) return -1;
        for (int i = 0; i < beatsList.size(); i++) {
            Beat beat = beatsList.get(i);
            if (beat != null && beatId.equals(beat.getId())) return i;
        }
        return -1;
    }

    public void stopPlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
            } catch (Exception e) {}
        }
        isMediaPlayerPreparing = false;
        savePlaybackState();
        if (miniPlayer != null) miniPlayer.updatePlayState(false);
        notifyDataSetChanged();
    }

    public boolean isMediaPlayerReady() {
        try {
            return mediaPlayer != null && currentlyPlayingPosition != -1 && !isMediaPlayerPreparing;
        } catch (Exception e) {
            return false;
        }
    }

    public static class BeatViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvProducer, tvBpm, tvKey, tvGenre, tvPrice, tvDescription;
        ImageView ivCover;
        MaterialButton btnPlay;
        ImageButton btnAddToCart;
        View cartBadge;

        public BeatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvProducer = itemView.findViewById(R.id.tvProducer);
            tvBpm = itemView.findViewById(R.id.tvBpm);
            tvKey = itemView.findViewById(R.id.tvKey);
            tvGenre = itemView.findViewById(R.id.tvGenre);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            ivCover = itemView.findViewById(R.id.ivCover);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            btnAddToCart = itemView.findViewById(R.id.btn_add_to_cart);
            cartBadge = itemView.findViewById(R.id.cart_badge);
        }
    }
}