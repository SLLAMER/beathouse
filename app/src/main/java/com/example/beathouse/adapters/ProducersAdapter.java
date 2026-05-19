// adapters/ProducersAdapter.java (ПОЛНОСТЬЮ ГОТОВАЯ ВЕРСИЯ - С РЕЙТИНГОМ И ПОДПИСЧИКАМИ)
package com.example.beathouse.adapters;

import android.content.Context;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.R;
import com.example.beathouse.models.Producer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class ProducersAdapter extends RecyclerView.Adapter<ProducersAdapter.ViewHolder> {

    private List<Producer> producers;
    private Context context;
    private OnProducerClickListener listener;

    public interface OnProducerClickListener {
        void onProducerClick(Producer producer);
    }

    public ProducersAdapter(List<Producer> producers, Context context, OnProducerClickListener listener) {
        this.producers = producers;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_producer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Producer producer = producers.get(position);

        // Имя продюсера
        holder.tvProducerName.setText(producer.getDisplayName() != null && !producer.getDisplayName().isEmpty()
                ? producer.getDisplayName() : producer.getUsername());

        // ✅ Слушатель для рейтинга в реальном времени
        loadProducerStatsRealtime(producer.getProducerId(), holder);

        // ✅ Статистика
        String stats = producer.getTotalBeats() + " beats • " + producer.getTotalSales() + " sales";
        holder.tvProducerStats.setText(stats);

        // Верификация
        holder.ivVerified.setVisibility(producer.isVerified() ? View.VISIBLE : View.GONE);

        // Аватар
        loadAvatar(holder.ivProducerAvatar, producer.getProfileImage());

        // ✅ Кнопка Follow / View
        holder.btnFollow.setText("View");
        holder.btnFollow.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProducerClick(producer);
            }
        });

        // Клик по карточке
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProducerClick(producer);
            }
        });
    }

    private void loadAvatar(ImageView imageView, String profileImage) {
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(profileImage, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            } catch (Exception e) {
                // Fallback to placeholder
            }
        }
        imageView.setImageResource(R.drawable.ic_profile_placeholder);
    }

    @Override
    public int getItemCount() {
        return producers != null ? producers.size() : 0;
    }

    private void loadProducerStatsRealtime(String producerId, ViewHolder holder) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("producers").document(producerId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null || !doc.exists()) return;
                    Double rating = doc.getDouble("rating");
                    Long followers = doc.getLong("followers");
                    if (holder.ratingBar != null) {
                        holder.ratingBar.setRating(rating != null ? rating.floatValue() : 0);
                    }
                    if (holder.tvRating != null) {
                        holder.tvRating.setText(String.format("%.1f", rating != null ? rating : 0.0));
                    }
                    if (holder.tvFollowers != null) {
                        holder.tvFollowers.setText((followers != null ? followers : 0) + " followers");
                    }
                });
    }

    public void updateProducers(List<Producer> newProducers) {
        this.producers = newProducers;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        de.hdodenhof.circleimageview.CircleImageView ivProducerAvatar;
        ImageView ivVerified;
        TextView tvProducerName;
        TextView tvProducerStats;
        TextView tvFollowers;  // ✅ ДОБАВЛЕНО
        TextView tvRating;     // ✅ ДОБАВЛЕНО
        RatingBar ratingBar;
        MaterialButton btnFollow;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardProducer);
            ivProducerAvatar = itemView.findViewById(R.id.ivProducerAvatar);
            ivVerified = itemView.findViewById(R.id.ivVerified);
            tvProducerName = itemView.findViewById(R.id.tvProducerName);
            tvProducerStats = itemView.findViewById(R.id.tvProducerStats);
            tvFollowers = itemView.findViewById(R.id.tvFollowers);  // ✅ ДОБАВЛЕНО
            tvRating = itemView.findViewById(R.id.tvRating);        // ✅ ДОБАВЛЕНО
            ratingBar = itemView.findViewById(R.id.ratingBar);
            btnFollow = itemView.findViewById(R.id.btnFollow);
        }
    }
}