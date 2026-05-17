package com.example.beathouse.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.beathouse.models.CartItem;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Environment;
import androidx.core.app.NotificationCompat;
public class FirebaseDownloadService {
    private static final String TAG = "FirebaseDownloadService";
    private Context context;
    private FirebaseFirestore firestore;
    private Handler mainHandler;
    private Handler timeoutHandler;

    // ✅ Уведомления
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private static final String CHANNEL_ID = "beat_download_channel";
    private static final int NOTIFICATION_ID = 1;
    private int currentBeatIndex = 0;
    private int totalBeatsCount = 0;

    public interface DownloadCallback {
        void onProgress(String beatTitle, int progress, int totalBeats, int currentBeat);
        void onBeatDownloaded(String beatTitle, String filePath);
        void onAllDownloadsCompleted();
        void onError(String error);
    }

    public FirebaseDownloadService(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.timeoutHandler = new Handler();

        // ✅ Инициализация уведомлений
        initNotificationChannel();
        initNotification();

        Log.d(TAG, "✅ FirebaseDownloadService (Firestore) initialized");
    }

    // ✅ Инициализация канала уведомлений
    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Beat Download",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Download progress for beats");
            notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        } else {
            notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        }
    }

    // ✅ Инициализация уведомления
    private void initNotification() {
        notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("BeatHouse")
                .setContentText("Preparing download...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(0, 0, true);
    }

    public void downloadBeats(List<CartItem> beats, DownloadCallback callback) {
        if (beats == null || beats.isEmpty()) {
            callback.onError("No beats to download");
            return;
        }

        totalBeatsCount = beats.size();
        currentBeatIndex = 0;

        Log.d(TAG, "🚀 Starting Firestore download of " + beats.size() + " beats");

        // ✅ Показываем уведомление
        showNotification("Starting download...", 0, true);

        new Thread(() -> {
            try {
                int totalBeats = beats.size();
                AtomicInteger completedBeats = new AtomicInteger(0);

                for (int i = 0; i < totalBeats; i++) {
                    currentBeatIndex = i + 1;
                    CartItem beat = beats.get(i);
                    downloadSingleBeat(beat, i, totalBeats, completedBeats, callback);

                    // Задержка между запросами
                    if (i < totalBeats - 1) {
                        Thread.sleep(500);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Download thread error: " + e.getMessage(), e);
                showNotification("Download failed", 0, false);
                mainHandler.post(() -> callback.onError("Download failed: " + e.getMessage()));
            }
        }).start();
    }

    private void downloadSingleBeat(CartItem beat, int currentIndex, int totalBeats,
                                    AtomicInteger completedBeats, DownloadCallback callback) {
        String beatId = beat.getBeatId();
        String beatTitle = beat.getBeatTitle();

        Log.d(TAG, "📥 Downloading from Firestore: " + beatTitle + " (ID: " + beatId + ")");

        // ✅ Обновляем уведомление
        showNotification("Downloading: " + beatTitle, 0, true);

        // Обновляем прогресс в UI
        mainHandler.post(() -> callback.onProgress(beatTitle, 0, totalBeats, currentIndex + 1));

        // Таймаут для запроса
        Runnable timeoutRunnable = () -> {
            Log.e(TAG, "⏰ Timeout for beat: " + beatTitle);
            showNotification("Timeout: " + beatTitle, 0, false);
            handleDownloadError(beatTitle, "Download timeout - check connection",
                    completedBeats, totalBeats, callback);
        };
        timeoutHandler.postDelayed(timeoutRunnable, 20000);

        // Запрос к Firestore
        firestore.collection("beats").document(beatId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    timeoutHandler.removeCallbacks(timeoutRunnable);

                    Log.d(TAG, "📡 Firestore response received for: " + beatTitle);
                    Log.d(TAG, "📊 Document exists: " + documentSnapshot.exists());

                    if (!documentSnapshot.exists()) {
                        Log.e(TAG, "❌ Beat document not found: " + beatId);
                        showNotification("Beat not found: " + beatTitle, 0, false);
                        handleDownloadError(beatTitle, "Beat not found in database", completedBeats, totalBeats, callback);
                        return;
                    }

                    try {
                        // ✅ Обновляем уведомление
                        showNotification("Processing: " + beatTitle, 25, false);

                        // Получаем audioChunks
                        processAudioData(beatTitle, documentSnapshot, currentIndex, totalBeats, completedBeats, callback);

                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error processing document: " + e.getMessage(), e);
                        showNotification("Error: " + beatTitle, 0, false);
                        handleDownloadError(beatTitle, "Data processing error: " + e.getMessage(),
                                completedBeats, totalBeats, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    timeoutHandler.removeCallbacks(timeoutRunnable);

                    Log.e(TAG, "❌ Firestore error for " + beatTitle + ": " + e.getMessage());
                    showNotification("Network error: " + beatTitle, 0, false);
                    String errorMessage = getFirestoreErrorMessage(e);
                    handleDownloadError(beatTitle, errorMessage, completedBeats, totalBeats, callback);
                });
    }

    private void processAudioData(String beatTitle, DocumentSnapshot documentSnapshot,
                                  int currentIndex, int totalBeats,
                                  AtomicInteger completedBeats, DownloadCallback callback) {
        try {
            // ✅ Обновляем уведомление
            showNotification("Downloading chunks: " + beatTitle, 50, false);

            checkAudioChunksSubcollection(beatTitle, documentSnapshot.getReference(),
                    currentIndex, totalBeats, completedBeats, callback);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing audio data: " + e.getMessage(), e);
            showNotification("Processing error: " + beatTitle, 0, false);
            handleDownloadError(beatTitle, "Audio processing error: " + e.getMessage(),
                    completedBeats, totalBeats, callback);
        }
    }

    private void checkAudioChunksSubcollection(String beatTitle, DocumentReference beatRef,
                                               int currentIndex, int totalBeats,
                                               AtomicInteger completedBeats, DownloadCallback callback) {
        Log.d(TAG, "🔍 Checking audio_chunks subcollection for: " + beatTitle);

        beatRef.collection("audio_chunks")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Log.d(TAG, "✅ Found audio_chunks subcollection with " + querySnapshot.size() + " documents");

                        // ✅ Обновляем уведомление
                        showNotification("Assembling: " + beatTitle, 75, false);

                        List<String> chunks = new ArrayList<>();
                        int processed = 0;
                        int totalChunks = querySnapshot.size();

                        // Собираем все документы для сортировки
                        List<DocumentSnapshot> allChunks = new ArrayList<>();
                        for (DocumentSnapshot chunkDoc : querySnapshot) {
                            allChunks.add(chunkDoc);
                        }

                        // Сортируем по ID документа
                        allChunks.sort((doc1, doc2) -> {
                            String id1 = doc1.getId();
                            String id2 = doc2.getId();
                            return id1.compareTo(id2);
                        });

                        // Обрабатываем каждый чанк
                        for (DocumentSnapshot chunkDoc : allChunks) {
                            String chunkId = chunkDoc.getId();

                            // Пробуем разные возможные поля для данных чанка
                            String chunkData = chunkDoc.getString("data");
                            if (chunkData == null) {
                                chunkData = chunkDoc.getString("chunkData");
                            }
                            if (chunkData == null) {
                                chunkData = chunkDoc.getString("content");
                            }
                            if (chunkData == null) {
                                chunkData = chunkDoc.getString("base64");
                            }
                            if (chunkData == null) {
                                Map<String, Object> chunkMap = chunkDoc.getData();
                                if (chunkMap != null) {
                                    for (Map.Entry<String, Object> entry : chunkMap.entrySet()) {
                                        String key = entry.getKey();
                                        if (!key.equals("timestamp") &&
                                                !key.equals("order") &&
                                                !key.equals("index") &&
                                                !key.equals("createdAt") &&
                                                !key.equals("size")) {
                                            if (entry.getValue() instanceof String) {
                                                chunkData = (String) entry.getValue();
                                                break;
                                            }
                                        }
                                    }
                                }
                            }

                            if (chunkData != null && !chunkData.trim().isEmpty()) {
                                chunks.add(chunkData);
                                processed++;

                                // Обновляем прогресс в UI
                                int progress = (int) (((processed) * 100) / totalChunks);
                                mainHandler.post(() ->
                                        callback.onProgress(beatTitle, progress, totalBeats, currentIndex + 1));

                                Log.d(TAG, "📦 Processed chunk " + chunkId + " (" + processed + "/" + totalChunks + ")");
                            }
                        }

                        if (chunks.isEmpty()) {
                            Log.e(TAG, "❌ No valid audio data in any chunks");
                            showNotification("No audio data: " + beatTitle, 0, false);
                            handleDownloadError(beatTitle, "All audio chunks are empty or corrupted",
                                    completedBeats, totalBeats, callback);
                            return;
                        }

                        Log.d(TAG, "✅ Successfully processed " + processed + "/" + totalChunks + " chunks");

                        // ✅ Обновляем уведомление
                        showNotification("Saving: " + beatTitle, 90, false);

                        // Собираем все чанки в одну строку
                        StringBuilder audioData = new StringBuilder();
                        for (String chunk : chunks) {
                            audioData.append(chunk);
                        }

                        // Сохраняем файл в папку Music
                        String filePath = saveAudioFileToMusic(beatTitle, audioData.toString());

                        if (filePath != null) {
                            completedBeats.incrementAndGet();
                            Log.d(TAG, "🎉 Beat downloaded successfully: " + beatTitle);

                            // ✅ Показываем успешное уведомление
                            showDownloadCompleteNotification(beatTitle, filePath);

                            mainHandler.post(() -> {
                                callback.onBeatDownloaded(beatTitle, filePath);

                                if (completedBeats.get() == totalBeats) {
                                    Log.d(TAG, "✅ All Firestore downloads completed successfully");
                                    showAllDownloadsCompleteNotification();
                                    callback.onAllDownloadsCompleted();
                                }
                            });
                        } else {
                            Log.e(TAG, "❌ Failed to save audio file");
                            showNotification("Save failed: " + beatTitle, 0, false);
                            handleDownloadError(beatTitle, "Failed to save audio file",
                                    completedBeats, totalBeats, callback);
                        }

                    } else {
                        Log.e(TAG, "❌ audio_chunks subcollection is empty");
                        showNotification("No audio data: " + beatTitle, 0, false);
                        handleDownloadError(beatTitle, "Audio chunks collection is empty",
                                completedBeats, totalBeats, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error accessing audio_chunks subcollection: " + e.getMessage());
                    showNotification("Download error: " + beatTitle, 0, false);
                    handleDownloadError(beatTitle, "Cannot access audio data: " + e.getMessage(),
                            completedBeats, totalBeats, callback);
                });
    }

    // ✅ Сохранение в папку Music
    private String saveAudioFileToMusic(String beatTitle, String base64Data) {
        try {
            // Создаем папку Music/BeatHouse если её нет
            File musicDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC), "BeatHouse");

            if (!musicDir.exists()) {
                boolean created = musicDir.mkdirs();
                Log.d(TAG, "🎵 Music directory created: " + created + " at " + musicDir.getAbsolutePath());
            }

            // Создаем безопасное имя файла
            String safeFileName = beatTitle.replaceAll("[^a-zA-Z0-9а-яА-Я\\s]", "_");
            String fileName = safeFileName + ".mp3";
            File audioFile = new File(musicDir, fileName);

            // Если файл уже существует, добавляем timestamp
            if (audioFile.exists()) {
                fileName = safeFileName + "_" + System.currentTimeMillis() + ".mp3";
                audioFile = new File(musicDir, fileName);
            }

            Log.d(TAG, "💾 Saving audio to Music: " + audioFile.getAbsolutePath());

            // Декодируем base64 и сохраняем
            byte[] audioBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);

            Log.d(TAG, "🔢 Decoded audio bytes: " + audioBytes.length);

            try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                fos.write(audioBytes);
                fos.flush();
            }

            // Сканируем файл чтобы он появился в медиа-библиотеке
            scanFile(audioFile);

            // Проверяем результат
            if (audioFile.exists() && audioFile.length() > 0) {
                Log.d(TAG, "✅ File saved to Music: " + audioFile.length() + " bytes");
                return audioFile.getAbsolutePath();
            } else {
                Log.e(TAG, "❌ Saved file is empty or missing");
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving audio file to Music: " + e.getMessage(), e);
            return null;
        }
    }

    // ✅ Сканирование файла для медиа-библиотеки
    private void scanFile(File file) {
        try {
            android.media.MediaScannerConnection.scanFile(
                    context,
                    new String[]{file.getAbsolutePath()},
                    new String[]{"audio/mpeg"},
                    (path, uri) -> Log.d(TAG, "🔍 Media scanned: " + path + " -> " + uri)
            );
        } catch (Exception e) {
            Log.e(TAG, "❌ Error scanning file: " + e.getMessage());
        }
    }

    // ✅ Показать уведомление о прогрессе
    private void showNotification(String text, int progress, boolean indeterminate) {
        notificationBuilder
                .setContentTitle("BeatHouse Download")
                .setContentText(text)
                .setProgress(100, progress, indeterminate);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    // ✅ Показать уведомление о завершении загрузки бита
    private void showDownloadCompleteNotification(String beatTitle, String filePath) {
        NotificationCompat.Builder completeBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("✅ " + beatTitle + " Downloaded")
                .setContentText("Tap to open music player")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(currentBeatIndex + 100, completeBuilder.build());
    }

    // ✅ Показать уведомление о завершении всех загрузок
    private void showAllDownloadsCompleteNotification() {
        NotificationCompat.Builder completeBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("🎵 All Beats Downloaded")
                .setContentText(totalBeatsCount + " beats saved to Music/BeatHouse")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, completeBuilder.build());

        // Закрываем прогресс-уведомление
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private String getFirestoreErrorMessage(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
            switch (firestoreException.getCode()) {
                case PERMISSION_DENIED:
                    return "Permission denied - check Firestore security rules";
                case UNAVAILABLE:
                    return "Firestore service unavailable - check internet connection";
                case NOT_FOUND:
                    return "Document not found";
                case CANCELLED:
                    return "Request cancelled";
                case ABORTED:
                    return "Request aborted";
                case ALREADY_EXISTS:
                    return "Document already exists";
                case RESOURCE_EXHAUSTED:
                    return "Resource exhausted - too many requests";
                case INTERNAL:
                    return "Internal Firestore error";
                case UNAUTHENTICATED:
                    return "Authentication required";
                default:
                    return "Firestore error: " + e.getMessage();
            }
        }
        return "Database error: " + e.getMessage();
    }

    private void handleDownloadError(String beatTitle, String error, AtomicInteger completedBeats,
                                     int totalBeats, DownloadCallback callback) {
        Log.e(TAG, "💥 Download error for " + beatTitle + ": " + error);
        completedBeats.incrementAndGet();

        mainHandler.post(() -> {
            callback.onError("Failed to download " + beatTitle + ": " + error);

            if (completedBeats.get() == totalBeats) {
                Log.d(TAG, "📋 All Firestore downloads finished (with errors)");
                showNotification("Download failed", 0, false);
                callback.onAllDownloadsCompleted();
            }
        });
    }
}