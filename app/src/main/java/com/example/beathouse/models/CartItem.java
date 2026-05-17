package com.example.beathouse.models;

import java.util.HashMap;
import java.util.Map;

public class CartItem {
    private String id;
    private String beatId;
    private String beatTitle;
    private String producerName;
    private String producerId;
    private double price;
    private boolean isFree;
    private String coverImage;
    private long addedAt;
    private String licenseType;

    public CartItem() {}

    public CartItem(Beat beat) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.beatId = beat.getId();
        this.beatTitle = beat.getTitle();
        this.producerName = beat.getUserName();
        this.producerId = beat.getProducerId();
        this.isFree = beat.isFree();
        this.coverImage = beat.getCoverImage();
        this.addedAt = System.currentTimeMillis();
        this.licenseType = Beat.LICENSE_MP3_WAV;
        // ✅ Берем цену для лицензии по умолчанию
        this.price = beat.isFree() ? 0 : beat.getPriceMp3Wav();
    }

    public int getBpm() {
        return 120;
    }

    public String getKey() {
        return "C Minor";
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBeatId() { return beatId; }
    public void setBeatId(String beatId) { this.beatId = beatId; }

    public String getBeatTitle() { return beatTitle; }
    public void setBeatTitle(String beatTitle) { this.beatTitle = beatTitle; }

    public String getProducerName() { return producerName; }
    public void setProducerName(String producerName) { this.producerName = producerName; }

    public String getProducerId() { return producerId; }
    public void setProducerId(String producerId) { this.producerId = producerId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public boolean isFree() { return isFree; }
    public void setFree(boolean free) { isFree = free; }

    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }

    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }

    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    // ✅ Обновить цену на основе текущей лицензии (нужен доступ к биту)
    public void updatePriceFromBeat(Beat beat) {
        if (beat == null) return;
        if (isFree || beat.isFree()) {
            this.price = 0;
            this.isFree = true;
            return;
        }

        switch (licenseType) {
            case Beat.LICENSE_MP3_WAV:
                this.price = beat.getPriceMp3Wav();
                break;
            case Beat.LICENSE_TRACKOUT:
                this.price = beat.getPriceTrackOut();
                break;
            case Beat.LICENSE_EXCLUSIVE:
                this.price = beat.getPriceExclusive();
                break;
            default:
                this.price = beat.getPriceMp3Wav();
        }
    }

    public String getLicenseDisplayName() {
        if (licenseType == null) return "MP3+WAV";
        switch (licenseType) {
            case Beat.LICENSE_MP3_WAV:
                return "MP3+WAV";
            case Beat.LICENSE_TRACKOUT:
                return "Track Out";
            case Beat.LICENSE_EXCLUSIVE:
                return "Exclusive";
            default:
                return "MP3+WAV";
        }
    }

    public String getFullTitleWithLicense() {
        return beatTitle + " (" + getLicenseDisplayName() + ")";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("beatId", beatId);
        map.put("beatTitle", beatTitle);
        map.put("producerName", producerName);
        map.put("producerId", producerId);
        map.put("price", price);
        map.put("isFree", isFree);
        map.put("coverImage", coverImage);
        map.put("addedAt", addedAt);
        map.put("licenseType", licenseType != null ? licenseType : Beat.LICENSE_MP3_WAV);
        return map;
    }

    public static CartItem fromMap(Map<String, Object> map) {
        CartItem item = new CartItem();
        item.setId((String) map.get("id"));
        item.setBeatId((String) map.get("beatId"));
        item.setBeatTitle((String) map.get("beatTitle"));
        item.setProducerName((String) map.get("producerName"));
        item.setProducerId((String) map.get("producerId"));

        if (map.containsKey("licenseType")) {
            item.setLicenseType((String) map.get("licenseType"));
        } else {
            item.setLicenseType(Beat.LICENSE_MP3_WAV);
        }

        if (map.get("price") instanceof Double) {
            item.setPrice((Double) map.get("price"));
        } else if (map.get("price") instanceof Long) {
            item.setPrice(((Long) map.get("price")).doubleValue());
        }

        if (map.get("isFree") instanceof Boolean) {
            item.setFree((Boolean) map.get("isFree"));
        }

        item.setCoverImage((String) map.get("coverImage"));

        if (map.get("addedAt") instanceof Long) {
            item.setAddedAt((Long) map.get("addedAt"));
        }

        return item;
    }
}