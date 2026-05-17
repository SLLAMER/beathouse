package com.example.beathouse.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class NotificationSettings implements Serializable {

    private boolean enableMessages = true;   // Сообщения в чате
    private boolean enableFollows = true;    // Новые подписчики
    private boolean enableSales = true;      // Продажи (для продавцов)
    private boolean enablePurchases = true;  // Покупки (для покупателей)
    private boolean enableReviews = true;    // Отзывы/оценки

    public NotificationSettings() {
        // Значения по умолчанию - все уведомления включены
    }

    // Геттеры и сеттеры
    public boolean isEnableMessages() { return enableMessages; }
    public void setEnableMessages(boolean enableMessages) { this.enableMessages = enableMessages; }

    public boolean isEnableFollows() { return enableFollows; }
    public void setEnableFollows(boolean enableFollows) { this.enableFollows = enableFollows; }

    public boolean isEnableSales() { return enableSales; }
    public void setEnableSales(boolean enableSales) { this.enableSales = enableSales; }

    public boolean isEnablePurchases() { return enablePurchases; }
    public void setEnablePurchases(boolean enablePurchases) { this.enablePurchases = enablePurchases; }

    public boolean isEnableReviews() { return enableReviews; }
    public void setEnableReviews(boolean enableReviews) { this.enableReviews = enableReviews; }

    // Проверка, включен ли конкретный тип
    public boolean isEnabled(String type) {
        switch (type) {
            case "message":
            case "chat":
                return enableMessages;
            case "follow":
                return enableFollows;
            case "sale":
                return enableSales;
            case "purchase":
                return enablePurchases;
            case "review":
            case "rating":
                return enableReviews;
            default:
                return true;
        }
    }

    // Конвертация в Map для Firestore
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("enableMessages", enableMessages);
        map.put("enableFollows", enableFollows);
        map.put("enableSales", enableSales);
        map.put("enablePurchases", enablePurchases);
        map.put("enableReviews", enableReviews);
        return map;
    }

    // Создание из Map (Firestore)
    @SuppressWarnings("unchecked")
    public static NotificationSettings fromMap(Map<String, Object> map) {
        if (map == null) return new NotificationSettings();

        NotificationSettings settings = new NotificationSettings();

        if (map.containsKey("enableMessages")) {
            settings.setEnableMessages((Boolean) map.get("enableMessages"));
        }
        if (map.containsKey("enableFollows")) {
            settings.setEnableFollows((Boolean) map.get("enableFollows"));
        }
        if (map.containsKey("enableSales")) {
            settings.setEnableSales((Boolean) map.get("enableSales"));
        }
        if (map.containsKey("enablePurchases")) {
            settings.setEnablePurchases((Boolean) map.get("enablePurchases"));
        }
        if (map.containsKey("enableReviews")) {
            settings.setEnableReviews((Boolean) map.get("enableReviews"));
        }

        return settings;
    }

    @Override
    public String toString() {
        return "NotificationSettings{" +
                "messages=" + enableMessages +
                ", follows=" + enableFollows +
                ", sales=" + enableSales +
                ", purchases=" + enablePurchases +
                ", reviews=" + enableReviews +
                '}';
    }
}