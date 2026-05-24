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
    private TextView tvAmount, tvOrderId, tvInstruction;
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
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvAmount = findViewById(R.id.tv_amount);
        tvOrderId = findViewById(R.id.tv_order_id);
        tvInstruction = findViewById(R.id.tv_instruction);
        ivQrCode = findViewById(R.id.iv_qr_code);
        btnIHavePaid = findViewById(R.id.btn_i_have_paid);
        btnCancel = findViewById(R.id.btn_cancel);
        cardQr = findViewById(R.id.card_qr);
        progressBar = findViewById(R.id.progress_bar);

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // Получаем актуальный курс и конвертируем в рубли для отображения
        com.example.beathouse.utils.CurrencyUtils.getUsdToRubRate(rate -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            tvAmount.setText(com.example.beathouse.utils.CurrencyUtils.formatRub(totalAmount, rate));
            // Генерируем QR код только после получения курса, чтобы сумма в нем соответствовала рублям
            generateAndDisplayQrCode(rate);
        });

        tvOrderId.setText(getString(R.string.order_id) + ": " + orderId);

        // Устанавливаем инструкцию из ресурсов
        if (tvInstruction != null) {
            tvInstruction.setText(getString(R.string.qr_instruction));
        }

        btnIHavePaid.setOnClickListener(v -> onIHavePaidClicked());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.qr_payment));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void generateAndDisplayQrCode(double rate) {
        Log.e(TAG, "=== generateAndDisplayQrCode START ===");
        Log.e(TAG, "totalAmount: " + totalAmount);
        Log.e(TAG, "orderId: " + orderId);

        // Пользователь указал "конвертация только на экранах", поэтому данные QR кода оставляем в USD
        String qrData = "beathouse://payment?amount=" + totalAmount +
                "&order_id=" + orderId +
                "&user_id=" + currentUserId;

        Log.e(TAG, "qrData: " + qrData);

        // Показываем прогресс
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (ivQrCode != null) {
            ivQrCode.setVisibility(View.GONE);
        }

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
            ivQrCode.setVisibility(View.VISIBLE);
            if (cardQr != null) {
                cardQr.setVisibility(View.VISIBLE);
            }
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            Log.e(TAG, "✅ QR code generated successfully");

        } catch (WriterException e) {
            Log.e(TAG, "❌ Error generating QR code: " + e.getMessage());
            Toast.makeText(this, R.string.error_generating_qr, Toast.LENGTH_SHORT).show();
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        }
    }

    private void onIHavePaidClicked() {
        btnIHavePaid.setEnabled(false);
        btnIHavePaid.setText(R.string.processing);

        // Показываем диалог подтверждения
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.confirm_payment)
                .setMessage(R.string.confirm_payment_message_qr)
                .setPositiveButton(R.string.yes_paid, (dialog, which) -> {
                    processSuccessPayment();
                })
                .setNegativeButton(R.string.not_yet, (dialog, which) -> {
                    btnIHavePaid.setEnabled(true);
                    btnIHavePaid.setText(R.string.i_have_paid);
                })
                .show();
    }

    private void processSuccessPayment() {
        // Показываем прогресс
        btnIHavePaid.setText(R.string.processing);
        btnIHavePaid.setEnabled(false);

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        new Handler().postDelayed(() -> {
            processOrderCreation();
        }, 1500);
    }

    private void processOrderCreation() {
        if (cartItems == null || cartItems.isEmpty()) {
            finish();
            return;
        }

        String itemsJson = new com.google.gson.Gson().toJson(cartItems);

        new com.example.beathouse.utils.OrderManager(this).processOrderCreation(cartItems, "QR", new com.example.beathouse.utils.OrderManager.OrderCallback() {
            @Override
            public void onAllOrdersCreated(java.util.List<com.example.beathouse.models.Order> createdOrders) {
                Intent intent = new Intent(QrPaymentActivity.this, BuyerOrderCompleteActivity.class);
                intent.putExtra("total", totalAmount);
                if (createdOrders != null && !createdOrders.isEmpty()) {
                    intent.putExtra("order_id", createdOrders.get(0).getId());
                } else {
                    intent.putExtra("order_id", orderId);
                }
                intent.putExtra("item_count", cartItems.size());
                intent.putExtra("order_items", itemsJson);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(QrPaymentActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}