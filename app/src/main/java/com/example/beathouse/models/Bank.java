package com.example.beathouse.models;

public class Bank {
    private String id;
    private String name;
    private int iconResId;  // Добавляем поле для иконки

    // Конструктор с иконкой
    public Bank(String id, String name, int iconResId) {
        this.id = id;
        this.name = name;
        this.iconResId = iconResId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getIconResId() {
        return iconResId;
    }
}