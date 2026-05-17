// BuyerOrderCompleteActivity.java
package com.example.beathouse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.services.FirebaseDownloadService;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BuyerOrderCompleteActivity extends BaseActivity {

    private TextView tvOrderId, tvTotal, tvItemCount, tvSuccessMessage, tvDownloadProgress;
    private MaterialButton btnDownload, btnViewOrders, btnContinueShopping;
    private ProgressBar downloadProgressBar;
    private String orderId;
    private List<CartItem> orderItems;
    private FirebaseDownloadService downloadService;
    private static final String TAG = "BuyerOrderCompleteActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_order_complete);

        initViews();
        setupOrderData();
        setupButtons();

        downloadService = new FirebaseDownloadService(this);

        Log.d(TAG, "OrderCompleteActivity created");
    }

    private void initViews() {
        tvOrderId = findViewById(R.id.tvOrderId);
        tvTotal = findViewById(R.id.tvTotal);
        tvItemCount = findViewById(R.id.tvItemCount);
        tvSuccessMessage = findViewById(R.id.tvSuccessMessage);
        tvDownloadProgress = findViewById(R.id.tvDownloadProgress);
        downloadProgressBar = findViewById(R.id.download_progress_bar);

        btnDownload = findViewById(R.id.btnDownload);
        btnViewOrders = findViewById(R.id.btnViewOrders);
        btnContinueShopping = findViewById(R.id.btnContinueShopping);

        downloadProgressBar.setVisibility(ProgressBar.GONE);
        tvDownloadProgress.setVisibility(TextView.GONE);
    }

    private void setupOrderData() {
        orderId = getIntent().getStringExtra("order_id");

        // ✅ Получаем double правильно
        double total = getIntent().getDoubleExtra("total", 0.0);
        int itemCount = getIntent().getIntExtra("item_count", 0);

        String itemsJson = getIntent().getStringExtra("order_items");
        if (itemsJson != null && !itemsJson.isEmpty()) {
            try {
                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<CartItem>>(){}.getType();
                orderItems = gson.fromJson(itemsJson, listType);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing items: " + e.getMessage());
                orderItems = new ArrayList<>();
            }
        } else {
            orderItems = new ArrayList<>();
        }

        tvOrderId.setText("Order #" + (orderId != null ? orderId.substring(Math.max(0, orderId.length() - 8)) : ""));

        // ✅ Убираем центы - форматируем без копеек
        if (total == 0) {
            tvTotal.setText("FREE");
        } else {
            tvTotal.setText(String.format("$%.0f", total));
        }

        tvItemCount.setText(itemCount + " items");
        tvSuccessMessage.setText(total == 0 ? "Free Beats Ready!" : "Payment Successful!");

        Log.d(TAG, "Order data set - Total: $" + total + ", Items: " + itemCount);
    }

    private void setupButtons() {
        btnDownload.setOnClickListener(v -> {
            if (orderItems == null || orderItems.isEmpty()) {
                Toast.makeText(this, "No items to download", Toast.LENGTH_SHORT).show();
                return;
            }
            startDownloadProcess();
        });

        btnViewOrders.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrderHistoryActivity.class);
            startActivity(intent);
        });

        btnContinueShopping.setOnClickListener(v -> {
            Intent intent = new Intent(this, BuyerMainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void startDownloadProcess() {
        btnDownload.setEnabled(false);
        btnDownload.setText("Downloading...");
        downloadProgressBar.setVisibility(ProgressBar.VISIBLE);
        downloadProgressBar.setProgress(0);
        tvDownloadProgress.setVisibility(TextView.VISIBLE);
        tvDownloadProgress.setText("Preparing downloads...");

        downloadService.downloadBeats(orderItems, new FirebaseDownloadService.DownloadCallback() {
            @Override
            public void onProgress(String beatTitle, int progress, int totalBeats, int currentBeat) {
                runOnUiThread(() -> {
                    String progressText = String.format("%s (%d/%d) %d%%",
                            beatTitle, currentBeat, totalBeats, progress);
                    tvDownloadProgress.setText(progressText);
                    downloadProgressBar.setProgress(progress);
                });
            }

            @Override
            public void onBeatDownloaded(String beatTitle, String filePath) {
                runOnUiThread(() -> {
                    Toast.makeText(BuyerOrderCompleteActivity.this,
                            "✓ " + beatTitle, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAllDownloadsCompleted() {
                runOnUiThread(() -> {
                    btnDownload.setEnabled(true);
                    btnDownload.setText("Download Complete ✓");
                    downloadProgressBar.setVisibility(ProgressBar.GONE);
                    tvDownloadProgress.setText("All beats downloaded successfully!");
                    Toast.makeText(BuyerOrderCompleteActivity.this,
                            "All beats downloaded!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnDownload.setEnabled(true);
                    btnDownload.setText("Retry Download");
                    downloadProgressBar.setVisibility(ProgressBar.GONE);
                    tvDownloadProgress.setText("Download failed: " + error);
                    Toast.makeText(BuyerOrderCompleteActivity.this,
                            "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, BuyerMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}