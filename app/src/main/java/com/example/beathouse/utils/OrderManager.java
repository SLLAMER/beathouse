package com.example.beathouse.utils;

import android.content.Context;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.models.Order;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class OrderManager {
    private Context context;
    private FirebaseFirestore db;

    public interface OrderCallback {
        void onAllOrdersCreated(List<Order> createdOrders);
        void onError(String error);
    }

    public OrderManager(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }

    public void processOrderCreation(List<CartItem> cartItems, String paymentMethod, OrderCallback callback) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            callback.onError("User not authenticated");
            return;
        }

        WriteBatch batch = db.batch();
        String orderId = UUID.randomUUID().toString();

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setBuyerId(userId);
        order.setItems(new ArrayList<>(cartItems));
        order.setTotal(calculateTotal(cartItems));
        order.setStatus("paid");
        order.setCreatedAt(System.currentTimeMillis());
        order.setPaidAt(System.currentTimeMillis());

        batch.set(db.collection("orders").document(orderId), order);

        List<Order> orders = new ArrayList<>();
        orders.add(order);

        batch.commit()
            .addOnSuccessListener(aVoid -> {
                new CartManager(context).clearCart();
                callback.onAllOrdersCreated(orders);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private double calculateTotal(List<CartItem> items) {
        double total = 0;
        for (CartItem item : items) {
            total += item.getPrice();
        }
        return total;
    }
}