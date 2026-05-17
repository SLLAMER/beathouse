package com.example.beathouse.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.R;
import com.example.beathouse.models.Beat;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.utils.CartManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private List<CartItem> cartItems;
    private Context context;
    private CartManager cartManager;
    private OnCartUpdateListener updateListener;
    private Map<String, Beat> beatCache;
    private boolean showLicenseSpinner; // ✅ Флаг для отображения Spinner
    private static final String TAG = "CartAdapter";

    public interface OnCartUpdateListener {
        void onCartUpdated();
    }

    // ✅ Конструктор для корзины (с Spinner)
    public CartAdapter(List<CartItem> cartItems, Context context, OnCartUpdateListener listener) {
        this(cartItems, context, listener, true);
    }

    // ✅ НОВЫЙ КОНСТРУКТОР с флагом
    public CartAdapter(List<CartItem> cartItems, Context context, OnCartUpdateListener listener, boolean showLicenseSpinner) {
        this.cartItems = cartItems;
        this.context = context;
        this.cartManager = new CartManager(context);
        this.updateListener = listener;
        this.beatCache = new HashMap<>();
        this.showLicenseSpinner = showLicenseSpinner;
        Log.e(TAG, "CartAdapter created with " + cartItems.size() + " items, showSpinner=" + showLicenseSpinner);
    }

    public void setBeatCache(Map<String, Beat> cache) {
        if (cache != null) {
            this.beatCache.clear();
            this.beatCache.putAll(cache);
            Log.e(TAG, "✅ Beat cache updated with " + cache.size() + " beats");
            updateAllPricesFromCache();
            notifyDataSetChanged();
            Log.e(TAG, "✅ Adapter refreshed after cache update");
        }
    }

    // ✅ Обновляет цены из кэша
    public void updateAllPricesFromCache() {
        Log.e(TAG, "=== updateAllPricesFromCache CALLED ===");
        boolean pricesChanged = false;

        for (CartItem item : cartItems) {
            Beat beat = beatCache.get(item.getBeatId());
            if (beat != null) {
                double newPrice = getPriceByLicense(beat, item.getLicenseType());
                if (Math.abs(item.getPrice() - newPrice) > 0.01) {
                    double oldPrice = item.getPrice();
                    item.setPrice(newPrice);
                    pricesChanged = true;
                    Log.e(TAG, "✅ Updated price for " + item.getBeatTitle() +
                            " from $" + oldPrice + " to $" + newPrice +
                            " (license: " + item.getLicenseType() + ")");
                } else {
                    Log.e(TAG, "Price unchanged for " + item.getBeatTitle() + ": $" + item.getPrice());
                }
            } else {
                Log.e(TAG, "⚠️ Beat not in cache for " + item.getBeatTitle() + " (ID: " + item.getBeatId() + ")");
            }
        }

        if (pricesChanged && updateListener != null) {
            Log.e(TAG, "✅ Prices changed, updating listener");
            updateListener.onCartUpdated();
        } else {
            Log.e(TAG, "No price changes detected");
        }
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem item = cartItems.get(position);
        Beat beat = beatCache.get(item.getBeatId());

        Log.e(TAG, "=== onBindViewHolder position: " + position + " ===");
        Log.e(TAG, "  beatCache size: " + beatCache.size());
        Log.e(TAG, "  beat is null: " + (beat == null));
        Log.e(TAG, "  item license: " + item.getLicenseType());
        Log.e(TAG, "  item price: $" + item.getPrice());

        holder.tvTitle.setText(item.getBeatTitle());
        holder.tvProducer.setText("by " + item.getProducerName());

        // ✅ В зависимости от флага показываем Spinner или TextView
        if (showLicenseSpinner) {
            // Режим корзины - показываем Spinner
            if (holder.llLicenseSpinner != null) {
                holder.llLicenseSpinner.setVisibility(View.VISIBLE);
            }
            if (holder.tvLicenseStatic != null) {
                holder.tvLicenseStatic.setVisibility(View.GONE);
            }
            setupLicenseSpinner(holder, item, beat, position);
        } else {
            // Режим оформления заказа - показываем только текст
            if (holder.llLicenseSpinner != null) {
                holder.llLicenseSpinner.setVisibility(View.GONE);
            }
            if (holder.tvLicenseStatic != null) {
                holder.tvLicenseStatic.setVisibility(View.VISIBLE);
                holder.tvLicenseStatic.setText("License: " + item.getLicenseDisplayName());
            } else {
                // Fallback: делаем Spinner неактивным
                holder.spinnerLicense.setEnabled(false);
                holder.spinnerLicense.setVisibility(View.VISIBLE);
            }
        }

        updatePriceDisplay(holder, item);
        loadCoverImage(holder.ivCover, item.getCoverImage());

        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (cartManager.removeFromCart(item.getBeatId())) {
                cartItems.remove(pos);
                notifyItemRemoved(pos);
                notifyItemRangeChanged(pos, cartItems.size());
                Toast.makeText(context, "Removed from cart", Toast.LENGTH_SHORT).show();
                if (updateListener != null) updateListener.onCartUpdated();
                if (context instanceof com.example.beathouse.BuyerCartActivity) {
                    ((com.example.beathouse.BuyerCartActivity) context).updateCartUI();
                }
            }
        });
    }

    private void setupLicenseSpinner(CartViewHolder holder, CartItem item, Beat beat, int position) {
        String[] licenseNames = Beat.LICENSE_NAMES;
        String[] licenseKeys = Beat.LICENSE_KEYS;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, licenseNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerLicense.setAdapter(adapter);

        String currentLicense = item.getLicenseType();
        for (int i = 0; i < licenseKeys.length; i++) {
            if (licenseKeys[i].equals(currentLicense)) {
                holder.spinnerLicense.setSelection(i);
                Log.e(TAG, "  Spinner selection set to " + i + " (" + licenseNames[i] + ")");
                break;
            }
        }

        holder.spinnerLicense.setTag(position);
        holder.spinnerLicense.setEnabled(true);
        Log.e(TAG, "  Spinner enabled set to TRUE");

        holder.spinnerLicense.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int spinnerPosition, long id) {
                int currentPos = (int) holder.spinnerLicense.getTag();
                if (currentPos != position) return;

                String selectedKey = licenseKeys[spinnerPosition];
                String selectedName = licenseNames[spinnerPosition];

                if (!selectedKey.equals(item.getLicenseType())) {
                    Log.e(TAG, "🔄 License changed for " + item.getBeatTitle() + ": " + selectedName);
                    item.setLicenseType(selectedKey);

                    Beat currentBeat = beatCache.get(item.getBeatId());
                    if (currentBeat != null) {
                        double newPrice = getPriceByLicense(currentBeat, selectedKey);
                        double oldPrice = item.getPrice();
                        item.setPrice(newPrice);
                        Log.e(TAG, "  ✅ Price updated: $" + oldPrice + " → $" + newPrice);
                    } else {
                        Log.e(TAG, "  ⚠️ Beat not in cache yet, price will update when cache loads");
                        if (item.getPrice() == 0) {
                            Log.e(TAG, "  ⚠️ Price is 0, will update when cache loads");
                        }
                    }

                    updatePriceDisplay(holder, item);
                    cartManager.updateCartItemLicense(item);

                    if (updateListener != null) updateListener.onCartUpdated();
                    if (context instanceof com.example.beathouse.BuyerCartActivity) {
                        ((com.example.beathouse.BuyerCartActivity) context).updateCartUI();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private double getPriceByLicense(Beat beat, String licenseKey) {
        if (beat == null) {
            Log.e(TAG, "getPriceByLicense: beat is null!");
            return 0;
        }
        if (beat.isFree()) return 0;

        double price = 0;
        switch (licenseKey) {
            case Beat.LICENSE_MP3_WAV:
                price = beat.getPriceMp3Wav();
                break;
            case Beat.LICENSE_TRACKOUT:
                price = beat.getPriceTrackOut();
                break;
            case Beat.LICENSE_EXCLUSIVE:
                price = beat.getPriceExclusive();
                break;
            default:
                price = beat.getPriceMp3Wav();
        }
        Log.e(TAG, "getPriceByLicense: " + licenseKey + " = $" + price);
        return price;
    }

    private void updatePriceDisplay(CartViewHolder holder, CartItem item) {
        if (item.isFree()) {
            holder.tvPrice.setText("FREE");
            holder.tvPrice.setTextColor(ContextCompat.getColor(context, R.color.success));
        } else {
            double price = item.getPrice();
            if (price == (long) price) {
                holder.tvPrice.setText("$" + String.format("%d", (long) price));
            } else {
                holder.tvPrice.setText("$" + String.format("%.0f", price));
            }
            holder.tvPrice.setTextColor(ContextCompat.getColor(context, R.color.primary));
        }
        Log.e(TAG, "Price display updated: " + holder.tvPrice.getText());
    }

    private void loadCoverImage(ImageView imageView, String coverImage) {
        if (coverImage != null && !coverImage.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(coverImage, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            } catch (Exception ignored) {}
        }
        imageView.setImageResource(R.drawable.ic_music_note);
    }

    @Override
    public int getItemCount() {
        return cartItems != null ? cartItems.size() : 0;
    }

    public void updateCartItems(List<CartItem> items) {
        Log.e(TAG, "updateCartItems called with " + (items != null ? items.size() : 0) + " items");
        this.cartItems.clear();
        if (items != null) {
            this.cartItems.addAll(items);
        }
        updateAllPricesFromCache();
        notifyDataSetChanged();
    }

    public int getCacheSize() {
        return beatCache.size();
    }

    public static class CartViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvTitle, tvProducer, tvPrice, tvLicenseStatic;
        ImageButton btnRemove;
        Spinner spinnerLicense;
        LinearLayout llLicenseSpinner; // ✅ Контейнер для Spinner

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvProducer = itemView.findViewById(R.id.tv_producer);
            tvPrice = itemView.findViewById(R.id.tv_price);
            btnRemove = itemView.findViewById(R.id.btn_remove);
            spinnerLicense = itemView.findViewById(R.id.spinner_license);
            tvLicenseStatic = itemView.findViewById(R.id.tv_license_static);
            llLicenseSpinner = itemView.findViewById(R.id.ll_license_spinner);
        }
    }
}