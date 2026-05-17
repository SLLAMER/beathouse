package com.example.beathouse.utils;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioUtils {
    private static final String TAG = "AudioUtils";
    private static final int MAX_CHUNK_SIZE = 700000; // ~700KB chunks (Firestore limit ~1MB)

    /**
     * Разбивает большой Base64 на чанки для Firestore
     */
    public static List<String> splitAudioIntoChunks(String fullBase64Audio) {
        List<String> chunks = new ArrayList<>();

        if (fullBase64Audio == null || fullBase64Audio.isEmpty()) {
            Log.d(TAG, "Empty audio data provided");
            return chunks;
        }

        int length = fullBase64Audio.length();
        Log.d(TAG, "Splitting audio of length: " + length + " characters");

        for (int i = 0; i < length; i += MAX_CHUNK_SIZE) {
            int end = Math.min(length, i + MAX_CHUNK_SIZE);
            String chunk = fullBase64Audio.substring(i, end);
            chunks.add(chunk);
        }

        Log.d(TAG, "Audio split into " + chunks.size() + " chunks");
        return chunks;
    }

    /**
     * Собирает чанки обратно в полный Base64
     */
    public static String combineChunks(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            Log.d(TAG, "No chunks to combine");
            return null;
        }

        StringBuilder fullAudio = new StringBuilder();
        for (String chunk : chunks) {
            fullAudio.append(chunk);
        }

        String result = fullAudio.toString();
        Log.d(TAG, "Combined " + chunks.size() + " chunks into audio of length: " + result.length());
        return result;
    }

    /**
     * Декодирует Base64 в временный файл для воспроизведения
     */
    public static File decodeBase64ToFile(Context context, String base64Audio, String fileName) throws IOException {
        if (base64Audio == null || base64Audio.isEmpty()) {
            throw new IOException("Empty base64 audio data");
        }

        Log.d(TAG, "Decoding base64 audio to file: " + fileName);

        byte[] audioBytes;
        try {
            audioBytes = Base64.decode(base64Audio, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid base64 data: " + e.getMessage());
        }

        if (audioBytes == null || audioBytes.length == 0) {
            throw new IOException("Decoded audio bytes are empty");
        }

        File tempFile = new File(context.getCacheDir(), fileName + "_" + System.currentTimeMillis() + ".mp3");
        FileOutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(tempFile);
            outputStream.write(audioBytes);
            outputStream.flush();

            Log.d(TAG, "Audio file created: " + tempFile.getAbsolutePath() + " (" + tempFile.length() + " bytes)");
            return tempFile;

        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing stream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Оценивает размер файла из Base64 строки
     */
    public static long getEstimatedFileSize(String base64) {
        if (base64 == null) return 0;

        // Base64 увеличивает размер на ~33%, так что умножаем на 0.75 для получения реального размера
        long estimatedSize = (long) (base64.length() * 0.75);
        Log.d(TAG, "Estimated file size: " + estimatedSize + " bytes");
        return estimatedSize;
    }

    /**
     * Проверяет, является ли Base64 строкой аудио
     */
    public static boolean isValidAudioBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return false;
        }

        try {
            // Проверяем, можно ли декодировать
            byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
            return decoded != null && decoded.length > 0;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid base64: " + e.getMessage());
            return false;
        }
    }

    /**
     * Очищает временные аудио файлы из кэша
     */
    public static void clearTempAudioFiles(Context context) {
        File cacheDir = context.getCacheDir();
        if (cacheDir == null || !cacheDir.exists()) {
            return;
        }

        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (files != null) {
            int deletedCount = 0;
            for (File file : files) {
                if (file.delete()) {
                    deletedCount++;
                }
            }
            Log.d(TAG, "Cleared " + deletedCount + " temp audio files");
        }
    }

    /**
     * Получает информацию о Base64 аудио
     */
    public static String getAudioInfo(String base64Audio) {
        if (base64Audio == null) return "No audio data";

        long estimatedSize = getEstimatedFileSize(base64Audio);
        int chunkCount = (int) Math.ceil((double) base64Audio.length() / MAX_CHUNK_SIZE);

        return String.format("Size: %.2f MB, Chunks: %d",
                estimatedSize / (1024.0 * 1024.0), chunkCount);
    }
}