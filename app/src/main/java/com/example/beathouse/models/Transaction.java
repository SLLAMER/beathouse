// models/Transaction.java
package com.example.beathouse.models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Transaction {
    private String transactionId;
    private String orderId;
    private String buyerId;
    private String producerId;
    private double amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private double platformFee;
    private double producerEarnings;
    private String qrCodeData;
    private long createdAt;
    private long completedAt;

    public Transaction() {
        this.transactionId = "TXN_" + UUID.randomUUID().toString().substring(0, 8);
        this.currency = "USD";
        this.status = DatabaseStructure.TransactionFields.STATUS_PENDING;
        this.createdAt = System.currentTimeMillis();
        this.platformFee = 0.0;
        this.producerEarnings = 0.0;
    }

    public Transaction(String orderId, String buyerId, String producerId, double amount) {
        this();
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.producerId = producerId;
        setAmount(amount);
    }

    // Геттеры и сеттеры
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

    public String getProducerId() { return producerId; }
    public void setProducerId(String producerId) { this.producerId = producerId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) {
        this.amount = Math.max(0, amount);
        calculateFees();
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public double getPlatformFee() { return platformFee; }
    public void setPlatformFee(double platformFee) { this.platformFee = platformFee; }

    public double getProducerEarnings() { return producerEarnings; }
    public void setProducerEarnings(double producerEarnings) { this.producerEarnings = producerEarnings; }

    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    private void calculateFees() {
        this.platformFee = amount * DatabaseStructure.TransactionFields.PLATFORM_FEE_PERCENT;
        this.producerEarnings = amount * DatabaseStructure.TransactionFields.PRODUCER_SHARE_PERCENT;
    }

    // Статусные методы
    public boolean isPending() {
        return DatabaseStructure.TransactionFields.STATUS_PENDING.equals(status);
    }

    public boolean isCompleted() {
        return DatabaseStructure.TransactionFields.STATUS_COMPLETED.equals(status);
    }

    public boolean isFailed() {
        return DatabaseStructure.TransactionFields.STATUS_FAILED.equals(status);
    }

    public void markAsCompleted() {
        this.status = DatabaseStructure.TransactionFields.STATUS_COMPLETED;
        this.completedAt = System.currentTimeMillis();
    }

    public void markAsFailed() {
        this.status = DatabaseStructure.TransactionFields.STATUS_FAILED;
        this.completedAt = System.currentTimeMillis();
    }

    // Форматирование сумм
    public String getFormattedAmount() {
        return String.format("$%.2f %s", amount, currency);
    }

    public String getFormattedPlatformFee() {
        return String.format("$%.2f", platformFee);
    }

    public String getFormattedProducerEarnings() {
        return String.format("$%.2f", producerEarnings);
    }

    // Конвертация в Map для Firestore
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(DatabaseStructure.TransactionFields.TRANSACTION_ID, transactionId);
        map.put(DatabaseStructure.TransactionFields.ORDER_ID, orderId);
        map.put(DatabaseStructure.TransactionFields.BUYER_ID, buyerId);
        map.put(DatabaseStructure.TransactionFields.PRODUCER_ID, producerId);
        map.put(DatabaseStructure.TransactionFields.AMOUNT, amount);
        map.put(DatabaseStructure.TransactionFields.CURRENCY, currency);
        map.put(DatabaseStructure.TransactionFields.STATUS, status);
        map.put(DatabaseStructure.TransactionFields.PAYMENT_METHOD, paymentMethod);
        map.put(DatabaseStructure.TransactionFields.PLATFORM_FEE, platformFee);
        map.put(DatabaseStructure.TransactionFields.PRODUCER_EARNINGS, producerEarnings);
        map.put(DatabaseStructure.TransactionFields.QR_CODE_DATA, qrCodeData != null ? qrCodeData : "");
        map.put(DatabaseStructure.TransactionFields.CREATED_AT, createdAt);
        map.put(DatabaseStructure.TransactionFields.COMPLETED_AT, completedAt);
        return map;
    }

    // Создание из Map
    public static Transaction fromMap(Map<String, Object> map) {
        Transaction transaction = new Transaction();

        transaction.setTransactionId(getString(map, DatabaseStructure.TransactionFields.TRANSACTION_ID));
        transaction.setOrderId(getString(map, DatabaseStructure.TransactionFields.ORDER_ID));
        transaction.setBuyerId(getString(map, DatabaseStructure.TransactionFields.BUYER_ID));
        transaction.setProducerId(getString(map, DatabaseStructure.TransactionFields.PRODUCER_ID));

        transaction.setAmount(getDouble(map, DatabaseStructure.TransactionFields.AMOUNT));
        transaction.setCurrency(getString(map, DatabaseStructure.TransactionFields.CURRENCY, "USD"));
        transaction.setStatus(getString(map, DatabaseStructure.TransactionFields.STATUS, "pending"));
        transaction.setPaymentMethod(getString(map, DatabaseStructure.TransactionFields.PAYMENT_METHOD));
        transaction.setPlatformFee(getDouble(map, DatabaseStructure.TransactionFields.PLATFORM_FEE));
        transaction.setProducerEarnings(getDouble(map, DatabaseStructure.TransactionFields.PRODUCER_EARNINGS));
        transaction.setQrCodeData(getString(map, DatabaseStructure.TransactionFields.QR_CODE_DATA));

        if (map.get(DatabaseStructure.TransactionFields.CREATED_AT) instanceof Long) {
            transaction.setCreatedAt((Long) map.get(DatabaseStructure.TransactionFields.CREATED_AT));
        }
        if (map.get(DatabaseStructure.TransactionFields.COMPLETED_AT) instanceof Long) {
            transaction.setCompletedAt((Long) map.get(DatabaseStructure.TransactionFields.COMPLETED_AT));
        }

        return transaction;
    }

    private static String getString(Map<String, Object> map, String key) {
        return getString(map, key, "");
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Double) return (Double) value;
        if (value instanceof Long) return ((Long) value).doubleValue();
        return 0.0;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                '}';
    }
}