package com.example.beathouse.models;

import java.util.Map;

public class Message {
    private String id;
    private String senderId;
    private String receiverId;
    private String text;
    private long timestamp;
    private boolean read;

    public Message() {}

    public Message(String senderId, String receiverId, String text) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("senderId", senderId);
        map.put("receiverId", receiverId);
        map.put("text", text);
        map.put("timestamp", timestamp);
        map.put("read", read);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Message fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Message message = new Message();
        if (map.containsKey("senderId")) message.setSenderId((String) map.get("senderId"));
        if (map.containsKey("receiverId")) message.setReceiverId((String) map.get("receiverId"));
        if (map.containsKey("text")) message.setText((String) map.get("text"));
        if (map.containsKey("timestamp")) message.setTimestamp((Long) map.get("timestamp"));
        if (map.containsKey("read")) message.setRead((Boolean) map.get("read"));
        return message;
    }
}