package com.example.beathouse.adapters;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.R;
import com.example.beathouse.models.Bank;
import java.util.List;

public class BanksAdapter extends RecyclerView.Adapter<BanksAdapter.ViewHolder> {

    private List<Bank> banks;
    private OnBankClickListener listener;

    public interface OnBankClickListener {
        void onBankClick(Bank bank);
    }

    public BanksAdapter(List<Bank> banks, OnBankClickListener listener) {
        this.banks = banks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bank, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Bank bank = banks.get(position);
        holder.tvBankName.setText(bank.getName());

        // Устанавливаем иконку с правильным масштабированием
        if (bank.getIconResId() != 0) {
            Drawable drawable = ContextCompat.getDrawable(holder.itemView.getContext(), bank.getIconResId());
            if (drawable != null) {
                // Масштабируем иконку до нужного размера (40dp), сохраняя пропорции
                int size = dpToPx(holder.itemView.getContext(), 40);
                drawable.setBounds(0, 0, size, size);
                holder.ivBankIcon.setImageDrawable(drawable);
                holder.ivBankIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            }
        } else {
            holder.ivBankIcon.setImageResource(R.drawable.ic_bank);
            holder.ivBankIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBankClick(bank);
            }
        });
    }

    @Override
    public int getItemCount() {
        return banks.size();
    }

    // Метод для конвертации dp в px
    private int dpToPx(android.content.Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBankIcon;
        TextView tvBankName;

        ViewHolder(View itemView) {
            super(itemView);
            ivBankIcon = itemView.findViewById(R.id.iv_bank_icon);
            tvBankName = itemView.findViewById(R.id.tv_bank_name);
        }
    }
}