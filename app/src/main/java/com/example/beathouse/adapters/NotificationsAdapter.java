// adapters/NotificationsAdapter.java
package com.example.beathouse.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.BuyerProfileDetailActivity;
import com.example.beathouse.ChatActivity;
import com.example.beathouse.ProducerProfileActivity;
import com.example.beathouse.R;
import android.widget.CheckBox;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

    private List<Map<String, Object>> notifications;
    private Context context;
    private OnNotificationActionListener listener;
    private SimpleDateFormat dateFormat;
    private boolean isSelectionMode = false;
    private List<String> selectedNotifications;

    public interface OnNotificationActionListener {
        void onNotificationClick(Map<String, Object> notification, int position);
        void onLongClick(Map<String, Object> notification, int position);
        void onSelectClick(Map<String, Object> notification, boolean selected);
    }

    public NotificationsAdapter(List<Map<String, Object>> notifications, Context context,
                                OnNotificationActionListener listener) {
        this.notifications = notifications;
        this.context = context;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        this.selectedNotifications = new ArrayList<>();
    }

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        notifyDataSetChanged();
    }

    public void setSelectedNotifications(List<String> selectedIds) {
        this.selectedNotifications = selectedIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> notification = notifications.get(position);
        String notificationId = (String) notification.get("notificationId");

        String title = getString(notification, "title", "Notification");
        String message = getString(notification, "message", "");
        String type = getString(notification, "type", "system");
        boolean read = getBoolean(notification, "read", false);
        long createdAt = getLong(notification, "createdAt", System.currentTimeMillis());

        holder.tvTitle.setText(title);
        holder.tvMessage.setText(message);
        holder.tvTime.setText(dateFormat.format(new Date(createdAt)));

        // Checkbox видимость
        holder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(selectedNotifications.contains(notificationId));

        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onSelectClick(notification, isChecked);
            }
        });

        // Визуальное отличие прочитанных/непрочитанных
        if (read) {
            holder.cardView.setAlpha(0.6f);
        } else {
            holder.cardView.setAlpha(1.0f);
        }

        // Иконка по типу уведомления
        switch (type) {
            case "purchase":
                holder.tvType.setText("🛒");
                break;
            case "sale":
                holder.tvType.setText("💰");
                break;
            case "review":
                holder.tvType.setText("⭐");
                break;
            case "message":
                holder.tvType.setText("💬");
                break;
            case "follow":
                holder.tvType.setText("👤");
                break;
            default:
                holder.tvType.setText("📢");
        }

        holder.cardView.setOnClickListener(v -> {
            if (isSelectionMode) {
                // ✅ Для режима выбора не используем setChecked напрямую на чекбоксе,
                // так как это вызовет OnCheckedChangeListener в процессе клика.
                // Вместо этого пробрасываем событие в Activity.
                if (listener != null) {
                    listener.onSelectClick(notification, !selectedNotifications.contains(notificationId));
                }
            } else {
                // Сначала отмечаем как прочитанное
                if (listener != null) {
                    listener.onNotificationClick(notification, position);
                }

                // Затем переходим по типу уведомления
                handleNotificationClick(notification);
            }
        });

        // Long click для выбора
        holder.cardView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(notification, position);
            }
            return true;
        });
    }

    // Обработка клика по уведомлению
    private void handleNotificationClick(Map<String, Object> notification) {
        String type = getString(notification, "type", "system");
        String title = getString(notification, "title", "Notification");

        // ✅ ДИАГНОСТИКА
        android.util.Log.d("NOTIFICATION_DEBUG", "=== CLICKED ===");
        android.util.Log.d("NOTIFICATION_DEBUG", "Type: " + type);
        android.util.Log.d("NOTIFICATION_DEBUG", "Title: " + title);
        android.util.Log.d("NOTIFICATION_DEBUG", "All keys: " + notification.keySet());
        android.util.Log.d("NOTIFICATION_DEBUG", "senderId: " + getString(notification, "senderId", "null"));
        android.util.Log.d("NOTIFICATION_DEBUG", "senderName: " + getString(notification, "senderName", "null"));
        switch (type) {
            case "message":
                // Переход в чат
                String senderId = getString(notification, "senderId", null);
                String senderName = getString(notification, "senderName", null);

                if (senderId == null) {
                    // Пробуем извлечь имя из title
                    if (title.contains("New message from ")) {
                        senderName = title.replace("New message from ", "");
                    }
                }

                if (senderId != null) {
                    Intent chatIntent = new Intent(context, ChatActivity.class);
                    chatIntent.putExtra("user_id", senderId);
                    chatIntent.putExtra("user_name", senderName != null ? senderName : "User");
                    context.startActivity(chatIntent);
                } else {
                    Toast.makeText(context, "Cannot open chat: user ID not found", Toast.LENGTH_SHORT).show();
                }
                break;

            case "follow":
                // Переход в профиль пользователя, который подписался
                String followerId = getString(notification, "senderId", null);
                String followerName = getString(notification, "senderName", null);

                // Если нет senderName, пробуем извлечь из title
                if (followerName == null && title.contains(" started following you")) {
                    followerName = title.replace(" started following you", "");
                }

                if (followerId != null) {
                    openUserProfile(followerId, followerName);
                } else {
                    Toast.makeText(context, "Cannot open profile: user ID not found", Toast.LENGTH_SHORT).show();
                }
                break;

            case "sale":
                Toast.makeText(context, "New sale! Check your sales.", Toast.LENGTH_SHORT).show();
                break;

            case "purchase":
                Toast.makeText(context, "Purchase completed! Check your orders.", Toast.LENGTH_SHORT).show();
                break;

            case "review":
                Toast.makeText(context, "New review! Check your rating.", Toast.LENGTH_SHORT).show();
                break;

            default:
                Toast.makeText(context, title, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // Открытие профиля пользователя (универсально для покупателя и продавца)
    private void openUserProfile(String userId, String userName) {
        if (userId == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String role = doc.getString("role");
                        boolean isSeller = "seller".equals(role);

                        Intent intent;
                        if (isSeller) {
                            intent = new Intent(context, ProducerProfileActivity.class);
                            intent.putExtra("producer_id", userId);
                            intent.putExtra("producer_name", userName != null ? userName : "Producer");
                        } else {
                            intent = new Intent(context, BuyerProfileDetailActivity.class);
                            intent.putExtra("buyer_id", userId);
                            intent.putExtra("buyer_name", userName != null ? userName : "User");
                        }
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Error loading user profile", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public int getItemCount() {
        return notifications != null ? notifications.size() : 0;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return defaultValue;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        CheckBox cbSelect;
        TextView tvType, tvTitle, tvMessage, tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardNotification);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            tvType = itemView.findViewById(R.id.tvType);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}