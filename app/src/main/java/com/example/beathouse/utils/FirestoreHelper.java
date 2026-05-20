// utils/FirestoreHelper.java (ПОЛНАЯ ВЕРСИЯ - С ФИЛЬТРАЦИЕЙ УВЕДОМЛЕНИЙ)
package com.example.beathouse.utils;

import android.util.Log;
import com.example.beathouse.models.Beat;
import com.example.beathouse.models.NotificationSettings;
import com.example.beathouse.models.Order;
import com.example.beathouse.models.Producer;
import com.example.beathouse.models.User;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FirestoreHelper {
    private final FirebaseFirestore db;
    private static final String TAG = "FirestoreHelper";
    private static final int CHUNK_SIZE = 900 * 1024;
    private final Map<String, String> audioCache = new HashMap<>();

    public interface FirestoreCallback {
        void onSuccess(Object result);
        void onError(String error);
    }

    public interface ProgressCallback {
        void onProgress(int currentChunk, int totalChunks, String type);
        void onComplete(Object result);
        void onError(String error);
    }

    public FirestoreHelper() {
        db = FirebaseFirestore.getInstance();
    }

    private void safeCallback(FirestoreCallback callback, Object result) {
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onSuccess(result));
        }
    }
    private void safeError(FirestoreCallback callback, String error) {
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(error));
        }
    }

    // ========== ROLE SWITCH ==========

    public void switchUserRole(String userId, String newRole, FirestoreCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", newRole);

        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(a -> {
                    Log.d(TAG, "✅ User role updated to: " + newRole);
                    if ("seller".equals(newRole)) {
                        createProducerIfNotExists(userId, callback);
                    } else {
                        callback.onSuccess(newRole);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to update role: " + e.getMessage());
                    callback.onError(e.getMessage());
                });
    }

    private void createProducerIfNotExists(String userId, FirestoreCallback callback) {
        db.collection("producers").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        db.collection("users").document(userId).get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc.exists()) {
                                        Map<String, Object> userData = userDoc.getData();
                                        Map<String, Object> producerData = new HashMap<>();
                                        producerData.put("producerId", userId);
                                        producerData.put("username", userData.getOrDefault("username", "Producer"));
                                        producerData.put("email", userData.getOrDefault("email", ""));
                                        producerData.put("displayName", userData.getOrDefault("username", "Producer"));
                                        producerData.put("profileImage", userData.getOrDefault("profileImage", ""));
                                        producerData.put("bio", userData.getOrDefault("bio", ""));
                                        producerData.put("rating", 0.0);
                                        producerData.put("totalBeats", 0); producerData.put("totalSales", 0); producerData.put("totalRevenue", 0.0);
                                        producerData.put("followers", 0); producerData.put("following", 0);
                                        producerData.put("verified", false); producerData.put("featured", false);
                                        producerData.put("location", "");
                                        producerData.put("createdAt", System.currentTimeMillis());
                                        producerData.put("genres", new ArrayList<>());
                                        db.collection("producers").document(userId).set(producerData)
                                                .addOnSuccessListener(a -> callback.onSuccess("seller"))
                                                .addOnFailureListener(e -> callback.onError(e.getMessage()));
                                    }
                                });
                    } else callback.onSuccess("seller");
                });
    }

    // ========== ACCOUNT DELETION ==========

    public void markAccountForDeletion(String userId, String reason, FirestoreCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("markedForDeletion", true);
        updates.put("deletionRequestedAt", System.currentTimeMillis());
        updates.put("deletionReason", reason != null ? reason : "");
        updates.put("status", "deleted");

        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(a -> {
                    Log.d(TAG, "✅ Account marked for deletion: " + userId);
                    db.collection("producers").document(userId).update(updates)
                            .addOnFailureListener(e -> Log.d(TAG, "No producer profile to mark"));
                    safeCallback(callback, "Account marked for deletion");
                })
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    public void deleteAccountCompletely(String userId, FirestoreCallback callback) {
        Log.d(TAG, "🗑️ Starting complete account deletion for: " + userId);
        deleteUserBeats(userId, new FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                Log.d(TAG, "✅ Beats deleted");
                deleteUserOrders(userId, new FirestoreCallback() {
                    @Override
                    public void onSuccess(Object result) {
                        Log.d(TAG, "✅ Orders deleted");
                        deleteUserNotifications(userId, new FirestoreCallback() {
                            @Override
                            public void onSuccess(Object result) {
                                Log.d(TAG, "✅ Notifications deleted");
                                deleteUserDocument(userId, new FirestoreCallback() {
                                    @Override
                                    public void onSuccess(Object result) {
                                        Log.d(TAG, "✅ User document deleted");
                                        deleteUserFollows(userId, new FirestoreCallback() {
                                            @Override
                                            public void onSuccess(Object result) {
                                                Log.d(TAG, "✅ User follows deleted");
                                                deleteProducerDocument(userId, callback);
                                            }
                                            @Override
                                            public void onError(String error) { safeError(callback, error); }
                                        });
                                    }
                                    @Override
                                    public void onError(String error) { safeError(callback, error); }
                                });
                            }
                            @Override
                            public void onError(String error) { safeError(callback, error); }
                        });
                    }
                    @Override
                    public void onError(String error) { safeError(callback, error); }
                });
            }
            @Override
            public void onError(String error) { safeError(callback, error); }
        });
    }

    private void deleteUserFollows(String userId, FirestoreCallback callback) {
        db.collection("follows").whereEqualTo("followerId", userId).get()
                .addOnSuccessListener(snap1 -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snap1) batch.delete(doc.getReference());
                    db.collection("follows").whereEqualTo("followingId", userId).get()
                            .addOnSuccessListener(snap2 -> {
                                for (DocumentSnapshot doc : snap2) batch.delete(doc.getReference());
                                batch.commit()
                                        .addOnSuccessListener(a -> safeCallback(callback, true))
                                        .addOnFailureListener(e -> safeError(callback, e.getMessage()));
                            })
                            .addOnFailureListener(e -> safeCallback(callback, true));
                })
                .addOnFailureListener(e -> safeCallback(callback, true));
    }

    private void deleteUserBeats(String userId, FirestoreCallback callback) {
        db.collection("beats").whereEqualTo("producerId", userId).get()
                .addOnSuccessListener(snap -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snap) {
                        batch.delete(doc.getReference());
                        batch.delete(doc.getReference().collection("audio_chunks").document("info"));
                    }
                    batch.commit()
                            .addOnSuccessListener(a -> safeCallback(callback, true))
                            .addOnFailureListener(e -> safeError(callback, e.getMessage()));
                })
                .addOnFailureListener(e -> safeCallback(callback, true));
    }

    private void deleteUserOrders(String userId, FirestoreCallback callback) {
        db.collection("orders").whereEqualTo("buyerId", userId).get()
                .addOnSuccessListener(snap1 -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snap1) batch.delete(doc.getReference());
                    db.collection("orders").whereEqualTo("producerId", userId).get()
                            .addOnSuccessListener(snap2 -> {
                                for (DocumentSnapshot doc : snap2) batch.delete(doc.getReference());
                                batch.commit()
                                        .addOnSuccessListener(a -> safeCallback(callback, true))
                                        .addOnFailureListener(e -> safeError(callback, e.getMessage()));
                            })
                            .addOnFailureListener(e -> safeCallback(callback, true));
                })
                .addOnFailureListener(e -> safeCallback(callback, true));
    }

    private void deleteUserNotifications(String userId, FirestoreCallback callback) {
        db.collection("notifications").whereEqualTo("userId", userId).get()
                .addOnSuccessListener(snap -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snap) batch.delete(doc.getReference());
                    batch.commit()
                            .addOnSuccessListener(a -> safeCallback(callback, true))
                            .addOnFailureListener(e -> safeCallback(callback, true));
                })
                .addOnFailureListener(e -> safeCallback(callback, true));
    }

    private void deleteUserDocument(String userId, FirestoreCallback callback) {
        db.collection("users").document(userId).delete()
                .addOnSuccessListener(a -> safeCallback(callback, true))
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    private void deleteProducerDocument(String userId, FirestoreCallback callback) {
        db.collection("producers").document(userId).delete()
                .addOnSuccessListener(a -> safeCallback(callback, "Account fully deleted"))
                .addOnFailureListener(e -> safeCallback(callback, "Account fully deleted (no producer profile)"));
    }

    // ========== NOTIFICATIONS ==========

    // ✅ Базовый метод отправки уведомления (с проверкой настроек)
    public void sendNotification(String userId, String type, String title, String msg,
                                 String senderId, String senderName, FirestoreCallback callback) {
        // ✅ Проверяем настройки пользователя перед отправкой
        checkUserNotificationSettings(userId, type, new FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                boolean isEnabled = (Boolean) result;
                if (!isEnabled) {
                    Log.d(TAG, "⚠️ Notification type '" + type + "' disabled for user: " + userId);
                    if (callback != null) callback.onSuccess("disabled");
                    return;
                }

                // Отправляем уведомление
                String id = "NOTIF_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
                Map<String, Object> data = new HashMap<>();
                data.put("notificationId", id);
                data.put("userId", userId);
                data.put("type", type);
                data.put("title", title);
                data.put("message", msg);
                data.put("read", false);
                data.put("createdAt", System.currentTimeMillis());

                if (senderId != null) data.put("senderId", senderId);
                if (senderName != null) data.put("senderName", senderName);

                db.collection("notifications").document(id).set(data)
                        .addOnSuccessListener(a -> { if (callback != null) callback.onSuccess(id); })
                        .addOnFailureListener(e -> { if (callback != null) callback.onError(e.getMessage()); });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Error checking notification settings: " + error);
                // В случае ошибки все равно отправляем
                String id = "NOTIF_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
                Map<String, Object> data = new HashMap<>();
                data.put("notificationId", id);
                data.put("userId", userId);
                data.put("type", type);
                data.put("title", title);
                data.put("message", msg);
                data.put("read", false);
                data.put("createdAt", System.currentTimeMillis());

                if (senderId != null) data.put("senderId", senderId);
                if (senderName != null) data.put("senderName", senderName);

                db.collection("notifications").document(id).set(data)
                        .addOnSuccessListener(a -> { if (callback != null) callback.onSuccess(id); })
                        .addOnFailureListener(e -> { if (callback != null) callback.onError(e.getMessage()); });
            }
        });
    }

    // ✅ Проверка настроек уведомлений пользователя
    private void checkUserNotificationSettings(String userId, String type, FirestoreCallback callback) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onSuccess(true);
                        return;
                    }

                    User user = User.fromMap(doc.getData());
                    NotificationSettings settings = user.getNotificationSettings();

                    boolean isEnabled = settings.isEnabled(type);
                    callback.onSuccess(isEnabled);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load notification settings: " + e.getMessage());
                    callback.onSuccess(true); // По умолчанию отправляем
                });
    }

    // ✅ Уведомление о подписке с отправителем
    public void sendFollowNotification(String toUserId, String fromUserId, String fromUsername) {
        sendNotification(toUserId, "follow",
                fromUsername + " started following you",
                "New follower!",
                fromUserId, fromUsername, null);
    }

    // ✅ Уведомление о продаже
    public void sendSaleNotification(String sellerId, String beatTitle, double amount) {
        sendNotification(sellerId, "sale",
                "New Sale! 💰",
                "Your beat \"" + beatTitle + "\" was sold for $" + String.format("%.0f", amount),
                null, null, null);
    }

    // ✅ Уведомление о покупке
    public void sendPurchaseNotification(String buyerId, String beatTitle) {
        sendNotification(buyerId, "purchase",
                "Purchase Complete! 🎵",
                "You purchased \"" + beatTitle + "\"",
                null, null, null);
    }

    // ✅ Уведомление о рейтинге
    public void sendRatingNotification(String toUserId, String fromUsername, float rating) {
        sendNotification(toUserId, "rating",
                "New Rating! ⭐",
                fromUsername + " rated you " + String.format("%.1f", rating) + " stars!",
                null, null, null);
    }

    // ========== SMART SAVE / UPDATE ==========

    // ✅ МЕТОД ДЛЯ ОБНОВЛЕНИЯ БИТА (без кэша)
    public void updateBeat(Beat beat, ProgressCallback callback) {
        if (beat.getId() == null || beat.getId().isEmpty()) {
            if (callback != null) callback.onError("Beat ID is required for update");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", beat.getTitle());
        updates.put("bpm", beat.getBpm());
        updates.put("key", beat.getKey());
        updates.put("genre", beat.getGenre());
        updates.put("price", beat.getPrice());
        updates.put("isFree", beat.isFree());
        updates.put("description", beat.getDescription() != null ? beat.getDescription() : "");
        updates.put("updatedAt", System.currentTimeMillis());

        final boolean hasNewCover = beat.hasCover() && beat.getCoverImage() != null && !beat.getCoverImage().isEmpty();
        final String newCover = hasNewCover ? beat.getCoverImage() : null;

        if (hasNewCover) {
            updates.put("coverImage", newCover);
            Log.d(TAG, "🖼️ Updating cover image, length: " + newCover.length());
        }

        Log.d(TAG, "📝 Updating beat: " + beat.getTitle());
        Log.d(TAG, "  ID: " + beat.getId());

        db.collection("beats").document(beat.getId()).update(updates)
                .addOnSuccessListener(a -> {
                    Log.d(TAG, "✅ Beat updated successfully");
                    if (callback != null) callback.onComplete(beat.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to update beat: " + e.getMessage());
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    public void saveBeatSmart(Beat beat, String audio, String cover, ProgressCallback callback) {
        if (beat.getId() != null && !beat.getId().isEmpty() && (audio == null || audio.isEmpty())) {
            updateBeat(beat, callback);
            return;
        }

        if (audio == null || audio.isEmpty()) {
            if (callback != null) callback.onError("Audio required");
            return;
        }

        byte[] audioBytes;
        try {
            audioBytes = android.util.Base64.decode(audio, android.util.Base64.DEFAULT);
        } catch (Exception e) {
            if (callback != null) callback.onError("Failed to decode audio: " + e.getMessage());
            return;
        }

        if (beat.getId() == null || beat.getId().isEmpty())
            beat.setId(db.collection("beats").document().getId());

        Map<String, Object> data = beat.toMap();
        data.put("isChunked", true);
        data.put("status", "active");
        data.put("producerId", beat.getProducerId());
        data.put("userId", beat.getUserId());

        if (cover != null && !cover.isEmpty() && cover.length() <= CHUNK_SIZE)
            data.put("coverImage", cover);

        Log.d(TAG, "💾 Saving new beat: " + beat.getTitle() + " (" + audioBytes.length + " bytes)");

        db.collection("beats").document(beat.getId()).set(data)
                .addOnSuccessListener(a -> {
                    Log.d(TAG, "✅ Beat document created");
                    List<byte[]> chunks = splitIntoByteChunks(audioBytes, CHUNK_SIZE);
                    WriteBatch batch = db.batch();
                    CollectionReference ref = db.collection("beats").document(beat.getId()).collection("audio_chunks");
                    Map<String, Object> info = new HashMap<>();
                    info.put("totalChunks", chunks.size());
                    info.put("totalSize", audioBytes.length);
                    info.put("useBlob", true);
                    batch.set(ref.document("info"), info);

                    for (int i = 0; i < chunks.size(); i++) {
                        Map<String, Object> cd = new HashMap<>();
                        cd.put("dataBlob", Blob.fromBytes(chunks.get(i)));
                        cd.put("index", i);
                        batch.set(ref.document("chunk_" + i), cd);

                        if (callback != null && (i % 5 == 0 || i == chunks.size() - 1))
                            callback.onProgress(i + 1, chunks.size(), "audio");
                    }

                    batch.commit()
                            .addOnSuccessListener(av -> {
                                Log.d(TAG, "✅ Audio chunks saved as Blobs: " + chunks.size() + " chunks");
                                audioCache.put(beat.getId(), audio);
                                updateProducerStats(beat.getProducerId());
                                if (callback != null) callback.onComplete(beat.getId());
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to save chunks: " + e.getMessage());
                                if (callback != null) callback.onError(e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save beat document: " + e.getMessage());
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    private List<byte[]> splitIntoByteChunks(byte[] data, int size) {
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < data.length; i += size) {
            int length = Math.min(size, data.length - i);
            byte[] chunk = new byte[length];
            System.arraycopy(data, i, chunk, 0, length);
            chunks.add(chunk);
        }
        return chunks;
    }


    public void loadBeatAudioSmart(String beatId, FirestoreCallback callback) {
        String cached = audioCache.get(beatId);
        if (cached != null) { safeCallback(callback, cached); return; }
        loadFullBeatAudio(beatId, callback);
    }

    public void loadFullBeatAudio(String beatId, FirestoreCallback callback) {
        String cached = audioCache.get(beatId);
        if (cached != null) { safeCallback(callback, cached); return; }

        db.collection("beats").document(beatId).collection("audio_chunks").document("info").get()
                .addOnSuccessListener(infoDoc -> {
                    boolean useBlob = infoDoc.exists() && infoDoc.contains("useBlob") && Boolean.TRUE.equals(infoDoc.getBoolean("useBlob"));

                    db.collection("beats").document(beatId).collection("audio_chunks")
                            .orderBy("index").get()
                            .addOnSuccessListener(chunkSnap -> {
                                if (chunkSnap.isEmpty()) { safeError(callback, "No audio"); return; }

                                if (useBlob) {
                                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                    for (DocumentSnapshot chunkDoc : chunkSnap) {
                                        Blob blob = chunkDoc.getBlob("dataBlob");
                                        if (blob != null) {
                                            try {
                                                baos.write(blob.toBytes());
                                            } catch (Exception e) {}
                                        }
                                    }
                                    String result = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT);
                                    audioCache.put(beatId, result);
                                    safeCallback(callback, result);
                                } else {
                                    StringBuilder fullAudio = new StringBuilder();
                                    for (DocumentSnapshot chunkDoc : chunkSnap) {
                                        String chunk = chunkDoc.getString("data");
                                        if (chunk != null) fullAudio.append(chunk);
                                    }
                                    String result = fullAudio.toString();
                                    audioCache.put(beatId, result);
                                    safeCallback(callback, result);
                                }
                            })
                            .addOnFailureListener(e -> safeError(callback, e.getMessage()));
                })
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    // ========== USERS ==========

    public void getUser(String userId, FirestoreCallback callback) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) safeCallback(callback, User.fromMap(doc.getData()));
                    else safeError(callback, "User not found");
                })
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    public void updateUser(User user, FirestoreCallback callback) {
        if (user.getId() == null || user.getId().isEmpty()) { safeError(callback, "User ID is null"); return; }
        db.collection("users").document(user.getId()).set(user.toMap())
                .addOnSuccessListener(a -> safeCallback(callback, user))
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    // ========== PRODUCERS ==========

    public void getProducer(String producerId, FirestoreCallback callback) {
        db.collection("producers").document(producerId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) safeCallback(callback, Producer.fromMap(doc.getData()));
                    else safeError(callback, "Producer not found");
                })
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    public void getAllProducers(FirestoreCallback callback) {
        db.collection("producers").orderBy("rating", Query.Direction.DESCENDING).limit(50).get()
                .addOnSuccessListener(q -> {
                    List<Producer> list = new ArrayList<>();
                    for (DocumentSnapshot d : q) list.add(Producer.fromMap(d.getData()));
                    safeCallback(callback, list);
                })
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    public void getProducerBeats(String producerId, FirestoreCallback callback) {
        Log.d(TAG, "🔍 Loading beats for producer: " + producerId);

        if (producerId == null || producerId.isEmpty()) {
            Log.e(TAG, "❌ ProducerId is null or empty");
            safeCallback(callback, new ArrayList<Beat>());
            return;
        }

        db.collection("beats")
                .whereEqualTo("producerId", producerId)
                .whereEqualTo("status", "active")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(q -> {
                    List<Beat> beats = new ArrayList<>();
                    for (DocumentSnapshot d : q) {
                        Beat beat = Beat.fromMap(d.getData());
                        if (beat != null) {
                            if (beat.getId() == null || beat.getId().isEmpty()) beat.setId(d.getId());
                            beats.add(beat);
                            Log.d(TAG, "  ✅ Loaded beat: " + beat.getTitle() + " (ID: " + beat.getId() + ")");
                        }
                    }
                    Log.d(TAG, "✅ Total beats found: " + beats.size());
                    safeCallback(callback, beats);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading producer beats: " + e.getMessage());
                    safeError(callback, e.getMessage());
                });
    }

    // ========== BEATS ==========

    public ListenerRegistration getBeatsRealtime(FirestoreCallback callback) {
        // ✅ Используем фильтр по статусу прямо в запросе для надежности
        return db.collection("beats")
                .whereEqualTo("status", "active")
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        Log.e(TAG, "Realtime query failed: " + err.getMessage());
                        // Если индекс не создан, откатываемся к клиентской фильтрации
                        if (err.getMessage() != null && err.getMessage().contains("INDEX")) {
                            loadBeatsWithClientSideFiltering(callback);
                        } else {
                            safeError(callback, err.getMessage());
                        }
                        return;
                    }
                    processBeatsSnapshot(snap, callback);
                });
    }

    private void loadBeatsWithClientSideFiltering(FirestoreCallback callback) {
        db.collection("beats")
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        safeError(callback, err.getMessage());
                        return;
                    }
                    processBeatsSnapshot(snap, callback);
                });
    }

    private void processBeatsSnapshot(com.google.firebase.firestore.QuerySnapshot snap, FirestoreCallback callback) {
        List<Beat> beats = new ArrayList<>();
        if (snap != null) {
            for (DocumentSnapshot d : snap) {
                Beat b = Beat.fromMap(d.getData());
                if (b != null) {
                    b.setId(d.getId());

                    // ✅ Двойная проверка на клиенте для максимальной безопасности
                    String status = b.getStatus();
                    // Показываем если статус active ИЛИ если статус вообще не задан (старые биты)
                    // Но НИКОГДА не показываем deleted
                    if (status == null || "active".equalsIgnoreCase(status)) {
                        if (!"deleted".equalsIgnoreCase(status)) {
                            beats.add(b);
                        }
                    }
                }
            }
        }

        // ✅ Всегда сортируем на клиенте по дате создания (новые сверху)
        beats.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

        Log.d(TAG, "Fetched and filtered " + beats.size() + " beats");
        safeCallback(callback, beats);
    }

    public void searchBeats(String query, FirestoreCallback callback) {
        String q = query.toLowerCase().trim();
        db.collection("beats").whereEqualTo("status", "active")
                .orderBy("createdAt", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snap -> {
                    List<Beat> res = new ArrayList<>();
                    for (DocumentSnapshot d : snap) {
                        Beat b = Beat.fromMap(d.getData());
                        if (b != null && b.getTitle() != null && b.getTitle().toLowerCase().contains(q)) res.add(b);
                    }
                    safeCallback(callback, res);
                })
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    public void getBeatsByIds(List<String> ids, FirestoreCallback callback) {
        if (ids == null || ids.isEmpty()) {
            safeCallback(callback, new ArrayList<>());
            return;
        }

        List<Beat> all = new ArrayList<>();
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 10) {
            batches.add(ids.subList(i, Math.min(i + 10, ids.size())));
        }

        final int[] done = {0};
        final int totalBatches = batches.size();
        Log.d(TAG, "🔍 Loading beats by IDs: " + ids.size() + " beats in " + totalBatches + " batches");

        for (List<String> batch : batches) {
            db.collection("beats")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (DocumentSnapshot d : snap) {
                            Beat beat = Beat.fromMap(d.getData());
                            if (beat != null) {
                                if (beat.getId() == null || beat.getId().isEmpty()) beat.setId(d.getId());
                                all.add(beat);
                                Log.d(TAG, "  ✅ Loaded beat: " + beat.getTitle() + " | Producer: " + beat.getProducerId());
                            }
                        }
                        done[0]++;
                        Log.d(TAG, "📊 Batch " + done[0] + "/" + totalBatches + " completed");
                        if (done[0] >= totalBatches) {
                            Log.d(TAG, "✅ All batches loaded: " + all.size() + " beats total");
                            safeCallback(callback, all);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Error loading batch: " + e.getMessage());
                        done[0]++;
                        if (done[0] >= totalBatches) safeCallback(callback, all);
                    });
        }
    }

    // ========== ORDERS ==========

    public void createOrder(Order order, FirestoreCallback callback) {
        db.collection("orders").document(order.getId()).set(order.toMap())
                .addOnSuccessListener(a -> safeCallback(callback, order.getId()))
                .addOnFailureListener(e -> safeError(callback, e.getMessage()));
    }

    public void getUserOrders(String userId, FirestoreCallback callback) {
        Log.d(TAG, "📋 Loading orders for user: " + userId);
        if (userId == null || userId.isEmpty()) {
            safeCallback(callback, new ArrayList<Order>());
            return;
        }
        db.collection("orders").whereEqualTo("buyerId", userId).get()
                .addOnSuccessListener(snap -> {
                    List<Order> orders = new ArrayList<>();
                    for (DocumentSnapshot d : snap) {
                        Order order = Order.fromMap(d.getData());
                        if (order != null) orders.add(order);
                    }
                    Log.d(TAG, "✅ Loaded " + orders.size() + " orders for user: " + userId);
                    safeCallback(callback, orders);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading orders: " + e.getMessage());
                    safeCallback(callback, new ArrayList<Order>());
                });
    }

    public ListenerRegistration getSellerSalesRealtime(String sellerId, FirestoreCallback callback) {
        Log.d(TAG, "📊 Listening for sales for seller: " + sellerId);
        if (sellerId == null || sellerId.isEmpty()) {
            safeCallback(callback, new ArrayList<Order>());
            return null;
        }
        return db.collection("orders")
                .whereEqualTo("producerId", sellerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        Log.e(TAG, "❌ Realtime sales error: " + err.getMessage());
                        safeError(callback, err.getMessage());
                        return;
                    }
                    List<Order> orders = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap) {
                            Order order = Order.fromMap(d.getData());
                            if (order != null) {
                                order.setId(d.getId());
                                orders.add(order);
                            }
                        }
                    }
                    Log.d(TAG, "✅ Realtime sales updated: " + orders.size());
                    safeCallback(callback, orders);
                });
    }

    public void getSellerSales(String sellerId, FirestoreCallback callback) {
        Log.d(TAG, "📊 Loading sales for seller: " + sellerId);
        if (sellerId == null || sellerId.isEmpty()) {
            safeCallback(callback, new ArrayList<Order>());
            return;
        }
        db.collection("orders").whereEqualTo("producerId", sellerId)
                .orderBy("createdAt", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snap -> {
                    List<Order> orders = new ArrayList<>();
                    for (DocumentSnapshot d : snap) {
                        Order order = Order.fromMap(d.getData());
                        if (order != null) {
                            order.setId(d.getId());
                            orders.add(order);
                        }
                    }
                    Log.d(TAG, "✅ Loaded " + orders.size() + " sales for seller: " + sellerId);
                    safeCallback(callback, orders);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading sales: " + e.getMessage());
                    safeError(callback, e.getMessage());
                });
    }

    // ========== HELPERS ==========

    public void updateProducerAfterSale(String producerId, double amount, int count) {
        if (producerId == null) return;
        db.collection("producers").document(producerId)
                .update("totalSales", FieldValue.increment(1), "totalRevenue", FieldValue.increment(amount));
        db.collection("users").document(producerId)
                .update("stats.totalEarned", FieldValue.increment(amount), "stats.beatsSold", FieldValue.increment(1));
    }

    private void updateProducerStats(String producerId) {
        if (producerId == null) return;
        db.collection("beats").whereEqualTo("producerId", producerId).whereEqualTo("status", "active").get()
                .addOnSuccessListener(snap -> {
                    int count = snap.size();
                    Log.d(TAG, "Updating producer stats: " + producerId + " totalBeats: " + count);
                    db.collection("producers").document(producerId).update("totalBeats", count);
                });
    }


    public void clearCache() {
        audioCache.clear();
        Log.d(TAG, "🗑️ Audio cache cleared");
    }
}