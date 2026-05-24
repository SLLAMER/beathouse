package com.example.beathouse.activities;
import com.example.beathouse.R;
import com.example.beathouse.BaseActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
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
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartActivity extends BaseActivity {

    private RecyclerView rvCartItems;
    private View emptyState;
    private MaterialCardView cartSummary;
    private TextView tvItemCount, tvTotalPrice;
    private MaterialButton btnCheckout;
    private CartManager cartManager;
    private CartAdapter cartAdapter;
    private FirestoreHelper firestoreHelper;
    private static final String TAG = "CartActivity";
    private boolean isCacheLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        firestoreHelper = new FirestoreHelper();
        setupToolbar();
        initViews();
        setupRecyclerView();
        loadCartData();
        setupCheckoutButton();

        Log.e(TAG, "CartActivity created");
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.shopping_cart));
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        rvCartItems = findViewById(R.id.rv_cart_items);
        emptyState = findViewById(R.id.empty_state);
        cartSummary = findViewById(R.id.cart_summary);
        tvItemCount = findViewById(R.id.tv_item_count);
        tvTotalPrice = findViewById(R.id.tv_total_price);
        btnCheckout = findViewById(R.id.btn_checkout);

        cartManager = new CartManager(this);
        Log.e(TAG, "Views initialized");
    }

    private void setupRecyclerView() {
        Log.e(TAG, "!!! setupRecyclerView CALLED !!!");
        cartAdapter = new CartAdapter(cartManager.getCartItems(), this, () -> updateCartUI());
        rvCartItems.setLayoutManager(new LinearLayoutManager(this));
        rvCartItems.setAdapter(cartAdapter);

        Log.e(TAG, "!!! ABOUT TO CALL loadBeatsForCart !!!");
        loadBeatsForCart();
        Log.e(TAG, "!!! loadBeatsForCart CALLED !!!");

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

    public void updateCartUI() {
        int itemCount = cartManager.getCartItemCount();
        double total = cartManager.getCartTotal();
        Log.e(TAG, "Updating UI - Item count: " + itemCount + ", Total: $" + total);
        runOnUiThread(() -> {
            if (itemCount == 0) {
                emptyState.setVisibility(View.VISIBLE);
                rvCartItems.setVisibility(View.GONE);
                cartSummary.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                rvCartItems.setVisibility(View.VISIBLE);
                cartSummary.setVisibility(View.VISIBLE);
                tvItemCount.setText(String.valueOf(itemCount));
                tvTotalPrice.setText(formatPrice(total));
                if (total == 0) {
                    btnCheckout.setText(getString(R.string.download_free_beats) + " (" + itemCount + ")");
                } else {
                    btnCheckout.setText(getString(R.string.proceed_to_checkout) + " (" + itemCount + " " + getString(R.string.items) + ")");
                }
            }
        });
    }

    private String formatPrice(double price) {
        if (price == (long) price) return "$" + String.format("%d", (long) price);
        return "$" + String.format("%.0f", price);
    }

    private void setupCheckoutButton() {
        btnCheckout.setOnClickListener(v -> {
            int itemCount = cartManager.getCartItemCount();
            if (itemCount > 0) {
                if (cartManager.getCartTotal() == 0) processFreeOrder();
                else processPaidOrder();
            } else {
                Toast.makeText(this, getString(R.string.cart_empty), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processFreeOrder() {
        Toast.makeText(this, getString(R.string.payment_successful), Toast.LENGTH_SHORT).show();
        Order order = createOrderFromCart();
        cartManager.clearCart();
        navigateToOrderComplete(order);
    }

    private void processPaidOrder() {
        if (cartAdapter != null && cartAdapter.getCacheSize() > 0) {
            cartAdapter.updateAllPricesFromCache();
            updateCartUI();
        }
        Intent intent = new Intent(this, CheckoutActivity.class);
        intent.putExtra("total_amount", cartManager.getCartTotal());
        intent.putExtra("item_count", cartManager.getCartItemCount());
        Gson gson = new Gson();
        intent.putExtra("cart_items", gson.toJson(cartManager.getCartItems()));
        startActivity(intent);
    }

    private Order createOrderFromCart() {
        Order order = new Order();
        order.setId("BH_" + System.currentTimeMillis());
        order.setUserId(getCurrentUserId());
        order.setUserEmail(getCurrentUserEmail());
        order.setItems(new ArrayList<>(cartManager.getCartItems()));
        order.setTotal(cartManager.getCartTotal());
        order.setStatus("completed");
        order.setCreatedAt(System.currentTimeMillis());
        order.setPaidAt(System.currentTimeMillis());
        order.setTransactionId("FREE_ORDER_" + System.currentTimeMillis());
        return order;
    }

    private void navigateToOrderComplete(Order order) {
        try {
            Intent intent = new Intent(this, OrderCompleteActivity.class);
            intent.putExtra("order_id", order.getId());
            intent.putExtra("transaction_id", order.getTransactionId());
            intent.putExtra("total", order.getFormattedTotal());
            intent.putExtra("item_count", order.getItemCount());
            intent.putExtra("is_free", order.getTotal() == 0);
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                intent.putExtra("order_items", new Gson().toJson(order.getItems()));
            }
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
            Toast.makeText(this, getString(R.string.order_complete), Toast.LENGTH_LONG).show();
        }
    }

    private String getCurrentUserId() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return "user_" + System.currentTimeMillis();
    }

    private String getCurrentUserEmail() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getEmail();
        }
        return "user@beathouse.com";
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume called");
        updateCartUI();
        if (cartAdapter != null) {
            cartAdapter.updateCartItems(cartManager.getCartItems());
            if (cartAdapter.getCacheSize() == 0 && !cartManager.getCartItems().isEmpty()) {
                isCacheLoading = false;
                loadBeatsForCart();
            } else if (cartAdapter.getCacheSize() > 0) {
                cartAdapter.updateAllPricesFromCache();
                updateCartUI();
            }
        }
    }
}