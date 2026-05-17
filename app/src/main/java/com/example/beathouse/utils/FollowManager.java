package com.example.beathouse.utils;

import android.util.Log;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FollowManager {
    private static final String TAG = "FollowManager";
    private final FirebaseFirestore db;
    private final FirestoreHelper firestoreHelper;

    public FollowManager() {
        db = FirebaseFirestore.getInstance();
        firestoreHelper = new FirestoreHelper();
    }

    public interface FollowCallback {
        void onSuccess(boolean isFollowing);
        void onError(String error);
    }

    public interface FollowerCountCallback {
        void onCountUpdated(long followers, long following);
        void onError(String error);
    }

    // ✅ ПОДПИСАТЬСЯ - универсально для любого пользователя
    public void followUser(String currentUserId, String targetUserId, FollowCallback callback) {
        if (currentUserId == null || targetUserId == null || currentUserId.equals(targetUserId)) {
            if (callback != null) callback.onError("Cannot follow yourself");
            return;
        }

        String followId = currentUserId + "_" + targetUserId;

        // Проверяем, нет ли уже подписки
        db.collection("follows").document(followId).get()
                .addOnSuccessListener(existingDoc -> {
                    if (existingDoc.exists()) {
                        if (callback != null) callback.onSuccess(true);
                        return;
                    }

                    // Получаем username для уведомления
                    db.collection("users").document(currentUserId).get()
                            .addOnSuccessListener(userDoc -> {
                                String username = "Someone";
                                if (userDoc.exists()) {
                                    String name = userDoc.getString("username");
                                    if (name != null && !name.isEmpty()) username = name;
                                }

                                String finalUsername = username;

                                // ✅ Сначала проверяем, нужно ли обновлять producers
                                checkUserRoleAndUpdateProducer(targetUserId, new ProducerCheckCallback() {
                                    @Override
                                    public void onProducerCheck(boolean isProducer, WriteBatch batchForProducer) {
                                        // Создаем новый batch для всех операций
                                        WriteBatch batch = db.batch();

                                        // 1. Создаем документ подписки
                                        Map<String, Object> followData = new HashMap<>();
                                        followData.put("followerId", currentUserId);
                                        followData.put("followingId", targetUserId);
                                        followData.put("createdAt", System.currentTimeMillis());
                                        batch.set(db.collection("follows").document(followId), followData);

                                        // 2. Увеличиваем счетчик followers у целевого пользователя
                                        batch.update(db.collection("users").document(targetUserId),
                                                "followers", FieldValue.increment(1));

                                        // 3. Увеличиваем счетчик following у текущего пользователя
                                        batch.update(db.collection("users").document(currentUserId),
                                                "following", FieldValue.increment(1));

                                        // 4. Если целевой пользователь продавец - обновляем и producers коллекцию
                                        if (isProducer) {
                                            batch.update(db.collection("producers").document(targetUserId),
                                                    "followers", FieldValue.increment(1));
                                            Log.d(TAG, "✅ Will update producers followers for: " + targetUserId);
                                        }

                                        // ✅ Выполняем batch
                                        batch.commit()
                                                .addOnSuccessListener(a -> {
                                                    // Отправляем уведомление
                                                    sendFollowNotification(targetUserId, currentUserId, finalUsername);
                                                    if (callback != null) callback.onSuccess(true);
                                                    Log.d(TAG, "✅ Followed: " + currentUserId + " -> " + targetUserId);
                                                })
                                                .addOnFailureListener(e -> {
                                                    if (callback != null) callback.onError(e.getMessage());
                                                    Log.e(TAG, "❌ Follow failed: " + e.getMessage());
                                                });
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onError(e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ✅ ОТПИСАТЬСЯ - универсально для любого пользователя
    public void unfollowUser(String currentUserId, String targetUserId, FollowCallback callback) {
        if (currentUserId == null || targetUserId == null) {
            if (callback != null) callback.onError("Invalid parameters");
            return;
        }

        String followId = currentUserId + "_" + targetUserId;

        // Проверяем, существует ли подписка
        db.collection("follows").document(followId).get()
                .addOnSuccessListener(followDoc -> {
                    if (!followDoc.exists()) {
                        Log.w(TAG, "⚠️ Follow relationship not found: " + followId);
                        if (callback != null) callback.onSuccess(false);
                        return;
                    }

                    // ✅ Проверяем роль пользователя перед удалением
                    checkUserRoleAndUpdateProducer(targetUserId, new ProducerCheckCallback() {
                        @Override
                        public void onProducerCheck(boolean isProducer, WriteBatch batchForProducer) {
                            WriteBatch batch = db.batch();

                            // 1. Удаляем документ подписки
                            batch.delete(db.collection("follows").document(followId));

                            // 2. Уменьшаем счетчик followers у целевого пользователя
                            batch.update(db.collection("users").document(targetUserId),
                                    "followers", FieldValue.increment(-1));

                            // 3. Уменьшаем счетчик following у текущего пользователя
                            batch.update(db.collection("users").document(currentUserId),
                                    "following", FieldValue.increment(-1));

                            // 4. Если целевой пользователь продавец - обновляем и producers коллекцию
                            if (isProducer) {
                                batch.update(db.collection("producers").document(targetUserId),
                                        "followers", FieldValue.increment(-1));
                                Log.d(TAG, "✅ Will update producers followers for: " + targetUserId);
                            }

                            batch.commit()
                                    .addOnSuccessListener(a -> {
                                        if (callback != null) callback.onSuccess(false);
                                        Log.d(TAG, "✅ Unfollowed: " + currentUserId + " -> " + targetUserId);
                                    })
                                    .addOnFailureListener(e -> {
                                        if (callback != null) callback.onError(e.getMessage());
                                        Log.e(TAG, "❌ Unfollow failed: " + e.getMessage());
                                    });
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ✅ Интерфейс для проверки роли
    private interface ProducerCheckCallback {
        void onProducerCheck(boolean isProducer, WriteBatch batch);
    }

    // ✅ Проверка роли пользователя (без использования WriteBatch)
    private void checkUserRoleAndUpdateProducer(String userId, ProducerCheckCallback callback) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    boolean isProducer = false;
                    if (userDoc.exists()) {
                        String role = userDoc.getString("role");
                        isProducer = "seller".equals(role);
                    }
                    callback.onProducerCheck(isProducer, null);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Could not check user role for: " + userId);
                    callback.onProducerCheck(false, null);
                });
    }

    // ✅ Отправка уведомления о подписке
    private void sendFollowNotification(String toUserId, String fromUserId, String fromUsername) {
        String notificationId = "FOLLOW_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);

        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", notificationId);
        notification.put("userId", toUserId);
        notification.put("type", "follow");
        notification.put("title", fromUsername + " started following you");
        notification.put("message", "New follower!");
        notification.put("senderId", fromUserId);
        notification.put("senderName", fromUsername);
        notification.put("read", false);
        notification.put("createdAt", System.currentTimeMillis());

        db.collection("notifications").document(notificationId).set(notification)
                .addOnSuccessListener(a -> Log.d(TAG, "✅ Follow notification sent to: " + toUserId))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to send follow notification: " + e.getMessage()));
    }

    // ✅ ПРОВЕРКА ПОДПИСКИ
    public void isFollowing(String currentUserId, String targetUserId, FollowCallback callback) {
        if (currentUserId == null || targetUserId == null || currentUserId.equals(targetUserId)) {
            if (callback != null) callback.onSuccess(false);
            return;
        }

        String followId = currentUserId + "_" + targetUserId;

        db.collection("follows").document(followId).get()
                .addOnSuccessListener(doc -> {
                    if (callback != null) callback.onSuccess(doc.exists());
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ✅ REAL-TIME слушатель счетчиков
    public ListenerRegistration listenFollowerCounts(String userId, FollowerCountCallback callback) {
        if (userId == null) {
            callback.onError("User ID is null");
            return null;
        }

        return db.collection("users").document(userId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null) {
                        callback.onError(err.getMessage());
                        return;
                    }
                    if (doc != null && doc.exists()) {
                        Long followers = doc.getLong("followers");
                        Long following = doc.getLong("following");
                        callback.onCountUpdated(
                                followers != null ? Math.max(0, followers) : 0,
                                following != null ? Math.max(0, following) : 0
                        );
                    } else {
                        callback.onCountUpdated(0, 0);
                    }
                });
    }
}