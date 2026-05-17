// models/DatabaseStructure.java (ПОЛНОСТЬЮ ГОТОВАЯ ВЕРСИЯ)
package com.example.beathouse.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ОПТИМИЗИРОВАННАЯ СТРУКТУРА БАЗЫ ДАННЫХ BEATHOUSE
 * 8 основных коллекций в Firestore
 */
public class DatabaseStructure {

    // ========== НАЗВАНИЯ КОЛЛЕКЦИЙ ==========
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_PRODUCERS = "producers";
    public static final String COLLECTION_BEATS = "beats";
    public static final String COLLECTION_ORDERS = "orders";
    public static final String COLLECTION_TRANSACTIONS = "transactions";
    public static final String COLLECTION_REVIEWS = "reviews";
    public static final String COLLECTION_CART = "cart";
    public static final String COLLECTION_NOTIFICATIONS = "notifications";
    public static final String COLLECTION_FOLLOWING = "following"; // Подписки

    // =====================================================================
    // 1. СТРУКТУРА users (Пользователи)
    // =====================================================================
    public static class UserFields {
        public static final String USER_ID = "userId";
        public static final String EMAIL = "email";
        public static final String USERNAME = "username";
        public static final String FULL_NAME = "fullName";
        public static final String ROLE = "role";
        public static final String PROFILE_IMAGE = "profileImage";
        public static final String BIO = "bio";
        public static final String LOCATION = "location";
        public static final String CREATED_AT = "createdAt";
        public static final String EMAIL_VERIFIED = "emailVerified";
        public static final String FOLLOWING_COUNT = "following";
        public static final String FOLLOWERS_COUNT = "followers";

        // Статистика
        public static final String STATS = "stats";
        public static final String STATS_TOTAL_SPENT = "stats.totalSpent";
        public static final String STATS_TOTAL_EARNED = "stats.totalEarned";
        public static final String STATS_BEATS_PURCHASED = "stats.beatsPurchased";
        public static final String STATS_BEATS_SOLD = "stats.beatsSold";

        // Роли
        public static final String ROLE_BUYER = "buyer";
        public static final String ROLE_SELLER = "seller";
    }

    // =====================================================================
    // 2. СТРУКТУРА producers (Продюсеры)
    // =====================================================================
    public static class ProducerFields {
        public static final String PRODUCER_ID = "producerId";
        public static final String USERNAME = "username";
        public static final String EMAIL = "email";
        public static final String DISPLAY_NAME = "displayName";
        public static final String PROFILE_IMAGE = "profileImage";
        public static final String BIO = "bio";
        public static final String GENRES = "genres";
        public static final String RATING = "rating";
        public static final String TOTAL_SALES = "totalSales";
        public static final String TOTAL_BEATS = "totalBeats";
        public static final String TOTAL_REVENUE = "totalRevenue";
        public static final String SOCIAL_LINKS = "socialLinks";
        public static final String LOCATION = "location";
        public static final String VERIFIED = "verified";
        public static final String FEATURED = "featured";
        public static final String CREATED_AT = "createdAt";
        public static final String FOLLOWERS = "followers";
        public static final String FOLLOWING = "following";
    }

    // =====================================================================
    // 3. СТРУКТУРА beats (Биты)
    // =====================================================================
    public static class BeatFields {
        public static final String BEAT_ID = "beatId";
        public static final String PRODUCER_ID = "producerId";
        public static final String USER_ID = "userId";
        public static final String USER_NAME = "userName";
        public static final String TITLE = "title";
        public static final String DESCRIPTION = "description";
        public static final String GENRE = "genre";
        public static final String KEY = "key";
        public static final String BPM = "bpm";
        public static final String PRICE = "price";
        public static final String IS_FREE = "isFree";
        public static final String IS_EXCLUSIVE = "isExclusive";
        public static final String IS_CHUNKED = "isChunked";
        public static final String AUDIO_DATA = "audioData";
        public static final String COVER_IMAGE = "coverImage";
        public static final String TAGS = "tags";
        public static final String LICENSE_TYPE = "licenseType";
        public static final String USAGE_TERMS = "usageTerms";
        public static final String PREVIEW_URL = "previewUrl";
        public static final String STATS = "stats";
        public static final String CREATED_AT = "createdAt";
        public static final String STATUS = "status";

        // Статистика бита
        public static final String STATS_PLAYS = "stats.plays";
        public static final String STATS_LIKES = "stats.likes";
        public static final String STATS_DOWNLOADS = "stats.downloads";
        public static final String STATS_REVENUE = "stats.revenue";

        // Типы лицензий
        public static final String LICENSE_BASIC = "basic";
        public static final String LICENSE_PREMIUM = "premium";
        public static final String LICENSE_EXCLUSIVE = "exclusive";

        // Статусы
        public static final String STATUS_ACTIVE = "active";
        public static final String STATUS_DISABLED = "disabled";
    }

    // =====================================================================
    // 4. СТРУКТУРА orders (Заказы)
    // =====================================================================
    public static class OrderFields {
        public static final String ORDER_ID = "orderId";
        public static final String BUYER_ID = "buyerId";
        public static final String PRODUCER_ID = "producerId";
        public static final String ITEMS = "items";
        public static final String TOTAL_AMOUNT = "totalAmount";
        public static final String STATUS = "status";
        public static final String PAYMENT_METHOD = "paymentMethod";
        public static final String TRANSACTION_ID = "transactionId";
        public static final String CREATED_AT = "createdAt";
        public static final String PAID_AT = "paidAt";
        public static final String COMPLETED_AT = "completedAt";

        // Статусы
        public static final String STATUS_PENDING = "pending";
        public static final String STATUS_PAID = "paid";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_CANCELLED = "cancelled";

        // Методы оплаты
        public static final String PAYMENT_QR = "qr";
        public static final String PAYMENT_CARD = "card";
    }

    // =====================================================================
    // 5. СТРУКТУРА transactions (Транзакции)
    // =====================================================================
    public static class TransactionFields {
        public static final String TRANSACTION_ID = "transactionId";
        public static final String ORDER_ID = "orderId";
        public static final String BUYER_ID = "buyerId";
        public static final String PRODUCER_ID = "producerId";
        public static final String AMOUNT = "amount";
        public static final String CURRENCY = "currency";
        public static final String STATUS = "status";
        public static final String PAYMENT_METHOD = "paymentMethod";
        public static final String PLATFORM_FEE = "platformFee";
        public static final String PRODUCER_EARNINGS = "producerEarnings";
        public static final String QR_CODE_DATA = "qrCodeData";
        public static final String CREATED_AT = "createdAt";
        public static final String COMPLETED_AT = "completedAt";

        // Статусы
        public static final String STATUS_PENDING = "pending";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_FAILED = "failed";

        // Комиссия
        public static final double PLATFORM_FEE_PERCENT = 0.10;
        public static final double PRODUCER_SHARE_PERCENT = 0.90;
    }

    // =====================================================================
    // 6. СТРУКТУРА reviews (Отзывы)
    // =====================================================================
    public static class ReviewFields {
        public static final String REVIEW_ID = "reviewId";
        public static final String PRODUCER_ID = "producerId";
        public static final String BUYER_ID = "buyerId";
        public static final String ORDER_ID = "orderId";
        public static final String BEAT_ID = "beatId";
        public static final String RATING = "rating";
        public static final String COMMENT = "comment";
        public static final String CREATED_AT = "createdAt";

        public static final double MIN_RATING = 0.0;
        public static final double MAX_RATING = 5.0;
    }

    // =====================================================================
    // 7. СТРУКТУРА cart (Корзина)
    // =====================================================================
    public static class CartFields {
        public static final String CART_ID = "cartId";
        public static final String USER_ID = "userId";
        public static final String ITEMS = "items";
        public static final String UPDATED_AT = "updatedAt";
    }

    // =====================================================================
    // 8. СТРУКТУРА notifications (Уведомления)
    // =====================================================================
    public static class NotificationFields {
        public static final String NOTIFICATION_ID = "notificationId";
        public static final String USER_ID = "userId";
        public static final String TYPE = "type";
        public static final String TITLE = "title";
        public static final String MESSAGE = "message";
        public static final String READ = "read";
        public static final String DATA = "data";
        public static final String CREATED_AT = "createdAt";

        // Типы уведомлений
        public static final String TYPE_PURCHASE = "purchase";
        public static final String TYPE_SALE = "sale";
        public static final String TYPE_REVIEW = "review";
        public static final String TYPE_SYSTEM = "system";
        public static final String TYPE_FOLLOW = "follow";
    }

    // =====================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // =====================================================================

    public static Map<String, Object> createUserData(String userId, String email, String username, String role) {
        Map<String, Object> data = new HashMap<>();
        data.put(UserFields.USER_ID, userId);
        data.put(UserFields.EMAIL, email);
        data.put(UserFields.USERNAME, username);
        data.put(UserFields.ROLE, role);
        data.put(UserFields.CREATED_AT, System.currentTimeMillis());
        data.put(UserFields.EMAIL_VERIFIED, false);
        data.put(UserFields.BIO, "");
        data.put(UserFields.PROFILE_IMAGE, "");
        data.put(UserFields.FOLLOWING_COUNT, 0);
        data.put(UserFields.FOLLOWERS_COUNT, 0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSpent", 0.0);
        stats.put("totalEarned", 0.0);
        stats.put("beatsPurchased", 0);
        stats.put("beatsSold", 0);
        data.put(UserFields.STATS, stats);

        return data;
    }

    public static Map<String, Object> createProducerData(String producerId, String username, String email) {
        Map<String, Object> data = new HashMap<>();
        data.put(ProducerFields.PRODUCER_ID, producerId);
        data.put(ProducerFields.USERNAME, username);
        data.put(ProducerFields.EMAIL, email);
        data.put(ProducerFields.DISPLAY_NAME, username);
        data.put(ProducerFields.BIO, "");
        data.put(ProducerFields.RATING, 0.0);
        data.put(ProducerFields.TOTAL_SALES, 0);
        data.put(ProducerFields.TOTAL_BEATS, 0);
        data.put(ProducerFields.TOTAL_REVENUE, 0.0);
        data.put(ProducerFields.VERIFIED, false);
        data.put(ProducerFields.FEATURED, false);
        data.put(ProducerFields.CREATED_AT, System.currentTimeMillis());
        data.put(ProducerFields.FOLLOWERS, 0);
        data.put(ProducerFields.FOLLOWING, 0);
        data.put(ProducerFields.GENRES, new ArrayList<String>());
        data.put(ProducerFields.LOCATION, "");

        Map<String, String> socialLinks = new HashMap<>();
        socialLinks.put("instagram", "");
        socialLinks.put("youtube", "");
        socialLinks.put("soundcloud", "");
        socialLinks.put("twitter", "");
        data.put(ProducerFields.SOCIAL_LINKS, socialLinks);

        return data;
    }

    public static Map<String, Object> createOrderData(String orderId, String buyerId, String producerId,
                                                      List<Map<String, Object>> items, double totalAmount) {
        Map<String, Object> data = new HashMap<>();
        data.put(OrderFields.ORDER_ID, orderId);
        data.put(OrderFields.BUYER_ID, buyerId);
        data.put(OrderFields.PRODUCER_ID, producerId);
        data.put(OrderFields.ITEMS, items);
        data.put(OrderFields.TOTAL_AMOUNT, totalAmount);
        data.put(OrderFields.STATUS, OrderFields.STATUS_PENDING);
        data.put(OrderFields.CREATED_AT, System.currentTimeMillis());
        return data;
    }

    public static Map<String, Object> createTransactionData(String transactionId, String orderId, String buyerId,
                                                            String producerId, double amount, String paymentMethod) {
        Map<String, Object> data = new HashMap<>();
        data.put(TransactionFields.TRANSACTION_ID, transactionId);
        data.put(TransactionFields.ORDER_ID, orderId);
        data.put(TransactionFields.BUYER_ID, buyerId);
        data.put(TransactionFields.PRODUCER_ID, producerId);
        data.put(TransactionFields.AMOUNT, amount);
        data.put(TransactionFields.CURRENCY, "USD");
        data.put(TransactionFields.STATUS, TransactionFields.STATUS_PENDING);
        data.put(TransactionFields.PAYMENT_METHOD, paymentMethod);
        data.put(TransactionFields.PLATFORM_FEE, amount * TransactionFields.PLATFORM_FEE_PERCENT);
        data.put(TransactionFields.PRODUCER_EARNINGS, amount * TransactionFields.PRODUCER_SHARE_PERCENT);
        data.put(TransactionFields.CREATED_AT, System.currentTimeMillis());
        return data;
    }

    public static Map<String, Object> createNotificationData(String userId, String type, String title, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put(NotificationFields.NOTIFICATION_ID, UUID.randomUUID().toString());
        data.put(NotificationFields.USER_ID, userId);
        data.put(NotificationFields.TYPE, type);
        data.put(NotificationFields.TITLE, title);
        data.put(NotificationFields.MESSAGE, message);
        data.put(NotificationFields.READ, false);
        data.put(NotificationFields.CREATED_AT, System.currentTimeMillis());
        return data;
    }
}