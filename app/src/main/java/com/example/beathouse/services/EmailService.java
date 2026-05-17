package com.example.beathouse.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import com.example.beathouse.models.Order;
import com.example.beathouse.models.CartItem;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EmailService {
    private static final String TAG = "EmailService";
    private static final String ORDER_PREFS = "order_preferences";
    private static final String LAST_ORDER_KEY = "last_order_receipt";

    public interface EmailCallback {
        void onSuccess();
        void onError(String error);
    }

    public static void sendOrderConfirmation(Context context, Order order, EmailCallback callback) {
        Log.d(TAG, "🚀 Starting REAL email sending process for order: " + order.getId());

        try {
            // Создаем Intent для отправки email через почтовое приложение
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + order.getUserEmail()));

            // Тема письма
            String subject = order.getTotal() == 0 ?
                    "🎵 BeatHouse - Free Beats Download Confirmation" :
                    "🎵 BeatHouse - Order Confirmation";

            // Тело письма
            String emailBody = generateEmailContent(order);

            // Устанавливаем тему и тело письма
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, emailBody);

            // Сохраняем квитанцию локально
            saveOrderReceiptLocally(context, order, emailBody);

            // Запускаем почтовое приложение
            context.startActivity(Intent.createChooser(emailIntent, "Send email via..."));

            Log.d(TAG, "✅ Email app launched successfully!");
            callback.onSuccess();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error launching email app: " + e.getMessage(), e);

            // Fallback: сохраняем квитанцию и показываем уведомление
            saveOrderReceiptLocally(context, order, generateEmailContent(order));
            callback.onError("Email app not found. Receipt saved locally.");
        }
    }

    // ✅ НОВЫЙ МЕТОД: Отправка через Gmail API (будущая реализация)
    public static void sendOrderConfirmationViaAPI(Context context, Order order, EmailCallback callback) {
        Log.d(TAG, "📧 Sending order confirmation via API for: " + order.getId());

        // TODO: Реализовать отправку через Gmail API или собственный бэкенд
        // Пока используем симуляцию

        new Thread(() -> {
            try {
                Thread.sleep(2000); // Симуляция отправки

                String emailContent = generateEmailContent(order);
                saveOrderReceiptLocally(context, order, emailContent);

                Log.d(TAG, "✅ Email sent via API simulation");
                callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "❌ API email sending error: " + e.getMessage());
                callback.onError("Email service temporarily unavailable");
            }
        }).start();
    }

    private static String generateEmailContent(Order order) {
        if (order.getTotal() == 0) {
            return generateFreeOrderEmailContent(order);
        } else {
            return generatePaidOrderEmailContent(order);
        }
    }

    private static String generateFreeOrderEmailContent(Order order) {
        StringBuilder sb = new StringBuilder();

        sb.append("🎵 BeatHouse - Free Beats Download Confirmation\n");
        sb.append("===============================================\n\n");

        sb.append("Thank you for downloading free beats from BeatHouse!\n\n");

        sb.append("Order Details:\n");
        sb.append("--------------\n");
        sb.append("Order ID: ").append(order.getId()).append("\n");
        sb.append("Transaction ID: ").append(order.getTransactionId()).append("\n");
        sb.append("Date: ").append(formatTimestamp(order.getPaidAt())).append("\n");
        sb.append("Email: ").append(order.getUserEmail()).append("\n");
        sb.append("Status: COMPLETED\n\n");

        sb.append("🎧 Free Beats Downloaded:\n");
        sb.append("-------------------------\n");

        int beatNumber = 1;
        for (CartItem item : order.getItems()) {
            sb.append(beatNumber).append(". ").append(item.getBeatTitle()).append("\n");
            sb.append("   🎹 Producer: ").append(item.getProducerName()).append("\n");
            sb.append("   💵 Price: FREE\n");
            sb.append("   🔗 Download Link: ").append(generateDownloadLink(order, item)).append("\n\n");
            beatNumber++;
        }

        sb.append("📥 Download Instructions:\n");
        sb.append("-------------------------\n");
        sb.append("1. Open BeatHouse app\n");
        sb.append("2. Go to 'My Beats' section\n");
        sb.append("3. Find your downloaded beats\n");
        sb.append("4. Click download to save offline\n\n");

        sb.append("❓ Need Help?\n");
        sb.append("-------------\n");
        sb.append("Contact support: support@beathouse.com\n\n");

        sb.append("Keep creating amazing music! 🎶\n\n");
        sb.append("Best regards,\n");
        sb.append("The BeatHouse Team 🎵\n");

        return sb.toString();
    }

    private static String generatePaidOrderEmailContent(Order order) {
        StringBuilder sb = new StringBuilder();

        sb.append("🎵 BeatHouse - Order Confirmation\n");
        sb.append("================================\n\n");

        sb.append("Thank you for your purchase! Your beats are ready for download.\n\n");

        sb.append("💰 Payment Details:\n");
        sb.append("------------------\n");
        sb.append("Order ID: ").append(order.getId()).append("\n");
        sb.append("Transaction ID: ").append(order.getTransactionId()).append("\n");
        sb.append("Date: ").append(formatTimestamp(order.getPaidAt())).append("\n");
        sb.append("Email: ").append(order.getUserEmail()).append("\n");
        sb.append("Payment Status: PAID\n");
        sb.append("Amount: ").append(order.getFormattedTotal()).append("\n\n");

        sb.append("🎧 Beats Purchased:\n");
        sb.append("------------------\n");

        int beatNumber = 1;
        for (CartItem item : order.getItems()) {
            sb.append(beatNumber).append(". ").append(item.getBeatTitle()).append("\n");
            sb.append("   🎹 Producer: ").append(item.getProducerName()).append("\n");
            sb.append("   💵 Price: ").append(item.isFree() ? "FREE" : String.format("$%.2f", item.getPrice())).append("\n");
            sb.append("   🔗 Download: ").append(generateDownloadLink(order, item)).append("\n\n");
            beatNumber++;
        }

        sb.append("📊 Order Summary:\n");
        sb.append("----------------\n");
        sb.append("Total Items: ").append(order.getItemCount()).append("\n");
        sb.append("Total Amount: ").append(order.getFormattedTotal()).append("\n\n");

        sb.append("📥 Download Instructions:\n");
        sb.append("-------------------------\n");
        sb.append("1. Login to your BeatHouse account\n");
        sb.append("2. Visit 'My Orders' section\n");
        sb.append("3. Click 'Download' for each beat\n");
        sb.append("4. Files are available in MP3 & WAV formats\n\n");

        sb.append("Thank you for supporting independent music producers! 🎶\n\n");
        sb.append("Best regards,\n");
        sb.append("The BeatHouse Team 🎵\n");

        return sb.toString();
    }

    private static void saveOrderReceiptLocally(Context context, Order order, String emailContent) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString(LAST_ORDER_KEY, emailContent);
            editor.putString("receipt_" + order.getId(), emailContent);
            editor.putString("last_order_id", order.getId());
            editor.putString("last_order_date", String.valueOf(order.getPaidAt()));
            editor.putFloat("last_order_total", (float) order.getTotal());

            editor.apply();

            Log.d(TAG, "💾 Receipt saved locally for order: " + order.getId());

            // Показываем уведомление о сохранении
            android.widget.Toast.makeText(context,
                    "📧 Receipt saved! Check your email app",
                    android.widget.Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving receipt: " + e.getMessage(), e);
        }
    }

    // ✅ НОВЫЙ МЕТОД: Показать сохраненный чек
    public static void showSavedReceipt(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE);
            String lastReceipt = prefs.getString(LAST_ORDER_KEY, "No recent orders found");

            // Показываем диалог с чеком
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Last Order Receipt")
                    .setMessage(lastReceipt)
                    .setPositiveButton("OK", null)
                    .show();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error showing receipt: " + e.getMessage());
        }
    }

    private static String formatTimestamp(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            Log.e(TAG, "❌ Error formatting timestamp: " + e.getMessage());
            return "Unknown date";
        }
    }

    private static String generateDownloadLink(Order order, CartItem item) {
        // В реальном приложении это были бы настоящие ссылки на CDN
        // Для демо возвращаем инструкции по скачиванию из приложения
        return "Open BeatHouse app → My Beats → Download '" + item.getBeatTitle() + "'";
    }

    // ✅ НОВЫЙ МЕТОД: Скачать бит локально (демо)
    public static void downloadBeatLocally(Context context, CartItem item) {
        try {
            Log.d(TAG, "📥 Downloading beat: " + item.getBeatTitle());

            // Симуляция скачивания
            android.widget.Toast.makeText(context,
                    "Downloading: " + item.getBeatTitle(),
                    android.widget.Toast.LENGTH_SHORT).show();

            // TODO: Реальная загрузка файла
            // 1. Скачать файл с сервера
            // 2. Сохранить в локальное хранилище
            // 3. Добавить в список "My Beats"

        } catch (Exception e) {
            Log.e(TAG, "❌ Error downloading beat: " + e.getMessage());
        }
    }
}