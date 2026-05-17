package com.example.beathouse;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.adapters.CartAdapter;
import com.example.beathouse.models.Beat;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.models.Order;
import com.example.beathouse.utils.CartManager;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CheckoutActivity extends BaseActivity {

    private RecyclerView rvOrderItems;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbCard, rbSbp, rbQr;
    private MaterialButton btnCompletePayment;
    private TextView tvOrderTotal, tvTotalItems;
    private CartManager cartManager;
    private double totalAmount;
    private List<CartItem> cartItems;
    private FirestoreHelper firestoreHelper;
    private FirebaseFirestore db;
    private String currentUserId;
    private String currentUserEmail;
    private Map<String, String> beatProducerMap = new HashMap<>();
    private Map<String, String> beatTitleMap = new HashMap<>();
    private boolean producerIdsLoaded = false;
    private static final String TAG = "CheckoutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        firestoreHelper = new FirestoreHelper();
        db = FirebaseFirestore.getInstance();

        setupToolbar();
        initViews();
        setupRecyclerView();
        setupPaymentMethods();
        loadProducerIds();

        Log.e("CHECKOUT_DEBUG", "=== onCreate COMPLETE ===");
        Log.e("CHECKOUT_DEBUG", "totalAmount = " + totalAmount);
        Log.e("CHECKOUT_DEBUG", "cartItems size = " + (cartItems != null ? cartItems.size() : 0));
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.checkout));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        rvOrderItems = findViewById(R.id.rv_order_items);
        rgPaymentMethod = findViewById(R.id.rg_payment_method);
        rbCard = findViewById(R.id.rb_card);
        rbSbp = findViewById(R.id.rb_sbp);
        rbQr = findViewById(R.id.rb_qr);
        btnCompletePayment = findViewById(R.id.btn_complete_payment);
        tvOrderTotal = findViewById(R.id.tv_order_total);
        tvTotalItems = findViewById(R.id.tv_total_items);

        Log.e("CHECKOUT_DEBUG", "=== initViews ===");
        Log.e("CHECKOUT_DEBUG", "rgPaymentMethod = " + (rgPaymentMethod != null ? "found" : "NOT found"));
        Log.e("CHECKOUT_DEBUG", "rbCard = " + (rbCard != null ? "found" : "NOT found"));
        Log.e("CHECKOUT_DEBUG", "rbSbp = " + (rbSbp != null ? "found" : "NOT found"));
        Log.e("CHECKOUT_DEBUG", "rbQr = " + (rbQr != null ? "found" : "NOT found"));
        Log.e("CHECKOUT_DEBUG", "btnCompletePayment = " + (btnCompletePayment != null ? "found" : "NOT found"));

        cartManager = new CartManager(this);
        totalAmount = getIntent().getDoubleExtra("total_amount", 0.0);

        String itemsJson = getIntent().getStringExtra("cart_items");
        if (itemsJson != null) {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<CartItem>>() {}.getType();
            cartItems = gson.fromJson(itemsJson, listType);
        } else {
            cartItems = new ArrayList<>();
        }

        tvOrderTotal.setText(String.format("$%.0f", totalAmount));

        if (cartItems != null) {
            int freeCount = 0, paidCount = 0;
            for (CartItem item : cartItems) {
                if (item.isFree()) freeCount++;
                else paidCount++;
            }
            tvTotalItems.setText(cartItems.size() + " " + getString(R.string.beats_nav) + " (" + freeCount +
                    " " + getString(R.string.free) + " + " + paidCount + " " + getString(R.string.paid) + ")");
        }

        btnCompletePayment.setOnClickListener(v -> processPayment());
    }

    private void setupRecyclerView() {
        // ✅ Создаем адаптер БЕЗ Spinner (false)
        CartAdapter adapter = new CartAdapter(cartItems, this, null, false);
        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
        rvOrderItems.setAdapter(adapter);
    }

    private void setupPaymentMethods() {
        rgPaymentMethod.clearCheck();
        // Убираем показ QR контейнера - теперь будет отдельная Activity
    }

    private void loadProducerIds() {
        if (cartItems == null || cartItems.isEmpty()) {
            producerIdsLoaded = true;
            return;
        }

        List<String> beatIds = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.getBeatId() != null) beatIds.add(item.getBeatId());
        }

        if (beatIds.isEmpty()) {
            producerIdsLoaded = true;
            return;
        }

        Log.d(TAG, "🔄 Loading producer IDs for " + beatIds.size() + " beats");

        firestoreHelper.getBeatsByIds(beatIds, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object r) {
                List<Beat> beats = (List<Beat>) r;
                for (Beat beat : beats) {
                    beatProducerMap.put(beat.getId(), beat.getProducerId());
                    beatTitleMap.put(beat.getId(), beat.getTitle());
                    Log.d(TAG, "✅ Mapped beat: " + beat.getTitle() + " → producer: " + beat.getProducerId());
                }
                producerIdsLoaded = true;
                Log.d(TAG, "✅ Producer IDs loaded: " + beatProducerMap.size() + "/" + beatIds.size());
            }

            @Override
            public void onError(String e) {
                Log.e(TAG, "❌ Error loading producer IDs: " + e);
                producerIdsLoaded = true;
            }
        });
    }

    private void processPayment() {
        Log.e("CHECKOUT_DEBUG", "=== processPayment CALLED ===");

        int checkedId = rgPaymentMethod.getCheckedRadioButtonId();
        Log.e("CHECKOUT_DEBUG", "checkedId = " + checkedId);

        if (checkedId == -1) {
            Log.e("CHECKOUT_DEBUG", "NO PAYMENT METHOD SELECTED");
            Toast.makeText(this, getString(R.string.select_payment_method), Toast.LENGTH_LONG).show();
            return;
        }

        Log.e("CHECKOUT_DEBUG", "Selected payment method ID: " + checkedId);

        if (!producerIdsLoaded) {
            Log.d(TAG, "⏳ Producer IDs not loaded yet, waiting...");
            Toast.makeText(this, getString(R.string.loading_order_data), Toast.LENGTH_SHORT).show();

            new android.os.Handler().postDelayed(() -> {
                if (producerIdsLoaded) {
                    processPayment();
                } else {
                    Toast.makeText(CheckoutActivity.this,
                            getString(R.string.still_loading), Toast.LENGTH_SHORT).show();
                    new android.os.Handler().postDelayed(() -> {
                        if (producerIdsLoaded) {
                            processPayment();
                        } else {
                            Toast.makeText(CheckoutActivity.this,
                                    getString(R.string.error_loading_data), Toast.LENGTH_LONG).show();
                            btnCompletePayment.setEnabled(true);
                            btnCompletePayment.setText(getString(R.string.complete_payment));
                        }
                    }, 2000);
                }
            }, 1000);
            return;
        }

        if (checkedId == R.id.rb_card) {
            Log.e("CHECKOUT_DEBUG", "=== NAVIGATING TO CardPaymentActivity ===");
            Intent intent = new Intent(this, CardPaymentActivity.class);
            intent.putExtra("total_amount", totalAmount);
            startActivity(intent);
            finish();
        } else if (checkedId == R.id.rb_sbp) {
            Log.e("CHECKOUT_DEBUG", "=== NAVIGATING TO SbpBanksActivity ===");
            Intent intent = new Intent(this, SbpBanksActivity.class);
            intent.putExtra("total_amount", totalAmount);
            startActivity(intent);
            finish();
        } else if (checkedId == R.id.rb_qr) {
            Log.e("CHECKOUT_DEBUG", "=== NAVIGATING TO QrPaymentActivity ===");
            // ✅ Переходим на отдельную страницу QR-оплаты
            Intent intent = new Intent(this, QrPaymentActivity.class);
            intent.putExtra("total_amount", totalAmount);
            intent.putExtra("order_id", "BH_QR_" + System.currentTimeMillis());

            Gson gson = new Gson();
            String itemsJson = gson.toJson(cartItems);
            intent.putExtra("cart_items", itemsJson);

            startActivity(intent);
            finish();
        } else {
            Log.e("CHECKOUT_DEBUG", "UNKNOWN checkedId: " + checkedId);
        }
    }

    // ⚠️ Методы для создания заказов при успешной оплате
    public void processSuccessfulPayment() {
        Map<String, List<CartItem>> producerOrders = new HashMap<>();

        for (CartItem item : cartItems) {
            String producerId = beatProducerMap.get(item.getBeatId());
            if (producerId == null || producerId.isEmpty()) {
                producerId = "unknown";
                Log.w(TAG, "⚠️ No producer ID for beat: " + item.getBeatTitle());
            }

            if (!producerOrders.containsKey(producerId)) {
                producerOrders.put(producerId, new ArrayList<>());
            }
            producerOrders.get(producerId).add(item);
        }

        Log.d(TAG, "📦 Grouped orders by " + producerOrders.size() + " producers");

        if (producerOrders.isEmpty()) {
            cartManager.clearCart();
            Toast.makeText(this, getString(R.string.no_orders_to_process), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final int[] completedOrders = {0};
        final int totalProducers = producerOrders.size();
        final List<Order> createdOrders = new ArrayList<>();
        final List<CartItem> allItems = new ArrayList<>();

        for (Map.Entry<String, List<CartItem>> entry : producerOrders.entrySet()) {
            String producerId = entry.getKey();
            List<CartItem> producerItems = entry.getValue();

            double producerTotal = calculateProducerTotal(producerItems);

            Log.d(TAG, "📝 Creating order for producer: " + producerId +
                    " with " + producerItems.size() + " items, total: $" + producerTotal);

            Order order = new Order();
            order.setId("BH_PAID_" + System.currentTimeMillis() + "_" +
                    UUID.randomUUID().toString().substring(0, 8));
            order.setUserId(currentUserId);
            order.setBuyerId(currentUserId);
            order.setUserEmail(currentUserEmail);
            order.setProducerId(producerId);
            order.setItems(new ArrayList<>(producerItems));
            order.setTotal(producerTotal);
            order.setStatus("completed");
            order.setCreatedAt(System.currentTimeMillis());
            order.setPaidAt(System.currentTimeMillis());
            order.setTransactionId("PAID_" + System.currentTimeMillis());

            firestoreHelper.createOrder(order, new FirestoreHelper.FirestoreCallback() {
                @Override
                public void onSuccess(Object result) {
                    synchronized (completedOrders) {
                        completedOrders[0]++;
                        createdOrders.add(order);
                        allItems.addAll(producerItems);

                        Log.d(TAG, "✅ Order created successfully: " + order.getId());

                        sendNotificationToProducer(producerId, producerItems, order.getTotal());
                        updateProducerStats(producerId, order.getTotal(), producerItems.size());
                        updateBuyerStats(order.getTotal(), producerItems.size());

                        if (completedOrders[0] >= totalProducers) {
                            Log.d(TAG, "🎉 All orders completed: " + completedOrders[0]);
                            runOnUiThread(() -> {
                                cartManager.clearCart();
                                navigateToOrderComplete(createdOrders, allItems);
                            });
                        }
                    }
                }

                @Override
                public void onError(String error) {
                    synchronized (completedOrders) {
                        completedOrders[0]++;
                        Log.e(TAG, "❌ Error creating order: " + error);

                        if (completedOrders[0] >= totalProducers) {
                            runOnUiThread(() -> {
                                cartManager.clearCart();
                                if (!allItems.isEmpty()) {
                                    navigateToOrderComplete(createdOrders, allItems);
                                } else {
                                    Toast.makeText(CheckoutActivity.this,
                                            getString(R.string.payment_failed), Toast.LENGTH_LONG).show();
                                    finish();
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    private double calculateProducerTotal(List<CartItem> items) {
        double total = 0;
        for (CartItem item : items) {
            if (!item.isFree()) total += item.getPrice();
        }
        return total;
    }

    private void sendNotificationToProducer(String producerId, List<CartItem> items, double amount) {
        if (producerId == null || producerId.equals("unknown")) return;

        StringBuilder beatNames = new StringBuilder();
        for (int i = 0; i < Math.min(items.size(), 3); i++) {
            if (i > 0) beatNames.append(", ");
            beatNames.append(items.get(i).getBeatTitle());
        }
        if (items.size() > 3) {
            beatNames.append(" ").append(getString(R.string.and)).append(" ")
                    .append(items.size() - 3).append(" ").append(getString(R.string.more));
        }

        String title = getString(R.string.new_sale);
        String message = getString(R.string.you_sold) + ": " + beatNames.toString() +
                " " + getString(R.string.for_amount) + " $" + String.format("%.0f", amount);

        firestoreHelper.sendNotification(producerId, "sale", title, message, null, null,
                new FirestoreHelper.FirestoreCallback() {
                    @Override
                    public void onSuccess(Object result) {
                        Log.d(TAG, "✅ Sale notification sent to: " + producerId);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Failed to send sale notification: " + error);
                    }
                });

        String buyerTitle = getString(R.string.purchase_complete);
        String buyerMessage = getString(R.string.you_purchased) + ": " + beatNames.toString() +
                " " + getString(R.string.for_amount) + " $" + String.format("%.0f", amount);

        firestoreHelper.sendNotification(currentUserId, "purchase", buyerTitle, buyerMessage, producerId, null,
                new FirestoreHelper.FirestoreCallback() {
                    @Override
                    public void onSuccess(Object result) {
                        Log.d(TAG, "✅ Purchase notification sent to buyer: " + currentUserId);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Failed to send purchase notification: " + error);
                    }
                });
    }

    private void updateProducerStats(String producerId, double amount, int beatCount) {
        if (producerId == null || producerId.equals("unknown")) return;

        Log.d(TAG, "📊 Updating stats for producer: " + producerId +
                " amount: $" + amount + " beats: " + beatCount);

        db.collection("producers").document(producerId)
                .update(
                        "totalSales", FieldValue.increment(1),
                        "totalRevenue", FieldValue.increment(amount)
                )
                .addOnSuccessListener(a -> Log.d(TAG, "✅ Producer stats updated"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update producer stats: " + e.getMessage()));

        db.collection("users").document(producerId)
                .update(
                        "stats.totalEarned", FieldValue.increment(amount),
                        "stats.beatsSold", FieldValue.increment(1)
                )
                .addOnSuccessListener(a -> Log.d(TAG, "✅ User stats updated"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update user stats: " + e.getMessage()));
    }

    private void updateBuyerStats(double amount, int beatCount) {
        Log.d(TAG, "📊 Updating stats for buyer: " + currentUserId);

        db.collection("users").document(currentUserId)
                .update(
                        "stats.totalSpent", FieldValue.increment(amount),
                        "stats.beatsPurchased", FieldValue.increment(beatCount)
                )
                .addOnSuccessListener(a -> Log.d(TAG, "✅ Buyer stats updated"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update buyer stats: " + e.getMessage()));
    }

    private void navigateToOrderComplete(List<Order> orders, List<CartItem> allItems) {
        if (orders.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_orders_created), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Order firstOrder = orders.get(0);

        Intent intent = new Intent(this, BuyerOrderCompleteActivity.class);
        intent.putExtra("order_id", firstOrder.getId());
        intent.putExtra("transaction_id", firstOrder.getTransactionId());
        intent.putExtra("total", firstOrder.getTotal());
        intent.putExtra("item_count", allItems.size());
        intent.putExtra("total_formatted", firstOrder.getFormattedTotal());

        Gson gson = new Gson();
        intent.putExtra("order_items", gson.toJson(allItems));

        startActivity(intent);
        finish();
    }
}