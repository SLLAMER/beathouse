package com.example.beathouse.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.beathouse.models.Beat;
import com.example.beathouse.models.CartItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CartManager {
    private static final String TAG = "CartManager";
    private static final String CART_PREFS = "cart_preferences";
    private static final String CART_ITEMS_KEY = "cart_items";

    private SharedPreferences prefs;
    private Gson gson;

    public CartManager(Context context) {
        prefs = context.getSharedPreferences(CART_PREFS, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // ✅ Получить отображаемое имя лицензии
    private String getLicenseDisplayName(String licenseKey) {
        if (licenseKey == null) return "MP3+WAV";
        switch (licenseKey) {
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

    // ✅ Добавление в корзину с выбором лицензии
    public boolean addToCartWithLicense(Beat beat, String licenseType, double price) {
        if (isInCart(beat.getId())) {
            Log.d(TAG, "Beat already in cart: " + beat.getTitle());
            return false;
        }

        CartItem cartItem = new CartItem(beat);
        cartItem.setLicenseType(licenseType);
        cartItem.setPrice(price);
        // Не добавляем лицензию в название, она будет отображаться отдельно
        cartItem.setBeatTitle(beat.getTitle());

        addToCart(cartItem);
        Log.d(TAG, "Added to cart with license: " + beat.getTitle() + " - " + getLicenseDisplayName(licenseType) + " - $" + price);
        return true;
    }

    // ✅ Добавление в корзину с лицензией по умолчанию (MP3+WAV)
    public boolean addToCartWithDefaultLicense(Beat beat) {
        if (isInCart(beat.getId())) {
            Log.d(TAG, "Beat already in cart: " + beat.getTitle());
            return false;
        }

        CartItem cartItem = new CartItem(beat);
        cartItem.setLicenseType(Beat.LICENSE_MP3_WAV);
        cartItem.setPrice(beat.getPriceMp3Wav());
        cartItem.setBeatTitle(beat.getTitle());

        addToCart(cartItem);
        Log.d(TAG, "Added to cart with default license: " + beat.getTitle() + " - MP3+WAV - $" + beat.getPriceMp3Wav());
        return true;
    }

    public boolean addToCart(Beat beat) {
        return addToCartWithDefaultLicense(beat);
    }

    public void addToCart(CartItem item) {
        List<CartItem> cartItems = getCartItems();
        cartItems.add(item);
        saveCartItems(cartItems);
        Log.d(TAG, "Added to cart: " + item.getBeatTitle());
    }

    // ✅ Обновить лицензию товара в корзине
    public void updateCartItemLicense(CartItem updatedItem) {
        List<CartItem> cartItems = getCartItems();
        for (int i = 0; i < cartItems.size(); i++) {
            if (cartItems.get(i).getBeatId().equals(updatedItem.getBeatId())) {
                cartItems.set(i, updatedItem);
                saveCartItems(cartItems);
                Log.d(TAG, "Updated license for " + updatedItem.getBeatTitle() + " to " + updatedItem.getLicenseDisplayName() + " - $" + updatedItem.getPrice());
                break;
            }
        }
    }

    public boolean isInCart(String beatId) {
        List<CartItem> cartItems = getCartItems();
        for (CartItem item : cartItems) {
            if (item.getBeatId().equals(beatId)) {
                return true;
            }
        }
        return false;
    }

    public boolean removeFromCart(String beatId) {
        List<CartItem> cartItems = getCartItems();
        for (int i = 0; i < cartItems.size(); i++) {
            if (cartItems.get(i).getBeatId().equals(beatId)) {
                cartItems.remove(i);
                saveCartItems(cartItems);
                Log.d(TAG, "Removed from cart: " + beatId);
                return true;
            }
        }
        return false;
    }

    public List<CartItem> getCartItems() {
        String json = prefs.getString(CART_ITEMS_KEY, null);
        if (json == null) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<CartItem>>(){}.getType();
        List<CartItem> items = gson.fromJson(json, type);
        return items != null ? items : new ArrayList<>();
    }

    public List<CartItem> getPaidItems() {
        List<CartItem> allItems = getCartItems();
        List<CartItem> paidItems = new ArrayList<>();
        for (CartItem item : allItems) {
            if (!item.isFree()) {
                paidItems.add(item);
            }
        }
        return paidItems;
    }

    public int getCartItemCount() {
        return getCartItems().size();
    }

    public double getCartTotal() {
        double total = 0;
        for (CartItem item : getCartItems()) {
            if (!item.isFree()) {
                total += item.getPrice();
            }
        }
        return total;
    }

    public String getFormattedTotal() {
        double total = getCartTotal();
        if (total == (long) total) {
            return "$" + String.format("%d", (long) total);
        }
        return "$" + String.format("%.0f", total);
    }

    public void clearCart() {
        saveCartItems(new ArrayList<>());
        Log.d(TAG, "Cart cleared");
    }

    private void saveCartItems(List<CartItem> items) {
        String json = gson.toJson(items);
        prefs.edit().putString(CART_ITEMS_KEY, json).apply();
    }
}