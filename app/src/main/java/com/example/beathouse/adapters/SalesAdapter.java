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

public class SalesAdapter extends RecyclerView.Adapter<SalesAdapter.ViewHolder> {

    private List<Order> sales;
    private Context context;
    private SimpleDateFormat dateFormat;
    private FirebaseFirestore db;
    private boolean isSelectionMode = false;
    private List<String> selectedOrders = new ArrayList<>();
    private OnSaleActionListener listener;

    public interface OnSaleActionListener {
        void onLongClick(Order order, int position);
        void onSelectClick(Order order, boolean selected);
    }

    public SalesAdapter(List<Order> sales, Context context, OnSaleActionListener listener) {
        this.sales = sales;
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

    public List<Order> getCurrentList() {
        return sales;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sale, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = sales.get(position);

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

        // ✅ ИСПРАВЛЕНО: Загружаем username по buyerId
        String fallbackEmail = order.getUserEmail() != null ? order.getUserEmail() : "Unknown buyer";
        loadBuyerUsername(order.getBuyerId(), holder.tvBuyerInfo, fallbackEmail);

        // Что купил
        StringBuilder itemsText = new StringBuilder();
        List<CartItem> items = order.getItems();
        double totalAmount = 0;

        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < Math.min(items.size(), 3); i++) {
                if (i > 0) itemsText.append(", ");
                itemsText.append(items.get(i).getBeatTitle());
                totalAmount += items.get(i).getPrice();
            }
            if (items.size() > 3) {
                itemsText.append(" +").append(items.size() - 3).append(" more");
            }
        }
        holder.tvItemsPurchased.setText(itemsText.toString());

        // ✅ Цена без центов
        if (totalAmount == (long) totalAmount) {
            holder.tvAmount.setText("$" + String.format("%d", (long) totalAmount));
        } else {
            holder.tvAmount.setText("$" + String.format("%.0f", totalAmount));
        }

        // Дата
        String dateStr = dateFormat.format(new Date(order.getCreatedAt()));
        holder.tvDate.setText(dateStr);

        // Статус
        holder.tvStatus.setText(order.getStatusText());
        int statusColor;
        switch (order.getStatus()) {
            case "completed":
                statusColor = context.getResources().getColor(R.color.success);
                break;
            case "pending":
                statusColor = context.getResources().getColor(R.color.warning);
                break;
            default:
                statusColor = context.getResources().getColor(R.color.on_surface_tertiary);
                break;
        }
        holder.tvStatus.setTextColor(statusColor);
    }

    private void loadBuyerUsername(String userId, TextView textView, String fallbackEmail) {
        if (userId == null || userId.isEmpty()) {
            textView.setText(fallbackEmail);
            return;
        }

        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String username = doc.getString("username");
                        if (username != null && !username.isEmpty()) {
                            textView.setText(username);
                        } else {
                            textView.setText(fallbackEmail);
                        }
                    } else {
                        textView.setText(fallbackEmail);
                    }
                })
                .addOnFailureListener(e -> {
                    textView.setText(fallbackEmail);
                });
    }

    @Override
    public int getItemCount() {
        return sales != null ? sales.size() : 0;
    }

    public void updateSales(List<Order> newSales) {
        this.sales = newSales;
        selectedOrders.clear();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        CheckBox cbSelect;
        TextView tvBuyerInfo;
        TextView tvItemsPurchased;
        TextView tvAmount;
        TextView tvDate;
        TextView tvStatus;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardSale);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            tvBuyerInfo = itemView.findViewById(R.id.tvBuyerInfo);
            tvItemsPurchased = itemView.findViewById(R.id.tvItemsPurchased);
            tvAmount = itemView.findViewById(R.id.tvSaleAmount);
            tvDate = itemView.findViewById(R.id.tvSaleDate);
            tvStatus = itemView.findViewById(R.id.tvSaleStatus);
        }
    }
}