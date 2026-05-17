// OrderCompleteActivity.java (ПОЛНОСТЬЮ ГОТОВАЯ ВЕРСИЯ)
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
import com.example.beathouse.models.User;
import com.example.beathouse.services.FirebaseDownloadService;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class OrderCompleteActivity extends BaseActivity {

    private TextView tvOrderId, tvTransactionId, tvTotal, tvItemCount, tvSuccessMessage, tvDownloadProgress;
    private MaterialButton btnDownload, btnViewOrders, btnContinue;
    private ProgressBar downloadProgressBar;
    private String orderId;
    private boolean isFree;
    private List<CartItem> orderItems;
    private FirebaseDownloadService downloadService;
    private FirestoreHelper firestoreHelper;
    private boolean isBuyer = false;
    private static final String TAG = "OrderCompleteActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_complete);

        firestoreHelper = new FirestoreHelper();
        checkUserRole();

        setupToolbar();
        initViews();
        setupOrderData();
        setupButtons();
        startConfettiAnimation();

        downloadService = new FirebaseDownloadService(this);
    }

    private void checkUserRole() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestoreHelper.getUser(userId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                User user = (User) result;
                isBuyer = user.isBuyer();
            }
            @Override
            public void onError(String error) {
                isBuyer = true;
            }
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> navigateToHome());
    }

    private void initViews() {
        tvOrderId = findViewById(R.id.tv_order_id);
        tvTransactionId = findViewById(R.id.tv_transaction_id);
        tvTotal = findViewById(R.id.tv_total);
        tvItemCount = findViewById(R.id.tv_item_count);
        tvSuccessMessage = findViewById(R.id.tv_success_message);
        tvDownloadProgress = findViewById(R.id.tv_download_progress);
        downloadProgressBar = findViewById(R.id.download_progress_bar);

        btnDownload = findViewById(R.id.btn_download_beats);
        btnViewOrders = findViewById(R.id.btn_view_orders);
        btnContinue = findViewById(R.id.btn_continue_shopping);

        downloadProgressBar.setVisibility(ProgressBar.GONE);
        tvDownloadProgress.setVisibility(TextView.GONE);
    }

    private void setupOrderData() {
        orderId = getIntent().getStringExtra("order_id");
        String transactionId = getIntent().getStringExtra("transaction_id");
        String total = getIntent().getStringExtra("total");
        int itemCount = getIntent().getIntExtra("item_count", 0);
        isFree = getIntent().getBooleanExtra("is_free", false);

        String itemsJson = getIntent().getStringExtra("order_items");
        if (itemsJson != null && !itemsJson.trim().isEmpty()) {
            try {
                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<CartItem>>(){}.getType();
                orderItems = gson.fromJson(itemsJson, listType);
                if (orderItems == null) orderItems = new ArrayList<>();
            } catch (Exception e) {
                orderItems = new ArrayList<>();
            }
        } else {
            orderItems = new ArrayList<>();
        }

        tvOrderId.setText(orderId != null ? orderId : "BH_" + System.currentTimeMillis());
        tvTransactionId.setText(transactionId != null ? transactionId : "TXN_" + System.currentTimeMillis());
        tvTotal.setText(total != null ? total : "FREE");
        tvItemCount.setText(itemCount + " items");
        tvSuccessMessage.setText(isFree ? "Free Beats Order Completed!" : "Payment Successful!");
    }

    private void setupButtons() {
        btnDownload.setOnClickListener(v -> {
            if (orderItems == null || orderItems.isEmpty()) {
                Toast.makeText(this, "No beats available", Toast.LENGTH_SHORT).show();
                return;
            }
            startDownloadProcess();
        });

        btnViewOrders.setOnClickListener(v -> {
            startActivity(new Intent(this, OrderHistoryActivity.class));
        });

        // ✅ Главная кнопка — редирект по роли
        btnContinue.setOnClickListener(v -> navigateToHome());
    }

    private void startDownloadProcess() {
        btnDownload.setEnabled(false);
        btnDownload.setText("Downloading...");
        downloadProgressBar.setVisibility(ProgressBar.VISIBLE);
        tvDownloadProgress.setVisibility(TextView.VISIBLE);

        downloadService.downloadBeats(orderItems, new FirebaseDownloadService.DownloadCallback() {
            @Override
            public void onProgress(String beatTitle, int progress, int totalBeats, int currentBeat) {
                runOnUiThread(() -> {
                    tvDownloadProgress.setText(String.format("%s (%d/%d) %d%%", beatTitle, currentBeat, totalBeats, progress));
                    downloadProgressBar.setProgress(progress);
                });
            }

            @Override
            public void onBeatDownloaded(String beatTitle, String filePath) {
                runOnUiThread(() -> Toast.makeText(OrderCompleteActivity.this, "✓ " + beatTitle, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAllDownloadsCompleted() {
                runOnUiThread(() -> {
                    btnDownload.setEnabled(true);
                    btnDownload.setText("Download Complete ✓");
                    downloadProgressBar.setVisibility(ProgressBar.GONE);
                    tvDownloadProgress.setText("All beats downloaded!");
                    Toast.makeText(OrderCompleteActivity.this, "All beats downloaded!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnDownload.setEnabled(true);
                    btnDownload.setText("Retry Download");
                    downloadProgressBar.setVisibility(ProgressBar.GONE);
                    tvDownloadProgress.setText("Download failed");
                    Toast.makeText(OrderCompleteActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ✅ Редирект по роли
    private void navigateToHome() {
        Intent intent;
        if (isBuyer) {
            intent = new Intent(this, BuyerMainActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void startConfettiAnimation() {
        new Handler().postDelayed(() -> {
            TextView confetti1 = findViewById(R.id.tv_confetti1);
            if (confetti1 != null) confetti1.animate().scaleX(1.5f).scaleY(1.5f).setDuration(300).start();
        }, 500);
    }

    @Override
    public void onBackPressed() {
        navigateToHome();
    }
}