package com.example.beathouse;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.core.view.GestureDetectorCompat;
import com.example.beathouse.adapters.BeatsAdapter;
import com.example.beathouse.models.Beat;
import com.example.beathouse.utils.FirestoreHelper;

public class MiniPlayer {
    private static final String TAG = "MiniPlayer";

    private CardView miniPlayerCard;
    private View miniPlayerContent;
    private ImageView ivMiniCover;
    private TextView tvMiniTitle, tvMiniProducer;
    private ImageButton btnMiniPlay, btnMiniPrevious, btnMiniNext;
    private BeatsAdapter beatsAdapter;
    private Context context;
    private GestureDetectorCompat gestureDetector;
    private boolean isPlaying = false;
    private Beat currentBeat;

    public MiniPlayer(View rootView, BeatsAdapter adapter, Context context) {
        // Ищем CardView, который является контейнером мини-плеера
        if (rootView instanceof CardView) {
            this.miniPlayerCard = (CardView) rootView;
        } else {
            this.miniPlayerCard = rootView.findViewById(R.id.miniPlayerCard);
        }

        this.context = context;
        this.beatsAdapter = adapter;

        if (miniPlayerCard != null) {
            initViews();
            setupListeners();
            setupSwipeToClose();
            syncFromAdapter(); // ✅ Синхронизируем состояние при создании
        } else {
            Log.e(TAG, "MiniPlayer card not found");
        }
    }

    private void initViews() {
        // Ищем контент внутри CardView
        miniPlayerContent = miniPlayerCard.findViewById(R.id.miniPlayerContent);
        ivMiniCover = miniPlayerCard.findViewById(R.id.ivMiniCover);
        tvMiniTitle = miniPlayerCard.findViewById(R.id.tvMiniTitle);
        tvMiniProducer = miniPlayerCard.findViewById(R.id.tvMiniProducer);
        btnMiniPlay = miniPlayerCard.findViewById(R.id.btnMiniPlay);
        btnMiniPrevious = miniPlayerCard.findViewById(R.id.btnMiniPrevious);
        btnMiniNext = miniPlayerCard.findViewById(R.id.btnMiniNext);
    }

    private void setupListeners() {
        // Play/Pause
        if (btnMiniPlay != null) {
            btnMiniPlay.setOnClickListener(v -> {
                if (beatsAdapter == null) return;

                if (beatsAdapter.isPlaying()) {
                    beatsAdapter.pausePlayback();
                    isPlaying = false;
                    updatePlayButton();
                } else if (currentBeat != null) {
                    int pos = beatsAdapter.findBeatPosition(currentBeat.getId());
                    if (pos >= 0) {
                        beatsAdapter.playBeatAudio(currentBeat, pos, null);
                        isPlaying = true;
                        updatePlayButton();
                    }
                }
            });
        }

        // Previous
        if (btnMiniPrevious != null) {
            btnMiniPrevious.setOnClickListener(v -> {
                if (beatsAdapter != null) {
                    beatsAdapter.safePlayPreviousBeat();
                    syncFromAdapter();
                }
            });
        }

        // Next
        if (btnMiniNext != null) {
            btnMiniNext.setOnClickListener(v -> {
                if (beatsAdapter != null) {
                    beatsAdapter.safePlayNextBeat();
                    syncFromAdapter();
                }
            });
        }

        // Клик по контенту для открытия полного плеера
        if (miniPlayerContent != null) {
            miniPlayerContent.setOnClickListener(v -> showExpandedPlayer());
        } else if (miniPlayerCard != null) {
            miniPlayerCard.setOnClickListener(v -> showExpandedPlayer());
        }
    }

    private void setupSwipeToClose() {
        gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Свайп влево (velocityX < 0)
                if (e1 != null && e2 != null && (e1.getX() - e2.getX()) > 100 && Math.abs(velocityX) > 800) {
                    Log.d(TAG, "👈 Swipe left detected, closing mini player");
                    hide();
                    if (beatsAdapter != null) {
                        beatsAdapter.pausePlayback();
                    }
                    return true;
                }
                return false;
            }
        });

        View touchTarget = miniPlayerContent != null ? miniPlayerContent : miniPlayerCard;
        if (touchTarget != null) {
            touchTarget.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return false;
            });
        }
    }

    private void syncFromAdapter() {
        if (beatsAdapter != null) {
            currentBeat = beatsAdapter.getCurrentlyPlayingBeat();
            isPlaying = beatsAdapter.isPlaying();
            if (currentBeat != null) {
                runOnUiThread(() -> {
                    tvMiniTitle.setText(currentBeat.getTitle());
                    tvMiniProducer.setText("by " + currentBeat.getUserName());
                    loadCoverWithFallback(currentBeat);

                    // ✅ Показываем карточку, если что-то играет или готово к игре
                    if (miniPlayerCard.getVisibility() != View.VISIBLE) {
                        miniPlayerCard.setVisibility(View.VISIBLE);
                        miniPlayerCard.setAlpha(1f);
                    }
                });
            } else {
                // Если ничего не играет, скрываем плеер
                runOnUiThread(() -> {
                    if (miniPlayerCard != null) miniPlayerCard.setVisibility(View.GONE);
                });
            }
            updatePlayButton();
        }
    }

    private void updatePlayButton() {
        runOnUiThread(() -> {
            if (btnMiniPlay != null) {
                btnMiniPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            }
        });
    }

    public void show(Beat beat) {
        if (beat == null || miniPlayerCard == null) return;
        this.currentBeat = beat;
        this.isPlaying = (beatsAdapter != null && beatsAdapter.isPlaying());

        runOnUiThread(() -> {
            tvMiniTitle.setText(beat.getTitle());
            tvMiniProducer.setText("by " + beat.getUserName());
            loadCoverWithFallback(beat);
            updatePlayButton();

            miniPlayerCard.setVisibility(View.VISIBLE);
            miniPlayerCard.setAlpha(0f);
            miniPlayerCard.animate().alpha(1f).setDuration(200).start();
        });
    }

    public void hide() {
        runOnUiThread(() -> {
            if (miniPlayerCard != null) {
                miniPlayerCard.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> miniPlayerCard.setVisibility(View.GONE))
                        .start();
            }
            currentBeat = null;
            isPlaying = false;
        });
    }

    public void updatePlayState(boolean playing) {
        this.isPlaying = playing;
        updatePlayButton();
    }

    public void updateCurrentBeat(Beat beat) {
        if (beat != null) {
            this.currentBeat = beat;
            runOnUiThread(() -> {
                tvMiniTitle.setText(beat.getTitle());
                tvMiniProducer.setText("by " + beat.getUserName());
                loadCoverWithFallback(beat);
            });
        }
    }

    public void onTrackSwitched(Beat newBeat, boolean playing) {
        this.currentBeat = newBeat;
        this.isPlaying = playing;
        runOnUiThread(() -> {
            if (newBeat != null) {
                tvMiniTitle.setText(newBeat.getTitle());
                tvMiniProducer.setText("by " + newBeat.getUserName());
                loadCoverWithFallback(newBeat);
                if (miniPlayerCard.getVisibility() != View.VISIBLE) {
                    miniPlayerCard.setVisibility(View.VISIBLE);
                    miniPlayerCard.setAlpha(0f);
                    miniPlayerCard.animate().alpha(1f).setDuration(200).start();
                }
            }
            updatePlayButton();
        });
    }

    private void loadCoverWithFallback(Beat beat) {
        if (beat == null || ivMiniCover == null) return;

        if (beat.hasCover() && beat.getCoverImage() != null) {
            setCoverImage(beat.getCoverImage());
        } else {
            setDefaultCover();
        }
    }

    private void setCoverImage(String base64) {
        if (base64 == null || base64.length() < 100 || ivMiniCover == null) {
            setDefaultCover();
            return;
        }
        try {
            byte[] d = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
            if (bm != null) {
                runOnUiThread(() -> {
                    ivMiniCover.setImageBitmap(bm);
                    ivMiniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                });
            } else {
                setDefaultCover();
            }
        } catch (Exception e) {
            setDefaultCover();
        }
    }

    private void setDefaultCover() {
        runOnUiThread(() -> {
            if (ivMiniCover != null) {
                ivMiniCover.setImageResource(R.drawable.ic_music_note);
                ivMiniCover.setScaleType(ImageView.ScaleType.CENTER);
            }
        });
    }

    private void showExpandedPlayer() {
        if (context == null || currentBeat == null || beatsAdapter == null) {
            Log.e(TAG, "Cannot open expanded player - missing data");
            return;
        }

        Log.d(TAG, "🎵 Opening expanded player for: " + currentBeat.getTitle());

        PlayerActivity.setBeatsAdapter(beatsAdapter);
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("BEAT_ID", currentBeat.getId());
        intent.putExtra("IS_PLAYING", isPlaying);
        intent.putExtra("BEAT_TITLE", currentBeat.getTitle());
        intent.putExtra("PRODUCER_NAME", currentBeat.getUserName());

        if (currentBeat.hasCover() && currentBeat.getCoverImage() != null) {
            intent.putExtra("COVER_DATA", currentBeat.getCoverImage());
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public boolean isVisible() {
        return miniPlayerCard != null && miniPlayerCard.getVisibility() == View.VISIBLE;
    }

    public void release() {
        if (miniPlayerCard != null) {
            miniPlayerCard.setOnTouchListener(null);
        }
        currentBeat = null;
        beatsAdapter = null;
        context = null;
    }

    private void runOnUiThread(Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        }
    }
}