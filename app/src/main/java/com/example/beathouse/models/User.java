// models/User.java (ПОЛНОСТЬЮ ГОТОВАЯ ВЕРСИЯ - С НАСТРОЙКАМИ УВЕДОМЛЕНИЙ)
package com.example.beathouse.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class User implements Serializable {

    private String id;
    private String email;
    private String username;
    private String fullName;
    private String bio;
    private String profileImage;
    private String location;
    private String socialInstagram;
    private String socialTelegram;
    private String socialVk;
    private String socialYoutube;
    private String phone;
    private String role = "buyer";
    private long createdAt;
    private boolean emailVerified;

    // ✅ Поля для удаления аккаунта
    private boolean markedForDeletion = false;
    private long deletionRequestedAt = 0;
    private String deletionReason = "";

    // ✅ Поля для подписок
    private int followers = 0;
    private int following = 0;

    // ✅ Настройки уведомлений
    private NotificationSettings notificationSettings;

    // Статистика пользователя
    private UserStats stats;

    // Конструкторы
    public User() {
        this.role = "buyer";
        this.stats = new UserStats();
        this.createdAt = System.currentTimeMillis();
        this.emailVerified = false;
        this.markedForDeletion = false;
        this.followers = 0;
        this.following = 0;
        this.notificationSettings = new NotificationSettings();
    }

    public User(String id, String email, String username) {
        this();
        this.id = id;
        this.email = email;
        this.username = username;
    }

    public User(String id, String email, String username, String role) {
        this(id, email, username);
        this.role = role != null ? role : "buyer";
    }

    // Основные геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    // Соцсети
    public String getSocialInstagram() { return socialInstagram; }
    public void setSocialInstagram(String s) { this.socialInstagram = s; }

    public String getSocialTelegram() { return socialTelegram; }
    public void setSocialTelegram(String s) { this.socialTelegram = s; }

    public String getSocialVk() { return socialVk; }
    public void setSocialVk(String s) { this.socialVk = s; }

    public String getSocialYoutube() { return socialYoutube; }
    public void setSocialYoutube(String s) { this.socialYoutube = s; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean v) { this.emailVerified = v; }

    // ✅ Геттеры и сеттеры для удаления аккаунта
    public boolean isMarkedForDeletion() { return markedForDeletion; }
    public void setMarkedForDeletion(boolean markedForDeletion) { this.markedForDeletion = markedForDeletion; }

    public long getDeletionRequestedAt() { return deletionRequestedAt; }
    public void setDeletionRequestedAt(long deletionRequestedAt) { this.deletionRequestedAt = deletionRequestedAt; }

    public String getDeletionReason() { return deletionReason; }
    public void setDeletionReason(String deletionReason) { this.deletionReason = deletionReason; }

    // ✅ Геттеры и сеттеры для подписок
    public int getFollowers() { return followers; }
    public void setFollowers(int followers) { this.followers = Math.max(0, followers); }
    public void incrementFollowers() { this.followers++; }
    public void decrementFollowers() { if (this.followers > 0) this.followers--; }

    public int getFollowing() { return following; }
    public void setFollowing(int following) { this.following = Math.max(0, following); }
    public void incrementFollowing() { this.following++; }
    public void decrementFollowing() { if (this.following > 0) this.following--; }

    // ✅ Геттеры и сеттеры для настроек уведомлений
    public NotificationSettings getNotificationSettings() {
        if (notificationSettings == null) {
            notificationSettings = new NotificationSettings();
        }
        return notificationSettings;
    }

    public void setNotificationSettings(NotificationSettings notificationSettings) {
        this.notificationSettings = notificationSettings;
    }

    // Роль
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role != null ? role : "buyer"; }
    public boolean isBuyer() { return "buyer".equals(role); }
    public boolean isSeller() { return "seller".equals(role); }

    // Статистика
    public UserStats getStats() { if (stats == null) stats = new UserStats(); return stats; }
    public void setStats(UserStats stats) { this.stats = stats != null ? stats : new UserStats(); }

    public int getBeatsCount() { return getStats().getBeatsSold(); }

    // ✅ Исправленные методы для счетчиков подписок
    public int getFollowersCount() { return followers; }
    public int getFollowingCount() { return following; }

    public boolean isProducer() { return isSeller(); }

    public String getRoleDisplayName() {
        if (isSeller()) return "Seller / Producer";
        if (isBuyer()) return "Buyer";
        return "User";
    }

    public String getFormattedRole() {
        return isSeller() ? "Seller" : "Buyer";
    }

    public String getBeatsCountLabel() {
        return isSeller() ? "Beats Sold" : "Beats Bought";
    }

    public String getTotalLabel() {
        return isSeller() ? "Total Earned" : "Total Spent";
    }

    public String getFormattedTotal() {
        if (isSeller()) return getStats().getFormattedTotalEarned();
        else return getStats().getFormattedTotalSpent();
    }

    public int getRelevantBeatsCount() {
        if (isSeller()) return getStats().getBeatsSold();
        else return getStats().getBeatsPurchased();
    }

    // Проверка наличия соцсетей
    public boolean hasSocialLinks() {
        return (socialInstagram != null && !socialInstagram.isEmpty()) ||
                (socialTelegram != null && !socialTelegram.isEmpty()) ||
                (socialVk != null && !socialVk.isEmpty()) ||
                (socialYoutube != null && !socialYoutube.isEmpty());
    }

    // ✅ Проверка, помечен ли аккаунт на удаление
    public boolean isActive() {
        return !markedForDeletion;
    }

    // toMap
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", id);
        map.put("email", email);
        map.put("username", username);
        map.put("fullName", fullName != null ? fullName : "");
        map.put("bio", bio != null ? bio : "");
        map.put("profileImage", profileImage != null ? profileImage : "");
        map.put("location", location != null ? location : "");
        map.put("role", role != null ? role : "buyer");
        map.put("createdAt", createdAt);
        map.put("emailVerified", emailVerified);
        map.put("phone", phone != null ? phone : "");
        // Соцсети
        map.put("socialInstagram", socialInstagram != null ? socialInstagram : "");
        map.put("socialTelegram", socialTelegram != null ? socialTelegram : "");
        map.put("socialVk", socialVk != null ? socialVk : "");
        map.put("socialYoutube", socialYoutube != null ? socialYoutube : "");
        // Поля удаления
        map.put("markedForDeletion", markedForDeletion);
        map.put("deletionRequestedAt", deletionRequestedAt);
        map.put("deletionReason", deletionReason != null ? deletionReason : "");
        // Поля подписок
        map.put("followers", followers);
        map.put("following", following);
        // Настройки уведомлений
        if (notificationSettings != null) {
            map.put("notificationSettings", notificationSettings.toMap());
        }
        if (stats != null) map.put("stats", stats.toMap());
        return map;
    }

    // fromMap
    @SuppressWarnings("unchecked")
    public static User fromMap(Map<String, Object> map) {
        if (map == null) return new User();
        User user = new User();
        user.setId(getStr(map, "userId"));
        user.setEmail(getStr(map, "email"));
        user.setUsername(getStr(map, "username"));
        user.setFullName(getStr(map, "fullName"));
        user.setBio(getStr(map, "bio"));
        user.setProfileImage(getStr(map, "profileImage"));
        user.setLocation(getStr(map, "location"));
        user.setRole(getStr(map, "role"));
        user.setPhone(getStr(map, "phone"));
        // Соцсети
        user.setSocialInstagram(getStr(map, "socialInstagram"));
        user.setSocialTelegram(getStr(map, "socialTelegram"));
        user.setSocialVk(getStr(map, "socialVk"));
        user.setSocialYoutube(getStr(map, "socialYoutube"));
        // Поля удаления
        if (map.containsKey("markedForDeletion")) user.setMarkedForDeletion((Boolean) map.get("markedForDeletion"));
        if (map.containsKey("deletionRequestedAt")) user.setDeletionRequestedAt((Long) map.get("deletionRequestedAt"));
        if (map.containsKey("deletionReason")) user.setDeletionReason(getStr(map, "deletionReason"));
        // Поля подписок
        if (map.containsKey("followers")) user.setFollowers(toInt(map.get("followers")));
        if (map.containsKey("following")) user.setFollowing(toInt(map.get("following")));
        // Настройки уведомлений
        if (map.containsKey("notificationSettings")) {
            Object settingsObj = map.get("notificationSettings");
            if (settingsObj instanceof Map) {
                user.setNotificationSettings(NotificationSettings.fromMap((Map<String, Object>) settingsObj));
            }
        }

        if (map.get("createdAt") instanceof Long) user.setCreatedAt((Long) map.get("createdAt"));
        if (map.get("emailVerified") instanceof Boolean) user.setEmailVerified((Boolean) map.get("emailVerified"));
        if (map.get("stats") instanceof Map) user.setStats(UserStats.fromMap((Map<String, Object>) map.get("stats")));
        return user;
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : "";
    }

    private static int toInt(Object v) {
        if (v instanceof Long) return ((Long) v).intValue();
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Double) return ((Double) v).intValue();
        if (v instanceof Float) return ((Float) v).intValue();
        return 0;
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username + "', role='" + role +
                "', followers=" + followers + ", following=" + following +
                ", active=" + !markedForDeletion + "}";
    }

    // ========== UserStats ==========
    public static class UserStats implements Serializable {
        private double totalSpent = 0.0;
        private double totalEarned = 0.0;
        private int beatsPurchased = 0;
        private int beatsSold = 0;

        public double getTotalSpent() { return totalSpent; }
        public void setTotalSpent(double v) { this.totalSpent = Math.max(0, v); }
        public void addToTotalSpent(double v) { this.totalSpent += Math.max(0, v); }
        public String getFormattedTotalSpent() { return "$" + String.format("%.0f", totalSpent); }

        public double getTotalEarned() { return totalEarned; }
        public void setTotalEarned(double v) { this.totalEarned = Math.max(0, v); }
        public void addToTotalEarned(double v) { this.totalEarned += Math.max(0, v); }
        public String getFormattedTotalEarned() { return "$" + String.format("%.0f", totalEarned); }

        public int getBeatsPurchased() { return beatsPurchased; }
        public void setBeatsPurchased(int v) { this.beatsPurchased = Math.max(0, v); }
        public void incrementBeatsPurchased() { this.beatsPurchased++; }

        public int getBeatsSold() { return beatsSold; }
        public void setBeatsSold(int v) { this.beatsSold = Math.max(0, v); }
        public void incrementBeatsSold() { this.beatsSold++; }

        public boolean hasEarnings() { return totalEarned > 0; }
        public boolean hasSpent() { return totalSpent > 0; }
        public boolean hasActivity() { return beatsPurchased > 0 || beatsSold > 0; }
        public int getTotalTransactions() { return beatsPurchased + beatsSold; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("totalSpent", totalSpent);
            map.put("totalEarned", totalEarned);
            map.put("beatsPurchased", beatsPurchased);
            map.put("beatsSold", beatsSold);
            return map;
        }

        public static UserStats fromMap(Map<String, Object> map) {
            UserStats s = new UserStats();
            if (map == null) return s;
            if (map.containsKey("totalSpent")) s.setTotalSpent(toDouble(map.get("totalSpent")));
            if (map.containsKey("totalEarned")) s.setTotalEarned(toDouble(map.get("totalEarned")));
            if (map.containsKey("beatsPurchased")) s.setBeatsPurchased(toInt(map.get("beatsPurchased")));
            if (map.containsKey("beatsSold")) s.setBeatsSold(toInt(map.get("beatsSold")));
            return s;
        }

        private static double toDouble(Object v) {
            if (v instanceof Double) return (Double) v;
            if (v instanceof Long) return ((Long) v).doubleValue();
            if (v instanceof Integer) return ((Integer) v).doubleValue();
            if (v instanceof Float) return ((Float) v).doubleValue();
            return 0.0;
        }

        private static int toInt(Object v) {
            if (v instanceof Long) return ((Long) v).intValue();
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof Double) return ((Double) v).intValue();
            if (v instanceof Float) return ((Float) v).intValue();
            return 0;
        }

        @Override
        public String toString() {
            return "UserStats{totalSpent=" + totalSpent + ", totalEarned=" + totalEarned +
                    ", beatsPurchased=" + beatsPurchased + ", beatsSold=" + beatsSold + '}';
        }
    }
}