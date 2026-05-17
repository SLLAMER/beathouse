// models/Beat.java (ПОЛНОСТЬЮ ГОТОВАЯ ВЕРСИЯ - С ЛИЦЕНЗИЯМИ)
package com.example.beathouse.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Beat implements Serializable {

    // Типы лицензий
    public static final String LICENSE_MP3_WAV = "mp3_wav";
    public static final String LICENSE_TRACKOUT = "trackout";
    public static final String LICENSE_EXCLUSIVE = "exclusive";

    public static final String[] LICENSE_NAMES = {
            "MP3 + WAV",
            "Track Out (Stems)",
            "Exclusive Rights"
    };

    public static final String[] LICENSE_KEYS = {
            LICENSE_MP3_WAV,
            LICENSE_TRACKOUT,
            LICENSE_EXCLUSIVE
    };

    private String id;
    private String title;
    private String userName;
    private int bpm;
    private String key;
    private String genre;

    // ✅ Старые поля для обратной совместимости
    private double price;
    private boolean isFree;

    // ✅ Новые поля для цен разных лицензий
    private double priceMp3Wav;      // Цена для лицензии MP3 + WAV
    private double priceTrackOut;     // Цена для лицензии Track Out
    private double priceExclusive;    // Цена для эксклюзивной лицензии

    private String description;
    private long createdAt;
    private String userId;
    private String fullAudio;
    private String coverImage;
    private boolean isExclusive;
    private String producerId;
    private String licenseType;
    private List<String> tags;
    private String usageTerms;
    private Map<String, Object> stats;
    private String status;
    private int playCount = 0;
    private int likeCount = 0;
    private boolean hasCover = false;

    public Beat() {
        this.createdAt = System.currentTimeMillis();
        this.isExclusive = false;
        this.licenseType = LICENSE_MP3_WAV;
        this.tags = new ArrayList<>();
        this.stats = new HashMap<>();
        this.status = "active";
        this.price = 0;
        this.priceMp3Wav = 0;
        this.priceTrackOut = 0;
        this.priceExclusive = 0;
        initStats();
    }

    public Beat(String title, String userName, int bpm, String key, String genre,
                double price, boolean isFree, String description) {
        this();
        this.title = title;
        this.userName = userName;
        this.bpm = bpm;
        this.key = key;
        this.genre = genre;
        this.price = price;
        this.isFree = isFree;
        this.description = description;

        // Инициализируем цены лицензий
        this.priceMp3Wav = price;
        this.priceTrackOut = price * 2;
        this.priceExclusive = price * 5;
    }

    // ✅ Геттеры и сеттеры для новых полей
    public double getPriceMp3Wav() { return priceMp3Wav; }
    public void setPriceMp3Wav(double priceMp3Wav) {
        this.priceMp3Wav = Math.max(0, priceMp3Wav);
    }

    public double getPriceTrackOut() { return priceTrackOut; }
    public void setPriceTrackOut(double priceTrackOut) {
        this.priceTrackOut = Math.max(0, priceTrackOut);
    }

    public double getPriceExclusive() { return priceExclusive; }
    public void setPriceExclusive(double priceExclusive) {
        this.priceExclusive = Math.max(0, priceExclusive);
    }

    // ✅ Получить цену по типу лицензии
    public double getPriceByLicense(String license) {
        switch (license) {
            case LICENSE_MP3_WAV:
                return priceMp3Wav;
            case LICENSE_TRACKOUT:
                return priceTrackOut;
            case LICENSE_EXCLUSIVE:
                return priceExclusive;
            default:
                return priceMp3Wav;
        }
    }

    // ✅ Получить название лицензии
    public static String getLicenseName(String licenseKey) {
        switch (licenseKey) {
            case LICENSE_MP3_WAV:
                return "MP3 + WAV";
            case LICENSE_TRACKOUT:
                return "Track Out (Stems)";
            case LICENSE_EXCLUSIVE:
                return "Exclusive Rights";
            default:
                return "MP3 + WAV";
        }
    }

    // ✅ Форматированная цена для конкретной лицензии
    public String getFormattedPriceByLicense(String license) {
        double p = getPriceByLicense(license);
        return p == 0 ? "FREE" : "$" + String.format("%.0f", p);
    }

    // ✅ Старые геттеры/сеттеры для совместимости
    public double getPrice() { return price; }
    public void setPrice(double price) {
        this.price = Math.max(0, price);
        this.isFree = (price == 0);
    }

    public boolean isFree() { return isFree; }
    public void setFree(boolean free) { isFree = free; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title != null ? title.trim() : ""; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName != null ? userName.trim() : "Unknown Producer"; }
    public int getBpm() { return bpm; }
    public void setBpm(int bpm) { this.bpm = Math.max(60, Math.min(220, bpm)); }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key != null ? key.trim() : "Cmin"; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre != null ? genre.trim() : "Hip-Hop"; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description != null ? description.trim() : ""; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFullAudio() { return fullAudio; }
    public void setFullAudio(String fullAudio) { this.fullAudio = fullAudio; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
        this.hasCover = (coverImage != null && !coverImage.isEmpty() && coverImage.length() > 100);
    }
    public boolean isExclusive() { return isExclusive; }
    public void setExclusive(boolean exclusive) { isExclusive = exclusive; }
    public String getProducerId() { return producerId != null ? producerId : userId; }
    public void setProducerId(String producerId) { this.producerId = producerId; }
    public String getLicenseType() { return licenseType != null ? licenseType : LICENSE_MP3_WAV; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public List<String> getTags() { return tags != null ? tags : new ArrayList<>(); }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getStatus() { return status != null ? status : "active"; }
    public void setStatus(String status) { this.status = status; }
    public boolean hasCover() { return hasCover; }
    public boolean hasAudio() { return fullAudio != null && !fullAudio.isEmpty(); }

    private void initStats() {
        if (!stats.containsKey("plays")) stats.put("plays", 0);
        if (!stats.containsKey("likes")) stats.put("likes", 0);
        if (!stats.containsKey("downloads")) stats.put("downloads", 0);
        if (!stats.containsKey("revenue")) stats.put("revenue", 0.0);
    }

    public String getFormattedPrice() {
        return isFree ? "FREE" : "$" + String.format("%.0f", price);
    }

    public String getLicenseTypeFormatted() {
        return getLicenseName(licenseType);
    }

    // toMap БЕЗ fullAudio
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("beatId", id);
        map.put("title", title != null ? title.trim() : "");
        map.put("userName", userName != null ? userName.trim() : "Unknown Producer");
        map.put("bpm", bpm);
        map.put("key", key != null ? key.trim() : "Cmin");
        map.put("genre", genre != null ? genre.trim() : "Hip-Hop");

        // ✅ Сохраняем цены лицензий
        map.put("price", price);
        map.put("priceMp3Wav", priceMp3Wav);
        map.put("priceTrackOut", priceTrackOut);
        map.put("priceExclusive", priceExclusive);
        map.put("isFree", isFree);

        map.put("isExclusive", isExclusive);
        map.put("description", description != null ? description.trim() : "");
        map.put("createdAt", createdAt);
        map.put("userId", userId);
        map.put("producerId", producerId != null ? producerId : userId);
        map.put("coverImage", coverImage);
        map.put("licenseType", licenseType != null ? licenseType : LICENSE_MP3_WAV);
        map.put("tags", tags != null ? tags : new ArrayList<>());
        map.put("usageTerms", usageTerms != null ? usageTerms : "");
        map.put("status", status != null ? status : "active");
        map.put("isChunked", true);
        map.put("playCount", playCount);
        map.put("likeCount", likeCount);
        map.put("hasCover", hasCover);
        initStats();
        map.put("stats", stats);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Beat fromMap(Map<String, Object> map) {
        if (map == null) return new Beat();
        Beat beat = new Beat();
        if (map.containsKey("beatId")) beat.setId((String) map.get("beatId"));
        if (map.containsKey("id")) beat.setId((String) map.get("id"));
        if (map.containsKey("title")) beat.setTitle((String) map.get("title"));
        if (map.containsKey("userName")) beat.setUserName((String) map.get("userName"));
        if (map.containsKey("bpm")) beat.setBpm(getInt(map.get("bpm")));
        if (map.containsKey("key")) beat.setKey((String) map.get("key"));
        if (map.containsKey("genre")) beat.setGenre((String) map.get("genre"));

        // ✅ Загружаем цены лицензий
        if (map.containsKey("price")) beat.setPrice(getDouble(map.get("price")));
        if (map.containsKey("priceMp3Wav")) beat.setPriceMp3Wav(getDouble(map.get("priceMp3Wav")));
        if (map.containsKey("priceTrackOut")) beat.setPriceTrackOut(getDouble(map.get("priceTrackOut")));
        if (map.containsKey("priceExclusive")) beat.setPriceExclusive(getDouble(map.get("priceExclusive")));
        if (map.containsKey("isFree")) beat.setFree(getBool(map.get("isFree")));

        if (map.containsKey("isExclusive")) beat.setExclusive(getBool(map.get("isExclusive")));
        if (map.containsKey("description")) beat.setDescription((String) map.get("description"));
        if (map.containsKey("createdAt")) beat.setCreatedAt(getLong(map.get("createdAt")));
        if (map.containsKey("userId")) beat.setUserId((String) map.get("userId"));
        if (map.containsKey("producerId")) beat.setProducerId((String) map.get("producerId"));
        if (map.containsKey("fullAudio")) beat.setFullAudio((String) map.get("fullAudio"));
        if (map.containsKey("coverImage")) beat.setCoverImage((String) map.get("coverImage"));
        if (map.containsKey("licenseType")) beat.setLicenseType((String) map.get("licenseType"));
        if (map.containsKey("tags") && map.get("tags") instanceof List) beat.setTags((List<String>) map.get("tags"));
        if (map.containsKey("status")) beat.setStatus((String) map.get("status"));
        return beat;
    }

    private static int getInt(Object v) {
        if (v instanceof Long) return ((Long) v).intValue();
        if (v instanceof Integer) return (Integer) v;
        return 0;
    }
    private static long getLong(Object v) {
        if (v instanceof Long) return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        return 0;
    }
    private static double getDouble(Object v) {
        if (v instanceof Double) return (Double) v;
        if (v instanceof Long) return ((Long) v).doubleValue();
        return 0.0;
    }
    private static boolean getBool(Object v) {
        return v instanceof Boolean ? (Boolean) v : false;
    }
}