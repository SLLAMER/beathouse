package com.example.beathouse.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.beathouse.models.Beat;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.models.Order;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderManager {
    private final Context context;
    private final FirebaseFirestore db;
    private final FirestoreHelper firestoreHelper;
    private static final String TAG = "OrderManager";

    public interface OrderCallback {
        void onAllOrdersCreated(List<Order> createdOrders);
        void onError(String error);
    }

    public OrderManager(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.firestoreHelper = new FirestoreHelper();
    }

    public void processOrderCreation(List<CartItem> cartItems, String paymentMethod, OrderCallback callback) {
        if (cartItems == null || cartItems.isEmpty()) {
            callback.onError("Cart is empty");
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        // Group items by producer
        Map<String, List<CartItem>> producerGroups = new HashMap<>();
        for (CartItem item : cartItems) {
            String producerId = item.getProducerId();
            if (!producerGroups.containsKey(producerId)) {
                producerGroups.put(producerId, new ArrayList<>());
            }
            producerGroups.get(producerId).add(item);
        }

        List<Order> createdOrders = new ArrayList<>();
        final int totalProducers = producerGroups.size();
        final int[] processedCount = {0};

        for (Map.Entry<String, List<CartItem>> entry : producerGroups.entrySet()) {
            String producerId = entry.getKey();
            List<CartItem> items = entry.getValue();

            Order order = new Order();
            order.setId("ORD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setUserId(userId);
            order.setBuyerId(userId);
            order.setUserEmail(userEmail);
            order.setProducerId(producerId);
            order.setItems(items);
            order.setStatus("paid");
            order.setPaidAt(System.currentTimeMillis());
            order.setTransactionId("TX_" + System.currentTimeMillis());

            db.collection("orders").document(order.getId()).set(order.toMap())
                    .addOnSuccessListener(aVoid -> {
                        createdOrders.add(order);

                        // Update producer stats and send notifications
                        updateStatsAndNotify(order);

                        processedCount[0]++;
                        if (processedCount[0] == totalProducers) {
                            new CartManager(context).clearCart();
                            callback.onAllOrdersCreated(createdOrders);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating order: " + e.getMessage());
                        processedCount[0]++;
                        if (processedCount[0] == totalProducers) {
                            callback.onAllOrdersCreated(createdOrders);
                        }
                    });
        }
    }

    private void updateStatsAndNotify(Order order) {
        // Update producer stats
        firestoreHelper.updateProducerAfterSale(order.getProducerId(), order.getTotal(), order.getItemCount());

        // Send notifications for each beat
        for (CartItem item : order.getItems()) {
            firestoreHelper.sendSaleNotification(order.getProducerId(), item.getBeatTitle(), item.getPrice());
            firestoreHelper.sendPurchaseNotification(order.getUserId(), item.getBeatTitle());
        }
    }
}
