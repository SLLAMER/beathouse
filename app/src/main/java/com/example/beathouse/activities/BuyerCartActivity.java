package com.example.beathouse.activities;
import com.example.beathouse.R;
import com.example.beathouse.BaseActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beathouse.adapters.CartAdapter;
import com.example.beathouse.databinding.ActivityBuyerCartBinding;
import com.example.beathouse.models.Beat;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.models.Order;
import com.example.beathouse.utils.CartManager;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuyerCartActivity extends BaseActivity {

    private ActivityBuyerCartBinding binding;
    private CartManager cartManager;
    private FirestoreHelper firestoreHelper;
    private CartAdapter cartAdapter;
    private String currentUserId;
    private String currentUserEmail;
    private FirebaseFirestore db;
    private static final String TAG = "BuyerCartActivity";
    private boolean isCacheLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBuyerCartBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        db = FirebaseFirestore.getInstance();
        firestoreHelper = new FirestoreHelper();

        setupToolbar();
        initComponents();
        setupRecyclerView();
        loadCartData();
        setupCheckoutButton();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.shopping_cart));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initComponents() {
        cartManager = new CartManager(this);
    }

    private void setupRecyclerView() {
        Log.e(TAG, "!!! setupRecyclerView CALLED !!!");
        cartAdapter = new CartAdapter(cartManager.getCartItems(), this, () -> {
            updateCartUI();
        });
        binding.rvCartItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCartItems.setAdapter(cartAdapter);

        Log.e(TAG, "!!! ABOUT TO CALL loadBeatsForCart !!!");
        loadBeatsForCart();
        Log.e(TAG, "!!! loadBeatsForCart CALLED !!!");

        // Ретри через 2 секунды если кэш пуст
        new Handler().postDelayed(() -> {
            if (cartAdapter.getCacheSize() == 0 && !cartManager.getCartItems().isEmpty()) {
                Log.e(TAG, "⚠️ Cache still empty after 2s, retrying...");
                loadBeatsForCart();
            }
        }, 2000);
    }

    private void loadBeatsForCart() {
        Log.e(TAG, "=== loadBeatsForCart START ===");
        Log.e(TAG, "isCacheLoading: " + isCacheLoading);

        if (isCacheLoading) {
            Log.e(TAG, "Cache already loading, skipping");
            return;
        }

        List<CartItem> items = cartManager.getCartItems();
        Log.e(TAG, "Cart items count: " + items.size());

        if (items.isEmpty()) {
            Log.e(TAG, "Cart is empty, skipping cache load");
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            Log.e(TAG, "Item " + i + ": " + item.getBeatTitle() + ", beatId=" + item.getBeatId());
        }

        List<String> beatIds = new ArrayList<>();
        for (CartItem item : items) {
            if (item.getBeatId() != null && !beatIds.contains(item.getBeatId())) {
                beatIds.add(item.getBeatId());
                Log.e(TAG, "Added beatId: " + item.getBeatId());
            }
        }

        Log.e(TAG, "Unique beatIds count: " + beatIds.size());

        if (beatIds.isEmpty()) {
            Log.e(TAG, "No beat IDs found");
            return;
        }

        Log.e(TAG, "🚀🚀🚀 Calling getBeatsByIds with " + beatIds.size() + " IDs 🚀🚀🚀");
        isCacheLoading = true;

        firestoreHelper.getBeatsByIds(beatIds, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                Log.e(TAG, "🎉🎉🎉 getBeatsByIds onSuccess CALLED! 🎉🎉🎉");
                List<Beat> beats = (List<Beat>) result;
                Log.e(TAG, "Beats loaded: " + (beats != null ? beats.size() : 0));

                Map<String, Beat> beatCache = new HashMap<>();
                if (beats != null) {
                    for (Beat beat : beats) {
                        beatCache.put(beat.getId(), beat);
                        Log.e(TAG, "Cached beat: " + beat.getTitle() +
                                " | MP3: $" + beat.getPriceMp3Wav() +
                                " | TrackOut: $" + beat.getPriceTrackOut() +
                                " | Exclusive: $" + beat.getPriceExclusive());
                    }
                }

                if (cartAdapter != null) {
                    cartAdapter.setBeatCache(beatCache);
                    updateCartUI();
                    Log.e(TAG, "Beat cache set with " + beatCache.size() + " beats");
                }
                isCacheLoading = false;
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading beats for cart: " + error);
                isCacheLoading = false;
            }
        });
    }

    private void loadCartData() {
        updateCartUI();
    }

    // ✅ ОБНОВЛЕННЫЙ метод UI
    public void updateCartUI() {
        List<CartItem> cartItems = cartManager.getCartItems();
        int itemCount = cartItems.size();
        double total = cartManager.getCartTotal();

        Log.e(TAG, "Updating UI - Item count: " + itemCount + ", Total: $" + total);

        runOnUiThread(() -> {
            if (itemCount == 0) {
                showEmptyState();
            } else {
                showCartContent();
                binding.tvItemCount.setText(itemCount + " " + getString(R.string.items));
                binding.tvTotalPrice.setText(cartManager.getFormattedTotal());

                if (total == 0) {
                    binding.btnCheckout.setText(getString(R.string.download_free_beats));
                } else {
                    binding.btnCheckout.setText(getString(R.string.proceed_to_checkout) + " - " + cartManager.getFormattedTotal());
                }
            }
        });
    }

    private void showEmptyState() {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvCartItems.setVisibility(View.GONE);
        binding.cartSummary.setVisibility(View.GONE);
    }

    private void showCartContent() {
        binding.emptyState.setVisibility(View.GONE);
        binding.rvCartItems.setVisibility(View.VISIBLE);
        binding.cartSummary.setVisibility(View.VISIBLE);
    }

    private void setupCheckoutButton() {
        binding.btnCheckout.setOnClickListener(v -> {
            if (cartManager.getCartItemCount() > 0) {
                // ✅ Перед переходом обновляем цены из кэша
                if (cartAdapter != null && cartAdapter.getCacheSize() > 0) {
                    cartAdapter.updateAllPricesFromCache();
                    updateCartUI();
                }

                if (cartManager.getCartTotal() == 0) {
                    processFreeOrder();
                } else {
                    processPaidOrder();
                }
            } else {
                Toast.makeText(this, getString(R.string.cart_empty), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processFreeOrder() {
        List<CartItem> cartItems = new ArrayList<>(cartManager.getCartItems());
        createOrdersForAllProducers(cartItems, true);
    }

    private void processPaidOrder() {
        Intent intent = new Intent(this, CheckoutActivity.class);
        intent.putExtra("total_amount", cartManager.getCartTotal());
        intent.putExtra("item_count", cartManager.getCartItemCount());
        Gson gson = new Gson();
        String itemsJson = gson.toJson(cartManager.getCartItems());
        intent.putExtra("cart_items", itemsJson);
        startActivity(intent);
    }

    private void createOrdersForAllProducers(List<CartItem> cartItems, boolean isFree) {
        binding.btnCheckout.setEnabled(false);
        binding.btnCheckout.setText(getString(R.string.processing));

        Log.d(TAG, "📝 Creating orders for " + cartItems.size() + " items");
        Log.d(TAG, "  CurrentUserId: " + currentUserId);
        Log.d(TAG, "  CurrentUserEmail: " + currentUserEmail);

        List<String> beatIds = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.getBeatId() != null) {
                beatIds.add(item.getBeatId());
            }
        }

        firestoreHelper.getBeatsByIds(beatIds, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                List<Beat> beats = (List<Beat>) result;

                Map<String, Beat> beatMap = new HashMap<>();
                for (Beat beat : beats) {
                    beatMap.put(beat.getId(), beat);
                }

                Map<String, List<CartItem>> producerOrders = new HashMap<>();
                for (CartItem item : cartItems) {
                    Beat beat = beatMap.get(item.getBeatId());
                    String producerId = beat != null ? beat.getProducerId() : "unknown";

                    if (!producerOrders.containsKey(producerId)) {
                        producerOrders.put(producerId, new ArrayList<>());
                    }
                    producerOrders.get(producerId).add(item);
                }

                final int[] completedOrders = {0};
                final int totalProducers = producerOrders.size();
                final List<CartItem> allOrderItems = new ArrayList<>();

                for (Map.Entry<String, List<CartItem>> entry : producerOrders.entrySet()) {
                    String producerId = entry.getKey();
                    List<CartItem> producerItems = entry.getValue();

                    Order order = new Order();
                    order.setId("BH_" + (isFree ? "FREE_" : "PAID_") +
                            System.currentTimeMillis() + "_" +
                            UUID.randomUUID().toString().substring(0, 8));
                    order.setUserId(currentUserId);
                    order.setBuyerId(currentUserId);
                    order.setUserEmail(currentUserEmail);
                    order.setProducerId(producerId);
                    order.setItems(new ArrayList<>(producerItems));
                    order.setTotal(isFree ? 0 : calculateTotal(producerItems));
                    order.setStatus(isFree ? "completed" : "pending");
                    order.setCreatedAt(System.currentTimeMillis());

                    if (isFree) {
                        order.setPaidAt(System.currentTimeMillis());
                        order.setTransactionId("FREE_" + System.currentTimeMillis());
                    }

                    Log.d(TAG, "  Creating order for producer: " + producerId);
                    Log.d(TAG, "    Order ID: " + order.getId());
                    Log.d(TAG, "    BuyerId: " + currentUserId);
                    Log.d(TAG, "    Total: $" + order.getTotal());

                    firestoreHelper.createOrder(order, new FirestoreHelper.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            synchronized (completedOrders) {
                                completedOrders[0]++;
                                allOrderItems.addAll(producerItems);
                                Log.d(TAG, "✅ Order created successfully: " + order.getId());

                                // Отправляем уведомления
                                for (CartItem item : producerItems) {
                                    firestoreHelper.sendSaleNotification(producerId, item.getBeatTitle(),
                                            item.isFree() ? 0 : item.getPrice());
                                    firestoreHelper.sendPurchaseNotification(currentUserId, item.getBeatTitle());
                                }

                                // Обновляем статистику
                                updateProducerStats(producerId, order.getTotal(), producerItems.size());
                                updateBuyerStats(order.getTotal(), producerItems.size());

                                if (completedOrders[0] >= totalProducers) {
                                    runOnUiThread(() -> {
                                        cartManager.clearCart();
                                        navigateToOrderComplete(allOrderItems);
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
                                        if (!allOrderItems.isEmpty()) {
                                            navigateToOrderComplete(allOrderItems);
                                        } else {
                                            Toast.makeText(BuyerCartActivity.this,
                                                    getString(R.string.failed_to_create_orders),
                                                    Toast.LENGTH_LONG).show();
                                            binding.btnCheckout.setEnabled(true);
                                            binding.btnCheckout.setText(getString(R.string.retry));
                                        }
                                    });
                                }
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.btnCheckout.setEnabled(true);
                    binding.btnCheckout.setText(getString(R.string.retry));
                    Toast.makeText(BuyerCartActivity.this,
                            getString(R.string.error_loading_beat_info) + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private double calculateTotal(List<CartItem> items) {
        double total = 0;
        for (CartItem item : items) {
            if (!item.isFree()) {
                total += item.getPrice();
            }
        }
        return total;
    }

    private void updateProducerStats(String producerId, double amount, int beatCount) {
        if (producerId == null || producerId.equals("unknown")) return;

        db.collection("producers").document(producerId)
                .update(
                        "totalSales", FieldValue.increment(1),
                        "totalRevenue", FieldValue.increment(amount)
                );

        db.collection("users").document(producerId)
                .update(
                        "stats.totalEarned", FieldValue.increment(amount),
                        "stats.beatsSold", FieldValue.increment(1)
                );
    }

    private void updateBuyerStats(double amount, int beatCount) {
        db.collection("users").document(currentUserId)
                .update(
                        "stats.totalSpent", FieldValue.increment(amount),
                        "stats.beatsPurchased", FieldValue.increment(beatCount)
                );
    }

    private void navigateToOrderComplete(List<CartItem> orderItems) {
        Intent intent = new Intent(this, BuyerOrderCompleteActivity.class);
        intent.putExtra("total", 0.0);
        intent.putExtra("item_count", orderItems.size());

        Gson gson = new Gson();
        String itemsJson = gson.toJson(orderItems);
        intent.putExtra("order_items", itemsJson);

        startActivity(intent);
        finish();
    }

    public void onBrowseBeatsClick(View view) {
        finish();
    }

    // ✅ ОБНОВЛЕННЫЙ onResume
    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume called");
        loadCartData();
        if (cartAdapter != null) {
            // ✅ Обновляем данные адаптера напрямую, без вызова updateCartUI
            cartAdapter.updateCartItems(cartManager.getCartItems());
            if (cartAdapter.getCacheSize() == 0 && !cartManager.getCartItems().isEmpty()) {
                isCacheLoading = false;
                loadBeatsForCart();
            } else if (cartAdapter.getCacheSize() > 0) {
                cartAdapter.updateAllPricesFromCache();
                // ✅ Обновляем UI после изменения цен
                updateCartUI();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}