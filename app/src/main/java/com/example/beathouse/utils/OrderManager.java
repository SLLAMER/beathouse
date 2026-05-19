package com.example.beathouse.utils;

import android.content.Context;
import android.util.Log;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.models.Order;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderManager {
    private static final String TAG = "OrderManager";
    private final Context context;
    private final FirebaseFirestore db;
    private final FirestoreHelper firestoreHelper;
    private final CartManager cartManager;

    public interface OrderCallback {
        void onAllOrdersCreated(List<Order> createdOrders);
        void onError(String error);
    }

    public OrderManager(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.firestoreHelper = new FirestoreHelper();
        this.cartManager = new CartManager(context);
    }

    public void processOrderCreation(List<CartItem> cartItems, String transactionPrefix, OrderCallback callback) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        if (cartItems == null || cartItems.isEmpty()) {
            if (callback != null) callback.onError("Cart is empty");
            return;
        }

        // Group by producer
        Map<String, List<CartItem>> producerOrders = new HashMap<>();
        for (CartItem item : cartItems) {
            String producerId = item.getProducerId();
            if (producerId == null) producerId = "unknown";
            if (!producerOrders.containsKey(producerId)) {
                producerOrders.put(producerId, new ArrayList<>());
            }
            producerOrders.get(producerId).add(item);
        }

        final int[] completed = {0};
        int totalProducers = producerOrders.size();
        List<Order> createdOrders = new ArrayList<>();

        for (Map.Entry<String, List<CartItem>> entry : producerOrders.entrySet()) {
            String producerId = entry.getKey();
            List<CartItem> items = entry.getValue();
            double total = 0;
            for (CartItem i : items) if (!i.isFree()) total += i.getPrice();

            Order order = new Order();
            order.setId("BH_" + transactionPrefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4));
            order.setBuyerId(currentUserId);
            order.setUserId(currentUserId);
            order.setUserEmail(currentUserEmail);
            order.setProducerId(producerId);
            order.setItems(items);
            order.setTotal(total);
            order.setStatus("completed");
            order.setPaidAt(System.currentTimeMillis());
            order.setTransactionId(transactionPrefix + "_" + System.currentTimeMillis());

            firestoreHelper.createOrder(order, new FirestoreHelper.FirestoreCallback() {
                @Override
                public void onSuccess(Object result) {
                    createdOrders.add(order);
                    updateProducerStats(producerId, order.getTotal(), items.size());
                    sendNotifications(producerId, currentUserId, items, order.getTotal());
                    checkAllDone();
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error creating order for producer " + producerId + ": " + error);
                    checkAllDone();
                }

                private void checkAllDone() {
                    completed[0]++;
                    if (completed[0] >= totalProducers) {
                        cartManager.clearCart();
                        updateBuyerStats(currentUserId, cartItems);
                        if (callback != null) callback.onAllOrdersCreated(createdOrders);
                    }
                }
            });
        }
    }

    private void updateProducerStats(String producerId, double amount, int count) {
        if (producerId == null || producerId.equals("unknown")) return;

        db.collection("producers").document(producerId)
                .update("totalSales", FieldValue.increment(1),
                        "totalRevenue", FieldValue.increment(amount));

        db.collection("users").document(producerId)
                .update("stats.totalEarned", FieldValue.increment(amount),
                        "stats.beatsSold", FieldValue.increment(count));
    }

    private void updateBuyerStats(String userId, List<CartItem> items) {
        double totalSpent = 0;
        for (CartItem i : items) if (!i.isFree()) totalSpent += i.getPrice();

        db.collection("users").document(userId)
                .update("stats.totalSpent", FieldValue.increment(totalSpent),
                        "stats.beatsPurchased", FieldValue.increment(items.size()));
    }

    private void sendNotifications(String producerId, String buyerId, List<CartItem> items, double amount) {
        if (producerId != null && !producerId.equals("unknown")) {
            firestoreHelper.sendSaleNotification(producerId, items.get(0).getBeatTitle(), amount);
        }
        firestoreHelper.sendPurchaseNotification(buyerId, items.get(0).getBeatTitle());
    }
}
