// models/Review.java
package com.example.beathouse.models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Review {
    private String reviewId;
    private String producerId;
    private String buyerId;
    private String orderId;
    private String beatId;
    private double rating;
    private String comment;
    private long createdAt;

    public Review() {
        this.reviewId = "REV_" + UUID.randomUUID().toString().substring(0, 8);
        this.createdAt = System.currentTimeMillis();
        this.rating = 0.0;
    }

    public Review(String producerId, String buyerId, String orderId, String beatId, double rating, String comment) {
        this();
        this.producerId = producerId;
        this.buyerId = buyerId;
        this.orderId = orderId;
        this.beatId = beatId;
        setRating(rating);
        this.comment = comment;
    }

    // Геттеры и сеттеры
    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getProducerId() { return producerId; }
    public void setProducerId(String producerId) { this.producerId = producerId; }

    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getBeatId() { return beatId; }
    public void setBeatId(String beatId) { this.beatId = beatId; }

    public double getRating() { return rating; }
    public void setRating(double rating) {
        this.rating = Math.max(DatabaseStructure.ReviewFields.MIN_RATING,
                Math.min(DatabaseStructure.ReviewFields.MAX_RATING, rating));
    }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // Форматирование
    public String getFormattedRating() {
        return String.format("%.1f / 5.0", rating);
    }

    public String getStarRating() {
        int fullStars = (int) rating;
        boolean halfStar = (rating - fullStars) >= 0.5;
        StringBuilder stars = new StringBuilder();

        for (int i = 0; i < fullStars; i++) {
            stars.append("★");
        }
        if (halfStar) {
            stars.append("½");
        }
        int emptyStars = 5 - fullStars - (halfStar ? 1 : 0);
        for (int i = 0; i < emptyStars; i++) {
            stars.append("☆");
        }

        return stars.toString();
    }

    // Конвертация в Map для Firestore
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(DatabaseStructure.ReviewFields.REVIEW_ID, reviewId);
        map.put(DatabaseStructure.ReviewFields.PRODUCER_ID, producerId);
        map.put(DatabaseStructure.ReviewFields.BUYER_ID, buyerId);
        map.put(DatabaseStructure.ReviewFields.ORDER_ID, orderId);
        map.put(DatabaseStructure.ReviewFields.BEAT_ID, beatId);
        map.put(DatabaseStructure.ReviewFields.RATING, rating);
        map.put(DatabaseStructure.ReviewFields.COMMENT, comment != null ? comment : "");
        map.put(DatabaseStructure.ReviewFields.CREATED_AT, createdAt);
        return map;
    }

    // Создание из Map
    public static Review fromMap(Map<String, Object> map) {
        Review review = new Review();

        review.setReviewId(getString(map, DatabaseStructure.ReviewFields.REVIEW_ID));
        review.setProducerId(getString(map, DatabaseStructure.ReviewFields.PRODUCER_ID));
        review.setBuyerId(getString(map, DatabaseStructure.ReviewFields.BUYER_ID));
        review.setOrderId(getString(map, DatabaseStructure.ReviewFields.ORDER_ID));
        review.setBeatId(getString(map, DatabaseStructure.ReviewFields.BEAT_ID));
        review.setRating(getDouble(map, DatabaseStructure.ReviewFields.RATING));
        review.setComment(getString(map, DatabaseStructure.ReviewFields.COMMENT));

        if (map.get(DatabaseStructure.ReviewFields.CREATED_AT) instanceof Long) {
            review.setCreatedAt((Long) map.get(DatabaseStructure.ReviewFields.CREATED_AT));
        }

        return review;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : "";
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Double) return (Double) value;
        if (value instanceof Long) return ((Long) value).doubleValue();
        return 0.0;
    }

    @Override
    public String toString() {
        return "Review{" +
                "reviewId='" + reviewId + '\'' +
                ", producerId='" + producerId + '\'' +
                ", rating=" + rating +
                ", comment='" + comment + '\'' +
                '}';
    }
}