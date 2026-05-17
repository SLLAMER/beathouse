// models/Order.java (ПОЛНОСТЬЮ ГОТОВАЯ ВЕРСИЯ - С buyerId И БЕЗ ЦЕНТОВ)
package com.example.beathouse.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Order {
    private String id;
    private String userId;
    private String buyerId;  // ✅ ДОБАВЛЕНО ПОЛЕ
    private String userEmail;
    private String producerId;
    private List<CartItem> items;
    private double total;
    private String status;
    private long createdAt;
    private long paidAt;
    private String transactionId;
    private String qrCodeData;

    public Order() {
        this.items = new ArrayList<>();
        this.status = "pending";
        this.createdAt = System.currentTimeMillis();
    }

    // ✅ Геттеры и сеттеры для buyerId
    public String getBuyerId() {
        return buyerId != null ? buyerId : userId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getProducerId() { return producerId; }
    public void setProducerId(String producerId) { this.producerId = producerId; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        calculateTotal();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getPaidAt() { return paidAt; }
    public void setPaidAt(long paidAt) { this.paidAt = paidAt; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }

    public void addItem(CartItem item) {
        if (items == null) items = new ArrayList<>();
        items.add(item);
        calculateTotal();
    }

    public void removeItem(String itemId) {
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getId().equals(itemId)) {
                    items.remove(i);
                    calculateTotal();
                    break;
                }
            }
        }
    }

    public void calculateTotal() {
        total = 0;
        if (items != null) {
            for (CartItem item : items) {
                if (!item.isFree()) total += item.getPrice();
            }
        }
    }

    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public boolean hasFreeItems() {
        if (items != null) {
            for (CartItem item : items) {
                if (item.isFree()) return true;
            }
        }
        return false;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("userId", userId);
        map.put("buyerId", buyerId != null ? buyerId : userId); // ✅ buyerId для поиска
        map.put("userEmail", userEmail);
        map.put("producerId", producerId);
        map.put("total", total);
        map.put("status", status);
        map.put("createdAt", createdAt);
        map.put("paidAt", paidAt);
        map.put("transactionId", transactionId);
        map.put("qrCodeData", qrCodeData);

        List<Map<String, Object>> itemsList = new ArrayList<>();
        if (items != null) {
            for (CartItem item : items) {
                itemsList.add(item.toMap());
            }
        }
        map.put("items", itemsList);
        return map;
    }

    public static Order fromMap(Map<String, Object> map) {
        Order order = new Order();
        if (map.containsKey("id")) order.setId((String) map.get("id"));
        if (map.containsKey("userId")) order.setUserId((String) map.get("userId"));
        if (map.containsKey("buyerId")) order.setBuyerId((String) map.get("buyerId"));
        if (map.containsKey("userEmail")) order.setUserEmail((String) map.get("userEmail"));
        if (map.containsKey("producerId")) order.setProducerId((String) map.get("producerId"));

        if (map.get("total") instanceof Double) {
            order.setTotal((Double) map.get("total"));
        } else if (map.get("total") instanceof Long) {
            order.setTotal(((Long) map.get("total")).doubleValue());
        }

        if (map.containsKey("status")) order.setStatus((String) map.get("status"));

        if (map.get("createdAt") instanceof Long) order.setCreatedAt((Long) map.get("createdAt"));
        if (map.get("paidAt") instanceof Long) order.setPaidAt((Long) map.get("paidAt"));

        if (map.containsKey("transactionId")) order.setTransactionId((String) map.get("transactionId"));
        if (map.containsKey("qrCodeData")) order.setQrCodeData((String) map.get("qrCodeData"));

        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) map.get("items");
        if (itemsList != null) {
            List<CartItem> items = new ArrayList<>();
            for (Map<String, Object> itemMap : itemsList) {
                items.add(CartItem.fromMap(itemMap));
            }
            order.setItems(items);
        }

        return order;
    }

    // ✅ Убираем центы - форматируем без копеек
    public String getFormattedTotal() {
        if (total == 0) return "FREE";
        return "$" + String.format("%.0f", total);
    }

    public String getStatusText() {
        switch (status) {
            case "pending": return "Pending Payment";
            case "paid": return "Paid";
            case "cancelled": return "Cancelled";
            case "completed": return "Completed";
            default: return "Unknown";
        }
    }
}