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
import com.example.beathouse.models.User;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.FollowManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.Map;

public class BuyerProfileDetailActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private ImageView ivBuyerAvatar;
    private TextView tvBuyerName, tvBuyerEmail, tvBeatsPurchased, tvTotalSpent,
            tvFollowersCount, tvFollowingCount, tvLocation, tvBio, tvRatingText;
    private RatingBar ratingBar;
    private MaterialButton btnFollow, btnRate;
    private ImageButton btnMessage;
    private View progressBar;
    private LinearLayout socialLinksLayout;
    private ImageView ivInstagram, ivTelegram, ivVk;

    private FollowManager followManager;
    private FirestoreHelper firestoreHelper;
    private String buyerId;
    private String currentUserId;
    private User currentBuyer;
    private boolean isFollowing = false;
    private String existingReviewId = null; // ID существующей оценки
    private double existingRating = 0; // Существующая оценка
    private ListenerRegistration userListener;
    private FirebaseFirestore db;
    private static final String TAG = "BuyerProfileDetailActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_profile_detail);

        buyerId = getIntent().getStringExtra("buyer_id");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (buyerId == null || buyerId.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_buyer_id), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadBuyerData();
        checkIfUserRated(); // Проверяем существующую оценку
        startUserListener();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivBuyerAvatar = findViewById(R.id.ivBuyerAvatar);
        tvBuyerName = findViewById(R.id.tvBuyerName);
        tvBuyerEmail = findViewById(R.id.tvBuyerEmail);
        tvBeatsPurchased = findViewById(R.id.tvBeatsPurchased);
        tvTotalSpent = findViewById(R.id.tvTotalSpent);
        tvFollowersCount = findViewById(R.id.tvFollowersCount);
        tvFollowingCount = findViewById(R.id.tvFollowingCount);
        tvLocation = findViewById(R.id.tvLocation);
        tvBio = findViewById(R.id.tvBio);
        tvRatingText = findViewById(R.id.tvRatingText);
        ratingBar = findViewById(R.id.ratingBar);
        btnFollow = findViewById(R.id.btnFollow);
        btnRate = findViewById(R.id.btnRate);
        btnMessage = findViewById(R.id.btnMessage);
        progressBar = findViewById(R.id.progressBar);
        socialLinksLayout = findViewById(R.id.socialLinksLayout);
        ivInstagram = findViewById(R.id.ivInstagram);
        ivTelegram = findViewById(R.id.ivTelegram);
        ivVk = findViewById(R.id.ivVk);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.buyer_profile));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        followManager = new FollowManager();
        firestoreHelper = new FirestoreHelper();
        db = FirebaseFirestore.getInstance();

        btnFollow.setOnClickListener(v -> toggleFollow());
        btnRate.setOnClickListener(v -> showRatingDialog());

        if (btnMessage != null && !buyerId.equals(currentUserId)) {
            btnMessage.setVisibility(View.VISIBLE);
            btnMessage.setOnClickListener(v -> openChat());
        } else if (btnMessage != null) {
            btnMessage.setVisibility(View.GONE);
        }
    }

    private void openChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("user_id", buyerId);
        intent.putExtra("user_name", currentBuyer != null ? currentBuyer.getUsername() : getString(R.string.user));
        startActivity(intent);
    }

    private void startUserListener() {
        if (userListener != null) userListener.remove();

        userListener = db.collection("users").document(buyerId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null || !doc.exists()) return;

                    Long followers = doc.getLong("followers");
                    Long following = doc.getLong("following");
                    Double rating = doc.getDouble("rating");

                    runOnUiThread(() -> {
                        tvFollowersCount.setText(String.valueOf(followers != null ? Math.max(0, followers) : 0));
                        tvFollowingCount.setText(String.valueOf(following != null ? Math.max(0, following) : 0));

                        if (rating != null && rating > 0) {
                            ratingBar.setRating(rating.floatValue());
                            tvRatingText.setText(String.format("%.1f / 5.0", rating));
                            tvRatingText.setVisibility(View.VISIBLE);
                        } else {
                            tvRatingText.setText(getString(R.string.no_ratings_yet));
                            tvRatingText.setVisibility(View.VISIBLE);
                        }
                    });
                });
    }

    private void loadBuyerData() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("users").document(buyerId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentBuyer = User.fromMap(doc.getData());

                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            updateBuyerUI();
                            loadSocialLinks();

                            if (!buyerId.equals(currentUserId)) {
                                btnFollow.setVisibility(View.VISIBLE);
                                checkIfFollowing();
                            } else {
                                btnFollow.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(this, getString(R.string.buyer_not_found), Toast.LENGTH_SHORT).show();
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                });
    }

    private void updateBuyerUI() {
        if (currentBuyer == null) return;
        tvBuyerName.setText(currentBuyer.getUsername() != null ? currentBuyer.getUsername() : getString(R.string.unknown));
        tvBuyerEmail.setText(currentBuyer.getEmail() != null ? currentBuyer.getEmail() : "");

        if (currentBuyer.isSeller()) {
            tvBeatsPurchased.setText(String.valueOf(currentBuyer.getStats().getBeatsSold()));
            tvTotalSpent.setText(currentBuyer.getStats().getFormattedTotalEarned());
        } else {
            tvBeatsPurchased.setText(String.valueOf(currentBuyer.getStats().getBeatsPurchased()));
            tvTotalSpent.setText(currentBuyer.getStats().getFormattedTotalSpent());
        }

        if (currentBuyer.getBio() != null && !currentBuyer.getBio().isEmpty()) {
            tvBio.setText(currentBuyer.getBio());
            tvBio.setVisibility(View.VISIBLE);
        }
        if (currentBuyer.getLocation() != null && !currentBuyer.getLocation().isEmpty()) {
            tvLocation.setText("📍 " + currentBuyer.getLocation());
            tvLocation.setVisibility(View.VISIBLE);
        }
        loadAvatar(currentBuyer.getProfileImage());
    }

    private void loadSocialLinks() {
        if (currentBuyer == null || socialLinksLayout == null) return;
        db.collection("users").document(buyerId).get()
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
                if (bitmap != null) { ivBuyerAvatar.setImageBitmap(bitmap); return; }
            } catch (Exception ignored) {}
        }
        ivBuyerAvatar.setImageResource(R.drawable.ic_profile_placeholder);
    }

    private void checkIfFollowing() {
        followManager.isFollowing(currentUserId, buyerId, new FollowManager.FollowCallback() {
            @Override
            public void onSuccess(boolean following) {
                isFollowing = following;
                runOnUiThread(() -> updateFollowButton());
            }
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error checking follow: " + error);
            }
        });
    }

    // ✅ Проверяем, есть ли уже оценка от пользователя (не блокируем, а запоминаем)
    private void checkIfUserRated() {
        db.collection("buyer_reviews")
                .whereEqualTo("buyerId", buyerId)
                .whereEqualTo("reviewerId", currentUserId)
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
            followManager.unfollowUser(currentUserId, buyerId, new FollowManager.FollowCallback() {
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
                    Toast.makeText(BuyerProfileDetailActivity.this,
                            getString(R.string.follow_error) + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            followManager.followUser(currentUserId, buyerId, new FollowManager.FollowCallback() {
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
                    Toast.makeText(BuyerProfileDetailActivity.this,
                            getString(R.string.follow_error) + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateFollowButton() {
        btnFollow.setText(isFollowing ? getString(R.string.following) : getString(R.string.follow));
    }

    private void showRatingDialog() {
        if (currentBuyer == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rating, null);
        RatingBar dialogRatingBar = dialogView.findViewById(R.id.dialogRatingBar);

        // ✅ Если уже есть оценка, показываем её в диалоге
        if (existingRating > 0) {
            dialogRatingBar.setRating((float) existingRating);
        }

        new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle(getString(R.string.rate_user) + " " + (currentBuyer.getUsername() != null ? currentBuyer.getUsername() : ""))
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
            db.collection("buyer_reviews").document(existingReviewId)
                    .update("rating", (double) rating, "updatedAt", System.currentTimeMillis())
                    .addOnSuccessListener(a -> {
                        Toast.makeText(this, getString(R.string.rating_updated), Toast.LENGTH_SHORT).show();
                        existingRating = rating;
                        updateAverageRating();
                        sendRatingNotification(rating);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            // Создаем новую оценку
            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("buyerId", buyerId);
            reviewData.put("reviewerId", currentUserId);
            reviewData.put("rating", (double) rating);
            reviewData.put("createdAt", System.currentTimeMillis());

            db.collection("buyer_reviews").add(reviewData)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(this, getString(R.string.rated), Toast.LENGTH_SHORT).show();
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
            firestoreHelper.sendRatingNotification(buyerId, username, (float) rating);
        });
    }

    private void updateAverageRating() {
        db.collection("buyer_reviews").whereEqualTo("buyerId", buyerId).get()
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
                    db.collection("users").document(buyerId).update("rating", avg);
                    Log.d(TAG, "Updated rating for buyer: " + buyerId + " -> " + avg + " (from " + count + " reviews)");
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) { userListener.remove(); userListener = null; }
    }
}