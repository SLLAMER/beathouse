package com.example.beathouse.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.R;
import com.example.beathouse.models.CartItem;
import com.example.beathouse.models.Order;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrderHistoryAdapter extends RecyclerView.Adapter<OrderHistoryAdapter.ViewHolder> {

    private List<Order> orders;
    private Context context;
    private SimpleDateFormat dateFormat;
    private FirebaseFirestore db;
    private boolean isSelectionMode = false;
    private List<String> selectedOrders = new ArrayList<>();
    private OnOrderActionListener listener;

    public interface OnOrderActionListener {
        void onLongClick(Order order, int position);
        void onSelectClick(Order order, boolean selected);
    }

    public OrderHistoryAdapter(List<Order> orders, Context context, OnOrderActionListener listener) {
        this.orders = orders;
        this.context = context;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        this.db = FirebaseFirestore.getInstance();
    }

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) {
            selectedOrders.clear();
        }
        notifyDataSetChanged();
    }

    public void setSelectedOrders(List<String> orderIds) {
        this.selectedOrders = orderIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);

        // Checkbox видимость
        holder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(selectedOrders.contains(order.getId()));

        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedOrders.add(order.getId());
            } else {
                selectedOrders.remove(order.getId());
            }
            if (listener != null) {
                listener.onSelectClick(order, isChecked);
            }
        });

        // Long click для выбора
        holder.cardView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(order, position);
            }
            return true;
        });

        // ID заказа (последние 8 символов)
        String orderId = order.getId();
        if (orderId != null && orderId.length() > 8) {
            orderId = orderId.substring(orderId.length() - 8);
        }
        holder.tvOrderId.setText("#" + orderId);

        // Дата
        String dateStr = dateFormat.format(new Date(order.getCreatedAt()));
        holder.tvDate.setText(dateStr);

        // Количество битов
        int itemCount = order.getItemCount();
        holder.tvItemCount.setText(itemCount + " " + (itemCount == 1 ? "beat" : "beats"));

        // Сумма
        holder.tvTotal.setText(order.getFormattedTotal());

        // Статус
        holder.tvStatus.setText(order.getStatusText());

        // Цвет статуса
        int statusColor;
        switch (order.getStatus()) {
            case "completed":
                statusColor = context.getResources().getColor(R.color.success);
                break;
            case "pending":
                statusColor = context.getResources().getColor(R.color.warning);
                break;
            default:
                statusColor = context.getResources().getColor(R.color.on_surface_variant);
                break;
        }
        holder.tvStatus.setTextColor(statusColor);

        // Загружаем имена продавцов
        loadProducerNames(order, holder);
    }

    private void loadProducerNames(Order order, ViewHolder holder) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            holder.tvProducerInfo.setText("Unknown seller");
            return;
        }

        // Собираем уникальные producerId
        List<String> producerIds = new ArrayList<>();
        for (CartItem item : order.getItems()) {
            if (item.getProducerId() != null && !producerIds.contains(item.getProducerId())) {
                producerIds.add(item.getProducerId());
            }
        }

        if (producerIds.isEmpty()) {
            StringBuilder sellers = new StringBuilder();
            for (int i = 0; i < Math.min(order.getItems().size(), 2); i++) {
                if (i > 0) sellers.append(", ");
                sellers.append(order.getItems().get(i).getProducerName());
            }
            if (order.getItems().size() > 2) {
                sellers.append(" +").append(order.getItems().size() - 2);
            }
            holder.tvProducerInfo.setText("From: " + sellers.toString());
            return;
        }

        fetchProducerNames(producerIds, order.getItems(), holder);
    }

    private void fetchProducerNames(List<String> producerIds, List<CartItem> items, ViewHolder holder) {
        if (producerIds.isEmpty()) return;

        List<String> names = new ArrayList<>();
        final int[] completed = {0};

        for (String producerId : producerIds) {
            db.collection("users").document(producerId).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String name = doc.getString("username");
                            if (name != null && !name.isEmpty()) {
                                names.add(name);
                            } else {
                                for (CartItem item : items) {
                                    if (producerId.equals(item.getProducerId()) && item.getProducerName() != null) {
                                        names.add(item.getProducerName());
                                        break;
                                    }
                                }
                            }
                        } else {
                            for (CartItem item : items) {
                                if (producerId.equals(item.getProducerId()) && item.getProducerName() != null) {
                                    names.add(item.getProducerName());
                                    break;
                                }
                            }
                        }
                        completed[0]++;

                        if (completed[0] >= producerIds.size()) {
                            String sellerText;
                            if (names.size() == 1) {
                                sellerText = "From: " + names.get(0);
                            } else if (names.size() == 2) {
                                sellerText = "From: " + names.get(0) + " and " + names.get(1);
                            } else {
                                sellerText = "From: " + names.get(0) + " and " + (names.size() - 1) + " others";
                            }
                            holder.tvProducerInfo.setText(sellerText);
                        }
                    })
                    .addOnFailureListener(e -> {
                        completed[0]++;
                        if (completed[0] >= producerIds.size()) {
                            StringBuilder sellers = new StringBuilder();
                            for (int i = 0; i < Math.min(items.size(), 2); i++) {
                                if (i > 0) sellers.append(", ");
                                sellers.append(items.get(i).getProducerName());
                            }
                            if (items.size() > 2) {
                                sellers.append(" +").append(items.size() - 2);
                            }
                            holder.tvProducerInfo.setText("From: " + sellers.toString());
                        }
                    });
        }
    }

    @Override
    public int getItemCount() {
        return orders != null ? orders.size() : 0;
    }

    public void updateOrders(List<Order> newOrders) {
        this.orders = newOrders;
        selectedOrders.clear();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        CheckBox cbSelect;
        TextView tvOrderId, tvDate, tvTotal, tvItemCount, tvStatus, tvProducerInfo;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardOrder);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTotal = itemView.findViewById(R.id.tvTotal);
            tvItemCount = itemView.findViewById(R.id.tvItemCount);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvProducerInfo = itemView.findViewById(R.id.tvProducerInfo);
        }
    }
}