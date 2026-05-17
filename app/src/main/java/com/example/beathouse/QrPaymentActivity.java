package com.example.beathouse;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.utils.CartManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class QrPaymentActivity extends BaseActivity {

    private static final String TAG = "QrPaymentActivity";

    private MaterialToolbar toolbar;
    private TextView tvAmount, tvOrderId;
    private ImageView ivQrCode;
    private MaterialButton btnIHavePaid, btnCancel;
    private FrameLayout cardQr;
    private View progressBar;

    private double totalAmount;
    private String orderId;
    private List<CartItem> cartItems;
    private CartManager cartManager;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_payment);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        cartManager = new CartManager(this);

        totalAmount = getIntent().getDoubleExtra("total_amount", 0.0);
        orderId = getIntent().getStringExtra("order_id");
        if (orderId == null || orderId.isEmpty()) {
            orderId = "ORDER_" + System.currentTimeMillis();
        }

        String itemsJson = getIntent().getStringExtra("cart_items");
        if (itemsJson != null) {
            Type listType = new TypeToken<ArrayList<CartItem>>() {}.getType();
            cartItems = new Gson().fromJson(itemsJson, listType);
        } else {
            cartItems = new ArrayList<>();
        }

        initViews();
        setupToolbar();
        generateAndDisplayQrCode();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvAmount = findViewById(R.id.tv_amount);
        tvOrderId = findViewById(R.id.tv_order_id);
        ivQrCode = findViewById(R.id.iv_qr_code);
        btnIHavePaid = findViewById(R.id.btn_i_have_paid);
        btnCancel = findViewById(R.id.btn_cancel);
        cardQr = findViewById(R.id.card_qr);
        progressBar = findViewById(R.id.progress_bar);

        tvAmount.setText(String.format("$%.0f", totalAmount));
        tvOrderId.setText("Заказ: " + orderId);

        btnIHavePaid.setOnClickListener(v -> onIHavePaidClicked());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Оплата по QR");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void generateAndDisplayQrCode() {
        Log.e(TAG, "=== generateAndDisplayQrCode START ===");
        Log.e(TAG, "totalAmount: " + totalAmount);
        Log.e(TAG, "orderId: " + orderId);

        String qrData = "beathouse://payment?amount=" + totalAmount +
                "&order_id=" + orderId +
                "&user_id=" + currentUserId;

        Log.e(TAG, "qrData: " + qrData);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 800, 800);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ivQrCode.setImageBitmap(bitmap);
            cardQr.setVisibility(View.VISIBLE);
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            Log.e(TAG, "✅ QR code generated successfully");

        } catch (WriterException e) {
            Log.e(TAG, "❌ Error generating QR code: " + e.getMessage());
            Toast.makeText(this, "Ошибка генерации QR-кода", Toast.LENGTH_SHORT).show();
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        }
    }

    private void onIHavePaidClicked() {
        btnIHavePaid.setEnabled(false);
        btnIHavePaid.setText("Обработка...");

        new Handler().postDelayed(() -> {
            cartManager.clearCart();

            Intent intent = new Intent(this, BuyerOrderCompleteActivity.class);
            intent.putExtra("total", totalAmount);
            intent.putExtra("item_count", cartItems != null ? cartItems.size() : 0);
            intent.putExtra("transaction_id", "QR_" + System.currentTimeMillis());
            intent.putExtra("order_id", orderId);

            Gson gson = new Gson();
            intent.putExtra("order_items", gson.toJson(cartItems));

            startActivity(intent);
            finish();
        }, 1000);
    }
}