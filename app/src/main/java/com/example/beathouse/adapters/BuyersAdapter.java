package com.example.beathouse.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.BuyerProfileDetailActivity;
import com.example.beathouse.R;
import com.example.beathouse.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuyersAdapter extends RecyclerView.Adapter<BuyersAdapter.ViewHolder> {

    private List<User> buyers;
    private Context context;
    private FirebaseFirestore db;
    private Map<String, ListenerRegistration> listeners = new HashMap<>();

    public BuyersAdapter(List<User> buyers, Context context) {
        this.buyers = buyers;
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_buyer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User buyer = buyers.get(position);

        // ✅ Все проверки на null
        if (holder.tvUsername != null) holder.tvUsername.setText(buyer.getUsername());
        if (holder.tvEmail != null) holder.tvEmail.setText(buyer.getEmail());

        int beatsPurchased = buyer.getStats().getBeatsPurchased();
        double totalSpent = buyer.getStats().getTotalSpent();

        if (holder.tvBeatsPurchased != null)
            holder.tvBeatsPurchased.setText(beatsPurchased + " beats purchased");
        if (holder.tvTotalSpent != null)
            holder.tvTotalSpent.setText("$" + String.format("%.2f", totalSpent));

        loadBuyerStatsRealtime(buyer.getId(), holder);

        // Аватар
        if (holder.ivAvatar != null) {
            if (buyer.getProfileImage() != null && !buyer.getProfileImage().isEmpty()) {
                try {
                    byte[] decodedBytes = Base64.decode(buyer.getProfileImage(), Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    holder.ivAvatar.setImageBitmap(bitmap != null ? bitmap :
                            BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_profile_placeholder));
                } catch (Exception e) {
                    holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }

        if (holder.cardView != null) holder.cardView.setOnClickListener(v -> openBuyerProfile(buyer));
        if (holder.btnView != null) holder.btnView.setOnClickListener(v -> openBuyerProfile(buyer));
    }

    private void loadBuyerStatsRealtime(String buyerId, ViewHolder holder) {
        ListenerRegistration oldListener = listeners.get(buyerId + "_" + holder.hashCode());
        if (oldListener != null) oldListener.remove();

        ListenerRegistration listener = db.collection("users").document(buyerId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null || !doc.exists()) return;

                    // Рейтинг
                    Double rating = doc.getDouble("rating");
                    if (holder.ratingBar != null) {
                        holder.ratingBar.setRating(rating != null && rating > 0 ? rating.floatValue() : 0);
                    }
                    if (holder.tvRating != null) {
                        holder.tvRating.setText(rating != null && rating > 0 ? String.format("%.1f", rating) : "0.0");
                    }

                    // Подписчики
                    Long followers = doc.getLong("followers");
                    if (holder.tvFollowers != null) {
                        holder.tvFollowers.setText((followers != null ? followers : 0) + " followers");
                    }

                    // Статистика
                    Map<String, Object> stats = (Map<String, Object>) doc.get("stats");
                    if (stats != null) {
                        Object purchased = stats.get("beatsPurchased");
                        if (purchased instanceof Long && holder.tvBeatsPurchased != null) {
                            holder.tvBeatsPurchased.setText(purchased + " beats purchased");
                        }
                        Object spent = stats.get("totalSpent");
                        if (spent instanceof Double && holder.tvTotalSpent != null) {
                            holder.tvTotalSpent.setText("$" + String.format("%.2f", (Double) spent));
                        }
                    }
                });

        listeners.put(buyerId + "_" + holder.hashCode(), listener);
    }

    private void openBuyerProfile(User buyer) {
        Intent intent = new Intent(context, BuyerProfileDetailActivity.class);
        intent.putExtra("buyer_id", buyer.getId());
        intent.putExtra("buyer_name", buyer.getUsername());
        context.startActivity(intent);
    }

    @Override
    public int getItemCount() {
        return buyers != null ? buyers.size() : 0;
    }

    public void updateList(List<User> newList) {
        this.buyers = newList;
        notifyDataSetChanged();
    }

    public void removeAllListeners() {
        for (ListenerRegistration listener : listeners.values()) {
            if (listener != null) listener.remove();
        }
        listeners.clear();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        String keyToRemove = null;
        for (Map.Entry<String, ListenerRegistration> entry : listeners.entrySet()) {
            if (entry.getKey().contains("_" + holder.hashCode())) {
                if (entry.getValue() != null) entry.getValue().remove();
                keyToRemove = entry.getKey();
                break;
            }
        }
        if (keyToRemove != null) listeners.remove(keyToRemove);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView ivAvatar;
        TextView tvUsername, tvEmail, tvBeatsPurchased, tvTotalSpent, tvFollowers, tvRating;
        RatingBar ratingBar;
        MaterialButton btnView;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardBuyer);
            ivAvatar = itemView.findViewById(R.id.ivBuyerAvatar);
            tvUsername = itemView.findViewById(R.id.tvBuyerUsername);
            tvEmail = itemView.findViewById(R.id.tvBuyerEmail);
            tvBeatsPurchased = itemView.findViewById(R.id.tvBeatsPurchased);
            tvTotalSpent = itemView.findViewById(R.id.tvTotalSpent);
            tvFollowers = itemView.findViewById(R.id.tvFollowers);
            tvRating = itemView.findViewById(R.id.tvRating);
            ratingBar = itemView.findViewById(R.id.ratingBar);
            btnView = itemView.findViewById(R.id.btnView);
        }
    }
}