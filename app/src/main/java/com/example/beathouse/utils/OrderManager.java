package com.example.beathouse.utils;

import android.content.Context;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.models.Order;
import java.util.List;
import java.util.ArrayList;

public class OrderManager {
    public interface OrderCallback {
        void onAllOrdersCreated(List<Order> createdOrders);
        void onError(String error);
    }

    public OrderManager(Context context) {}

    public void processOrderCreation(List<CartItem> items, String method, OrderCallback callback) {
        if (callback != null) callback.onAllOrdersCreated(new ArrayList<>());
    }
}
