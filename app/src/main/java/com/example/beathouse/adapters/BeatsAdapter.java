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
import com.example.beathouse.services.AudioPlaybackService;
import com.google.android.material.button.MaterialButton;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.content.Intent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeatsAdapter extends RecyclerView.Adapter<BeatsAdapter.BeatViewHolder> {

    private List<Beat> beatsList;
    private List<Beat> beatsListFull; // ✅ Для поиска
    private Context context;
    private static MediaPlayer mediaPlayer;
    private static int currentlyPlayingPosition = -1;
    private static Beat currentlyPlayingBeat;
    private static String currentlyPlayingId = null;
    private Map<String, String> audioCache;
    private Handler mainHandler;
    private MiniPlayer miniPlayer;
    private CartManager cartManager;
    private AudioPlaybackService audioService;
    private boolean isServiceBound = false;
    private static final String TAG = "BeatsAdapter";
    private static BeatsAdapter staticInstance;

    // Состояние воспроизведения
    private int savedPosition = 0;
    private boolean wasPlaying = false;
    private static boolean isMediaPlayerPreparing = false;

    // Слушатель изменений состояния воспроизведения
    public interface OnPlaybackStateChangeListener {
        void onPlaybackStateChanged(boolean isPlaying, Beat currentBeat);
    }

    private OnPlaybackStateChangeListener playbackStateChangeListener;

    // ✅ Слушатель для поиска и фильтрации
    public interface OnFilterChangeListener {
        void onFilterChanged(String query, String genre);
    }
    private OnFilterChangeListener filterChangeListener;

    public void setOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        this.playbackStateChangeListener = listener;
    }

    public void setOnFilterChangeListener(OnFilterChangeListener listener) {
        this.filterChangeListener = listener;
    }

    private void notifyPlaybackStateChanged() {
        updateNotification();
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
        this.beatsListFull = new ArrayList<>(beatsList); // ✅ Сохраняем полный список
        this.context = context;
        this.audioCache = new HashMap<>();
        this.mainHandler = new Handler(Looper.getMainLooper());

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }

        this.cartManager = new CartManager(context);
        setupMediaPlayerListeners();

        bindAudioService();
        registerAudioReceiver();

        staticInstance = this;

        Log.d(TAG, "BeatsAdapter created with " + (beatsList != null ? beatsList.size() : 0) + " beats");
    }

    private void bindAudioService() {
        Intent intent = new Intent(context, AudioPlaybackService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlaybackService.AudioBinder binder = (AudioPlaybackService.AudioBinder) service;
            audioService = binder.getService();
            isServiceBound = true;
            updateNotification();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    private void registerAudioReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioPlaybackService.ACTION_PLAY);
        filter.addAction(AudioPlaybackService.ACTION_PAUSE);
        filter.addAction(AudioPlaybackService.ACTION_PREVIOUS);
        filter.addAction(AudioPlaybackService.ACTION_NEXT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(audioReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(audioReceiver, filter);
        }
    }

    private final BroadcastReceiver audioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case AudioPlaybackService.ACTION_PLAY:
                    resumePlayback();
                    break;
                case AudioPlaybackService.ACTION_PAUSE:
                    pausePlayback();
                    break;
                case AudioPlaybackService.ACTION_PREVIOUS:
                    playPreviousBeatAutomatically();
                    break;
                case AudioPlaybackService.ACTION_NEXT:
                    playNextBeatAutomatically();
                    break;
            }
        }
    };

    private void updateNotification() {
        if (isServiceBound && audioService != null) {
            Beat current = getCurrentlyPlayingBeat();
            audioService.updateState(current, isPlaying());
        }
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

    // ✅ ПОИСК И ФИЛЬТРАЦИЯ
    public void filter(String query, String genre) {
        List<Beat> filteredList = new ArrayList<>();

        for (Beat beat : beatsListFull) {
            boolean matchesQuery = true;
            boolean matchesGenre = true;

            // Поиск по названию
            if (query != null && !query.isEmpty()) {
                matchesQuery = beat.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                        beat.getUserName().toLowerCase().contains(query.toLowerCase());
            }

            // Фильтр по жанру
            if (genre != null && !genre.isEmpty() && !genre.equals("All")) {
                matchesGenre = beat.getGenre() != null &&
                        beat.getGenre().toLowerCase().contains(genre.toLowerCase());
            }

            if (matchesQuery && matchesGenre) {
                filteredList.add(beat);
            }
        }

        beatsList.clear();
        beatsList.addAll(filteredList);
        notifyDataSetChanged();

        if (filterChangeListener != null) {
            filterChangeListener.onFilterChanged(query, genre);
        }

        Log.d(TAG, "Filtered: " + filteredList.size() + " beats (query=" + query + ", genre=" + genre + ")");
    }

    // ✅ Сброс фильтра
    public void resetFilter() {
        beatsList.clear();
        beatsList.addAll(beatsListFull);
        notifyDataSetChanged();
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
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading cover from beat: " + e.getMessage());
            }
        }
    }

    private void updateCartButtonState(BeatViewHolder holder, Beat beat) {
        boolean inCart = cartManager.isInCart(beat.getId());

        // Всегда используем иконку корзины
        holder.btnAddToCart.setImageResource(R.drawable.ic_shopping_cart);
        holder.btnAddToCart.setColorFilter(ContextCompat.getColor(context, R.color.primary));

        if (inCart) {
            // Если в корзине — показываем кружочек (бейдж)
            if (holder.cartBadge != null) {
                holder.cartBadge.setVisibility(View.VISIBLE);
            }
        } else {
            // Если нет — скрываем
            if (holder.cartBadge != null) {
                holder.cartBadge.setVisibility(View.GONE);
            }
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
        boolean isSameBeat = beat.getId().equals(currentlyPlayingId);

        if (isSameBeat && isPlaying()) {
            pausePlayback();
            return;
        }

        if (isSameBeat && !isPlaying() && !isMediaPlayerPreparing) {
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
        currentlyPlayingBeat = beat;
        currentlyPlayingId = beat.getId();
        isMediaPlayerPreparing = true;

        if (miniPlayer != null) {
            miniPlayer.show(beat);
            miniPlayer.updatePlayState(false);
        }

        notifyPlaybackStateChanged();
        notifyDataSetChanged();

        // 1. Проверяем дисковый кэш
        if (AudioUtils.isAudioCached(context, beat.getId())) {
            Log.d(TAG, "⚡ Using disk cache for beat: " + beat.getTitle());
            File cachedFile = AudioUtils.getCachedAudioFile(context, beat.getId());
            playAudioFile(cachedFile, position, null, beat.getTitle());
            return;
        }

        // 2. Проверяем оперативную память (старый механизм)
        String audioData = null;
        if (audioCache.containsKey(beat.getId())) {
            audioData = audioCache.get(beat.getId());
        } else if (beat.hasAudio() && beat.getFullAudio() != null) {
            audioData = beat.getFullAudio();
        }

        if (audioData != null && !audioData.isEmpty()) {
            prepareAndPlayAudio(audioData, beat, position, null);
        } else {
            // 3. Загружаем с сервера
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
                // ✅ Передаем только beatId для консистентного кэширования
                File audioFile = AudioUtils.decodeBase64ToFile(context, audioData, beat.getId());

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
            Beat beat = beatsList.get(position);
            boolean isCurrentlyPlaying = beat.getId().equals(currentlyPlayingId) && isPlaying();

            if (isCurrentlyPlaying) {
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
        if (currentlyPlayingBeat != null) return currentlyPlayingBeat;

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
            // ✅ Убираем принудительную остановку при обновлении списка,
            // чтобы музыка не прерывалась при переключении вкладок.
            // stopPlaybackCompletely();

            if (newBeats != this.beatsList) {
                this.beatsList.clear();
                this.beatsList.addAll(newBeats);
            }

            this.beatsListFull.clear();
            this.beatsListFull.addAll(newBeats);
            notifyDataSetChanged();
        }
    }

    public void clearAudioCache() {
        audioCache.clear();
        AudioUtils.clearTempAudioFiles(context);
    }

    public void releaseMediaPlayer() {
        // Мы НЕ освобождаем MediaPlayer здесь, так как он должен продолжать играть при смене фрагментов.
        // Мы только отключаем MiniPlayer от адаптера, если это необходимо,
        // но вообще лучше оставить его живым в рамках MainActivity.
        Log.d(TAG, "releaseMediaPlayer called (no-op for persistence)");
    }

    public void stopAndReleaseCompletely() {
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

        // Cleanup service and receiver
        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection);
                isServiceBound = false;
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding service", e);
            }
        }
        try {
            context.unregisterReceiver(audioReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }

    @Override
    public int getItemCount() {
        return beatsList != null ? beatsList.size() : 0;
    }

    private void runOnUiThread(Runnable action) {
        mainHandler.post(action);
    }

    private void showToast(String message) {
        runOnUiThread(() -> {
            if (context instanceof android.app.Activity) {
                View rootView = ((android.app.Activity) context).findViewById(android.R.id.content);
                if (rootView != null) {
                    com.google.android.material.snackbar.Snackbar.make(rootView, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
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
        // We don't need special logic here anymore because onBindViewHolder handles state based on currentlyPlayingId
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
        View cardBeat;

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
            cardBeat = itemView.findViewById(R.id.cardBeat);
        }
    }
}