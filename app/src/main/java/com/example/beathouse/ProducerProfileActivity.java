package com.example.beathouse;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.adapters.BeatsAdapter;
import com.example.beathouse.models.Beat;
import com.example.beathouse.models.Producer;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.FollowManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProducerProfileActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private ImageView ivProducerAvatar, ivVerified;
    private TextView tvProducerName, tvProducerBio, tvBeatsCount, tvSalesCount,
            tvFollowersCount, tvFollowingCount, tvLocation;
    private RatingBar ratingBar;
    private MaterialButton btnFollow, btnRate;
    private ImageButton btnMessage;
    private RecyclerView recyclerViewBeats;
    private View progressBar, emptyState;
    private LinearLayout socialLinksLayout;
    private ImageView ivInstagram, ivTelegram, ivVk;

    private FirestoreHelper firestoreHelper;
    private FollowManager followManager;
    private BeatsAdapter beatsAdapter;
    private List<Beat> beatsList;
    private String producerId;
    private String currentUserId;
    private Producer currentProducer;
    private boolean isFollowing = false;
    private String existingReviewId = null; // ID существующей оценки
    private double existingRating = 0; // Существующая оценка
    private ListenerRegistration userListener;
    private ListenerRegistration ratingListener;
    private FirebaseFirestore db;
    private static final String TAG = "ProducerProfileActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_producer_profile);

        producerId = getIntent().getStringExtra("producer_id");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        initViews();
        setupRecyclerView();
        loadProducerData();
        loadProducerBeats();
        checkIfFollowing();
        checkIfUserRated(); // Проверяем существующую оценку
        startUserListener();
        startRatingListener();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivProducerAvatar = findViewById(R.id.ivProducerAvatar);
        ivVerified = findViewById(R.id.ivVerified);
        tvProducerName = findViewById(R.id.tvProducerName);
        tvProducerBio = findViewById(R.id.tvProducerBio);
        tvBeatsCount = findViewById(R.id.tvBeatsCount);
        tvSalesCount = findViewById(R.id.tvSalesCount);
        tvFollowersCount = findViewById(R.id.tvFollowersCount);
        tvFollowingCount = findViewById(R.id.tvFollowingCount);
        tvLocation = findViewById(R.id.tvLocation);
        ratingBar = findViewById(R.id.ratingBar);
        btnFollow = findViewById(R.id.btnFollow);
        btnRate = findViewById(R.id.btnRate);
        btnMessage = findViewById(R.id.btnMessage);
        recyclerViewBeats = findViewById(R.id.recyclerViewBeats);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        socialLinksLayout = findViewById(R.id.socialLinksLayout);
        ivInstagram = findViewById(R.id.ivInstagram);
        ivTelegram = findViewById(R.id.ivTelegram);
        ivVk = findViewById(R.id.ivVk);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.producer_profile));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        firestoreHelper = new FirestoreHelper();
        followManager = new FollowManager();
        db = FirebaseFirestore.getInstance();
        beatsList = new ArrayList<>();

        btnFollow.setOnClickListener(v -> toggleFollow());
        btnRate.setOnClickListener(v -> showRatingDialog());

        if (btnMessage != null && !producerId.equals(currentUserId)) {
            btnMessage.setVisibility(View.VISIBLE);
            btnMessage.setOnClickListener(v -> openChat());
        } else if (btnMessage != null) {
            btnMessage.setVisibility(View.GONE);
        }
    }

    private void openChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("user_id", producerId);
        intent.putExtra("user_name", currentProducer != null ? currentProducer.getDisplayName() : "Producer");
        startActivity(intent);
    }

    private void setupRecyclerView() {
        beatsAdapter = new BeatsAdapter(beatsList, this);
        if (recyclerViewBeats != null) {
            recyclerViewBeats.setLayoutManager(new LinearLayoutManager(this));
            recyclerViewBeats.setAdapter(beatsAdapter);
        }
    }

    private void startUserListener() {
        if (userListener != null) userListener.remove();

        userListener = db.collection("users").document(producerId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null || !doc.exists()) return;

                    Long followers = doc.getLong("followers");
                    Long following = doc.getLong("following");
                    runOnUiThread(() -> {
                        tvFollowersCount.setText(String.valueOf(followers != null ? Math.max(0, followers) : 0));
                        tvFollowingCount.setText(String.valueOf(following != null ? Math.max(0, following) : 0));
                    });
                });
    }

    private void startRatingListener() {
        if (ratingListener != null) ratingListener.remove();
        ratingListener = db.collection("producers").document(producerId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null || !doc.exists()) return;
                    Double rating = doc.getDouble("rating");
                    if (rating != null) {
                        runOnUiThread(() -> ratingBar.setRating(rating.floatValue()));
                    }
                });
    }

    private void loadProducerData() {
        if (producerId == null) { showProducerFromIntent(); return; }
        progressBar.setVisibility(View.VISIBLE);
        firestoreHelper.getProducer(producerId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                currentProducer = (Producer) result;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateProducerUI();
                    loadSocialLinks();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> { progressBar.setVisibility(View.GONE); showProducerFromIntent(); });
            }
        });
    }

    private void showProducerFromIntent() {
        String name = getIntent().getStringExtra("producer_name");
        if (tvProducerName != null && name != null) tvProducerName.setText(name);
    }

    private void updateProducerUI() {
        if (currentProducer == null) return;

        Log.d(TAG, "Updating UI for producer: " + currentProducer.getDisplayName());

        tvProducerName.setText(currentProducer.getDisplayName());

        String bio = currentProducer.getBio();
        if (bio != null && !bio.isEmpty()) {
            tvProducerBio.setText(bio);
            tvProducerBio.setVisibility(View.VISIBLE);
        } else {
            tvProducerBio.setText(getString(R.string.no_bio_yet));
            tvProducerBio.setVisibility(View.VISIBLE);
        }

        tvBeatsCount.setText(String.valueOf(currentProducer.getTotalBeats()));
        tvSalesCount.setText(String.valueOf(currentProducer.getTotalSales()));

        if (currentProducer.getLocation() != null && !currentProducer.getLocation().isEmpty()) {
            tvLocation.setText("📍 " + currentProducer.getLocation());
            tvLocation.setVisibility(View.VISIBLE);
        }

        ivVerified.setVisibility(currentProducer.isVerified() ? View.VISIBLE : View.GONE);
        loadAvatar(currentProducer.getProfileImage());
    }

    private void loadSocialLinks() {
        if (producerId == null || socialLinksLayout == null) return;
        db.collection("users").document(producerId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    boolean hasAny = false;

                    String ig = doc.getString("socialInstagram");
                    if (ig != null && !ig.isEmpty() && ivInstagram != null) {
                        ivInstagram.setVisibility(View.VISIBLE);
                        ivInstagram.setOnClickListener(v -> openSocialUrl(ig));
                        hasAny = true;
                    } else if (ivInstagram != null) { ivInstagram.setVisibility(View.GONE); }

                    String tg = doc.getString("socialTelegram");
                    if (tg != null && !tg.isEmpty() && ivTelegram != null) {
                        ivTelegram.setVisibility(View.VISIBLE);
                        ivTelegram.setOnClickListener(v -> openSocialUrl(tg));
                        hasAny = true;
                    } else if (ivTelegram != null) { ivTelegram.setVisibility(View.GONE); }

                    String vk = doc.getString("socialVk");
                    if (vk != null && !vk.isEmpty() && ivVk != null) {
                        ivVk.setVisibility(View.VISIBLE);
                        ivVk.setOnClickListener(v -> openSocialUrl(vk));
                        hasAny = true;
                    } else if (ivVk != null) { ivVk.setVisibility(View.GONE); }

                    socialLinksLayout.setVisibility(hasAny ? View.VISIBLE : View.GONE);
                });
    }

    private void openSocialUrl(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cannot_open_link), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAvatar(String profileImage) {
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(profileImage, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) { ivProducerAvatar.setImageBitmap(bitmap); return; }
            } catch (Exception ignored) {}
        }
        ivProducerAvatar.setImageResource(R.drawable.ic_profile_placeholder);
    }

    private void loadProducerBeats() {
        if (producerId == null) return;
        firestoreHelper.getProducerBeats(producerId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                List<Beat> beats = (List<Beat>) result;
                runOnUiThread(() -> {
                    if (beats != null && !beats.isEmpty()) {
                        beatsList.clear();
                        beatsList.addAll(beats);
                        beatsAdapter.updateBeatsList(beats);
                        emptyState.setVisibility(View.GONE);
                        recyclerViewBeats.setVisibility(View.VISIBLE);
                    } else {
                        emptyState.setVisibility(View.VISIBLE);
                        recyclerViewBeats.setVisibility(View.GONE);
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerViewBeats.setVisibility(View.GONE);
                });
            }
        });
    }

    private void checkIfFollowing() {
        followManager.isFollowing(currentUserId, producerId, new FollowManager.FollowCallback() {
            @Override
            public void onSuccess(boolean following) { isFollowing = following; runOnUiThread(() -> updateFollowButton()); }
            @Override
            public void onError(String error) { Log.e(TAG, "Error: " + error); }
        });
    }

    // ✅ Проверяем, есть ли уже оценка от пользователя (не блокируем, а запоминаем)
    private void checkIfUserRated() {
        db.collection("reviews")
                .whereEqualTo("producerId", producerId)
                .whereEqualTo("buyerId", currentUserId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        DocumentSnapshot doc = snap.getDocuments().get(0);
                        existingReviewId = doc.getId();
                        Double rating = doc.getDouble("rating");
                        if (rating != null) {
                            existingRating = rating;
                        }
                        // ✅ Не блокируем кнопку, просто запоминаем существующую оценку
                        btnRate.setText(getString(R.string.change_rating));
                    } else {
                        existingReviewId = null;
                        existingRating = 0;
                        btnRate.setText(getString(R.string.rate));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error checking rating: " + e.getMessage()));
    }

    private void toggleFollow() {
        btnFollow.setEnabled(false);
        if (isFollowing) {
            followManager.unfollowUser(currentUserId, producerId, new FollowManager.FollowCallback() {
                @Override
                public void onSuccess(boolean following) {
                    isFollowing = false;
                    runOnUiThread(() -> {
                        updateFollowButton();
                        btnFollow.setEnabled(true);
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> btnFollow.setEnabled(true));
                    Toast.makeText(ProducerProfileActivity.this,
                            getString(R.string.follow_error) + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            followManager.followUser(currentUserId, producerId, new FollowManager.FollowCallback() {
                @Override
                public void onSuccess(boolean following) {
                    isFollowing = true;
                    runOnUiThread(() -> {
                        updateFollowButton();
                        btnFollow.setEnabled(true);
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> btnFollow.setEnabled(true));
                    Toast.makeText(ProducerProfileActivity.this,
                            getString(R.string.follow_error) + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateFollowButton() {
        btnFollow.setText(isFollowing ? getString(R.string.following) : getString(R.string.follow));
    }

    private void showRatingDialog() {
        if (currentProducer == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rating, null);
        RatingBar dialogRatingBar = dialogView.findViewById(R.id.dialogRatingBar);

        // ✅ Если уже есть оценка, показываем её в диалоге
        if (existingRating > 0) {
            dialogRatingBar.setRating((float) existingRating);
        }

        new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle(getString(R.string.rate_user) + " " + currentProducer.getDisplayName())
                .setPositiveButton(getString(R.string.submit_rating), (dialog, which) -> submitRating(dialogRatingBar.getRating()))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void submitRating(float rating) {
        if (rating == 0) {
            Toast.makeText(this, getString(R.string.select_rating), Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Если есть существующая оценка - обновляем, иначе - создаем новую
        if (existingReviewId != null) {
            // Обновляем существующую оценку
            db.collection("reviews").document(existingReviewId)
                    .update("rating", (double) rating, "updatedAt", System.currentTimeMillis())
                    .addOnSuccessListener(a -> {
                        Toast.makeText(this, getString(R.string.rating_updated) + " ⭐", Toast.LENGTH_SHORT).show();
                        existingRating = rating;
                        updateAverageRating();
                        sendRatingNotification(rating);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            // Создаем новую оценку
            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("producerId", producerId);
            reviewData.put("buyerId", currentUserId);
            reviewData.put("rating", (double) rating);
            reviewData.put("createdAt", System.currentTimeMillis());

            db.collection("reviews").add(reviewData)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(this, getString(R.string.rated) + " ⭐", Toast.LENGTH_SHORT).show();
                        existingReviewId = doc.getId();
                        existingRating = rating;
                        btnRate.setText(getString(R.string.change_rating));
                        updateAverageRating();
                        sendRatingNotification(rating);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    // ✅ Отправка уведомления о рейтинге
    private void sendRatingNotification(double rating) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
            String username = getString(R.string.someone);
            if (userDoc.exists()) {
                String name = userDoc.getString("username");
                if (name != null && !name.isEmpty()) username = name;
            }
            firestoreHelper.sendRatingNotification(producerId, username, (float) rating);
        });
    }

    // ✅ Обновляем средний рейтинг
    private void updateAverageRating() {
        db.collection("reviews").whereEqualTo("producerId", producerId).get()
                .addOnSuccessListener(snap -> {
                    double total = 0;
                    int count = 0;
                    for (var doc : snap) {
                        Double r = doc.getDouble("rating");
                        if (r != null) {
                            total += r;
                            count++;
                        }
                    }
                    double avg = count > 0 ? Math.round((total / count) * 10.0) / 10.0 : 0;

                    // Обновляем в producers коллекции
                    db.collection("producers").document(producerId).update("rating", avg);
                    // Обновляем в users коллекции
                    db.collection("users").document(producerId).update("rating", avg);

                    Log.d(TAG, "Updated rating for producer: " + producerId + " -> " + avg + " (from " + count + " reviews)");
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) { userListener.remove(); userListener = null; }
        if (ratingListener != null) { ratingListener.remove(); ratingListener = null; }
    }
}