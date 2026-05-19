package com.example.beathouse.utils;

import android.util.Log;
import com.example.beathouse.models.DatabaseStructure;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.HashMap;
import java.util.Map;

public class UserSyncHelper {
    private static final String TAG = "UserSyncHelper";

    public static void syncAllUsers(FirebaseFirestore db) {
        db.collection(DatabaseStructure.COLLECTION_USERS).get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap) {
                        String userId = doc.getId();
                        Map<String, Object> data = doc.getData();
                        if (data == null) continue;

                        // Проверяем и обновляем роль
                        if (!data.containsKey("role") || data.get("role") == null) {
                            determineAndSetRole(db, userId, doc);
                        }

                        // Проверяем и обновляем статистику
                        if (!data.containsKey("stats")) {
                            Map<String, Object> stats = new HashMap<>();
                            stats.put("totalSpent", 0.0);
                            stats.put("totalEarned", 0.0);
                            stats.put("beatsPurchased", 0);
                            stats.put("beatsSold", 0);
                            db.collection(DatabaseStructure.COLLECTION_USERS)
                                    .document(userId)
                                    .update("stats", stats);
                            Log.d(TAG, "✅ Stats added to user: " + userId);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Sync failed: " + e.getMessage()));
    }

    private static void determineAndSetRole(FirebaseFirestore db, String userId, DocumentSnapshot userDoc) {
        // Проверяем наличие профиля продюсер
        db.collection(DatabaseStructure.COLLECTION_PRODUCERS)
                .document(userId).get()
                .addOnSuccessListener(prodDoc -> {
                    if (prodDoc.exists()) {
                        // Есть профиль продюсера - seller
                        userDoc.getReference().update("role", "seller");
                        Log.d(TAG, "✅ Role set to seller for: " + userId);
                    } else {
                        // Проверяем есть ли загруженные биты
                        db.collection(DatabaseStructure.COLLECTION_BEATS)
                                .whereEqualTo("userId", userId)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(beatsSnap -> {
                                    String role = beatsSnap.isEmpty() ? "buyer" : "seller";
                                    userDoc.getReference().update("role", role);
                                    Log.d(TAG, "✅ Role set to " + role + " for: " + userId);

                                    // Если продавец - создаем профиль
                                    if ("seller".equals(role) && !prodDoc.exists()) {
                                        createProducerProfileFromUser(db, userId, userDoc.getData());
                                    }
                                });
                    }
                });
    }

    private static void createProducerProfileFromUser(FirebaseFirestore db, String userId, Map<String, Object> userData) {
        if (userData == null) return;

        String username = (String) userData.getOrDefault("username", "Producer");
        String email = (String) userData.getOrDefault("email", "");

        Map<String, Object> producerData = new HashMap<>();
        producerData.put("producerId", userId);
        producerData.put("username", username);
        producerData.put("email", email);
        producerData.put("displayName", username);
        producerData.put("profileImage", userData.getOrDefault("profileImage", ""));
        producerData.put("bio", userData.getOrDefault("bio", ""));
        producerData.put("rating", 0.0);
        producerData.put("totalBeats", 0);
        producerData.put("totalSales", 0);
        producerData.put("totalRevenue", 0.0);
        producerData.put("followers", 0);
        producerData.put("following", 0);
        producerData.put("verified", false);
        producerData.put("featured", false);
        producerData.put("location", "");
        producerData.put("createdAt", System.currentTimeMillis());
        producerData.put("genres", new java.util.ArrayList<>());

        db.collection(DatabaseStructure.COLLECTION_PRODUCERS)
                .document(userId)
                .set(producerData)
                .addOnSuccessListener(a -> Log.d(TAG, "✅ Producer profile created for: " + userId))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to create producer profile: " + e.getMessage()));
    }

    public static void syncUserData(String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        syncSingleUser(db, userId);
    }

    public static void syncSingleUser(FirebaseFirestore db, String userId) {
        db.collection(DatabaseStructure.COLLECTION_USERS).document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Map<String, Object> data = doc.getData();
                    if (data == null) return;

                    Map<String, Object> updates = new HashMap<>();

                    if (!data.containsKey("role") || data.get("role") == null) {
                        determineAndSetRole(db, userId, doc);
                    }

                    if (!data.containsKey("stats")) {
                        Map<String, Object> stats = new HashMap<>();
                        stats.put("totalSpent", 0.0);
                        stats.put("totalEarned", 0.0);
                        stats.put("beatsPurchased", 0);
                        stats.put("beatsSold", 0);
                        updates.put("stats", stats);
                    }

                    if (!updates.isEmpty()) {
                        db.collection(DatabaseStructure.COLLECTION_USERS)
                                .document(userId).update(updates);
                    }
                });
    }
}