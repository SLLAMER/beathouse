// models/Producer.java
package com.example.beathouse.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Producer {
    private String producerId;
    private String username;
    private String email;
    private String displayName;
    private String profileImage;
    private String bio;
    private List<String> genres;
    private double rating;
    private int totalSales;
    private int totalBeats;
    private double totalRevenue;
    private Map<String, String> socialLinks;
    private String location;
    private boolean verified;
    private boolean featured;
    private long createdAt;
    private int followers;
    private int following;

    public Producer() {
        this.genres = new ArrayList<>();
        this.socialLinks = new HashMap<>();
        this.rating = 0.0;
        this.totalSales = 0;
        this.totalBeats = 0;
        this.totalRevenue = 0.0;
        this.verified = false;
        this.featured = false;
        this.createdAt = System.currentTimeMillis();
        this.followers = 0;
        this.following = 0;

        // Инициализация социальных ссылок
        this.socialLinks.put("instagram", "");
        this.socialLinks.put("youtube", "");
        this.socialLinks.put("soundcloud", "");
        this.socialLinks.put("twitter", "");
    }

    // Геттеры и сеттеры
    public String getProducerId() { return producerId; }
    public void setProducerId(String producerId) { this.producerId = producerId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres != null ? genres : new ArrayList<>(); }

    public double getRating() { return rating; }
    public void setRating(double rating) {
        this.rating = Math.max(0, Math.min(5, rating));
    }

    public int getTotalSales() { return totalSales; }
    public void setTotalSales(int totalSales) { this.totalSales = totalSales; }

    public int getTotalBeats() { return totalBeats; }
    public void setTotalBeats(int totalBeats) { this.totalBeats = totalBeats; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public Map<String, String> getSocialLinks() { return socialLinks; }
    public void setSocialLinks(Map<String, String> socialLinks) {
        this.socialLinks = socialLinks != null ? socialLinks : new HashMap<>();
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public int getFollowers() { return followers; }
    public void setFollowers(int followers) { this.followers = followers; }

    public int getFollowing() { return following; }
    public void setFollowing(int following) { this.following = following; }

    // Социальные ссылки
    public String getInstagram() { return socialLinks.get("instagram"); }
    public void setInstagram(String instagram) { socialLinks.put("instagram", instagram); }

    public String getYoutube() { return socialLinks.get("youtube"); }
    public void setYoutube(String youtube) { socialLinks.put("youtube", youtube); }

    public String getSoundcloud() { return socialLinks.get("soundcloud"); }
    public void setSoundcloud(String soundcloud) { socialLinks.put("soundcloud", soundcloud); }

    public String getTwitter() { return socialLinks.get("twitter"); }
    public void setTwitter(String twitter) { socialLinks.put("twitter", twitter); }

    // Форматирование рейтинга
    public String getFormattedRating() {
        return String.format("%.1f", rating);
    }

    // Статистика
    public void incrementTotalSales() { this.totalSales++; }
    public void incrementTotalBeats() { this.totalBeats++; }
    public void addRevenue(double amount) { this.totalRevenue += amount; }

    // Конвертация в Map для Firestore
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(DatabaseStructure.ProducerFields.PRODUCER_ID, producerId);
        map.put(DatabaseStructure.ProducerFields.USERNAME, username);
        map.put(DatabaseStructure.ProducerFields.EMAIL, email);
        map.put(DatabaseStructure.ProducerFields.DISPLAY_NAME, displayName);
        map.put(DatabaseStructure.ProducerFields.PROFILE_IMAGE, profileImage != null ? profileImage : "");
        map.put(DatabaseStructure.ProducerFields.BIO, bio != null ? bio : "");
        map.put(DatabaseStructure.ProducerFields.GENRES, genres != null ? genres : new ArrayList<>());
        map.put(DatabaseStructure.ProducerFields.RATING, rating);
        map.put(DatabaseStructure.ProducerFields.TOTAL_SALES, totalSales);
        map.put(DatabaseStructure.ProducerFields.TOTAL_BEATS, totalBeats);
        map.put(DatabaseStructure.ProducerFields.TOTAL_REVENUE, totalRevenue);
        map.put(DatabaseStructure.ProducerFields.SOCIAL_LINKS, socialLinks != null ? socialLinks : new HashMap<>());
        map.put(DatabaseStructure.ProducerFields.LOCATION, location != null ? location : "");
        map.put(DatabaseStructure.ProducerFields.VERIFIED, verified);
        map.put(DatabaseStructure.ProducerFields.FEATURED, featured);
        map.put(DatabaseStructure.ProducerFields.CREATED_AT, createdAt);
        map.put(DatabaseStructure.ProducerFields.FOLLOWERS, followers);
        map.put(DatabaseStructure.ProducerFields.FOLLOWING, following);
        return map;
    }

    // Создание из Map (Firestore)
    @SuppressWarnings("unchecked")
    public static Producer fromMap(Map<String, Object> map) {
        if (map == null) return new Producer();

        Producer producer = new Producer();

        producer.setProducerId(getString(map, DatabaseStructure.ProducerFields.PRODUCER_ID));
        producer.setUsername(getString(map, DatabaseStructure.ProducerFields.USERNAME));
        producer.setEmail(getString(map, DatabaseStructure.ProducerFields.EMAIL));
        producer.setDisplayName(getString(map, DatabaseStructure.ProducerFields.DISPLAY_NAME));
        producer.setProfileImage(getString(map, DatabaseStructure.ProducerFields.PROFILE_IMAGE));
        producer.setBio(getString(map, DatabaseStructure.ProducerFields.BIO));
        producer.setLocation(getString(map, DatabaseStructure.ProducerFields.LOCATION));

        // Числовые поля
        producer.setRating(getDouble(map, DatabaseStructure.ProducerFields.RATING));
        producer.setTotalSales(getInt(map, DatabaseStructure.ProducerFields.TOTAL_SALES));
        producer.setTotalBeats(getInt(map, DatabaseStructure.ProducerFields.TOTAL_BEATS));
        producer.setTotalRevenue(getDouble(map, DatabaseStructure.ProducerFields.TOTAL_REVENUE));

        // Булевы поля
        producer.setVerified(getBoolean(map, DatabaseStructure.ProducerFields.VERIFIED));
        producer.setFeatured(getBoolean(map, DatabaseStructure.ProducerFields.FEATURED));

        // Даты
        if (map.get(DatabaseStructure.ProducerFields.CREATED_AT) instanceof Long) {
            producer.setCreatedAt((Long) map.get(DatabaseStructure.ProducerFields.CREATED_AT));
        }

        producer.setFollowers(getInt(map, DatabaseStructure.ProducerFields.FOLLOWERS));
        producer.setFollowing(getInt(map, DatabaseStructure.ProducerFields.FOLLOWING));

        // Жанры
        Object genresObj = map.get(DatabaseStructure.ProducerFields.GENRES);
        if (genresObj instanceof List) {
            producer.setGenres((List<String>) genresObj);
        }

        // Социальные ссылки
        Object socialObj = map.get(DatabaseStructure.ProducerFields.SOCIAL_LINKS);
        if (socialObj instanceof Map) {
            producer.setSocialLinks((Map<String, String>) socialObj);
        }

        return producer;
    }

    // Вспомогательные методы для безопасного извлечения
    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : "";
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Double) return ((Double) value).intValue();
        return 0;
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Double) return (Double) value;
        if (value instanceof Long) return ((Long) value).doubleValue();
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        return 0.0;
    }

    private static boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : false;
    }

    @Override
    public String toString() {
        return "Producer{" +
                "producerId='" + producerId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", rating=" + rating +
                ", totalBeats=" + totalBeats +
                ", totalSales=" + totalSales +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Producer producer = (Producer) o;
        return producerId != null && producerId.equals(producer.producerId);
    }

    @Override
    public int hashCode() {
        return producerId != null ? producerId.hashCode() : 0;
    }
}