package com.example.beathouse.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.R;
import com.example.beathouse.models.User;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class AdminUsersAdapter extends RecyclerView.Adapter<AdminUsersAdapter.ViewHolder> {

    private List<User> users;
    private List<User> usersFull;
    private Context context;
    private OnUserActionListener listener;

    public interface OnUserActionListener {
        void onBlockUser(User user);
        void onDeleteUser(User user);
    }

    public AdminUsersAdapter(List<User> users, Context context, OnUserActionListener listener) {
        this.users = users;
        this.usersFull = new ArrayList<>(users);
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        if (user == null) return;

        holder.tvUsername.setText(user.getUsername());
        holder.tvEmail.setText(user.getEmail());
        holder.tvRole.setText(user.getRoleDisplayName());

        // Status
        if (user.isBlocked()) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(context.getString(R.string.status_blocked));
            holder.btnBlock.setText(context.getString(R.string.unblock));
            holder.btnBlock.setTextColor(context.getColor(R.color.success));
        } else {
            holder.tvStatus.setVisibility(View.GONE);
            holder.btnBlock.setText(context.getString(R.string.block));
            holder.btnBlock.setTextColor(context.getColor(R.color.warning));
        }

        // Avatar
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            try {
                byte[] bytes = Base64.decode(user.getProfileImage(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                holder.ivAvatar.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        holder.btnBlock.setOnClickListener(v -> listener.onBlockUser(user));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteUser(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public void filter(String text) {
        users.clear();
        if (text.isEmpty()) {
            users.addAll(usersFull);
        } else {
            text = text.toLowerCase();
            for (User user : usersFull) {
                if (user.getUsername().toLowerCase().contains(text) ||
                        user.getEmail().toLowerCase().contains(text)) {
                    users.add(user);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void updateList(List<User> newList) {
        this.users = new ArrayList<>(newList);
        this.usersFull = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername, tvEmail, tvRole, tvStatus;
        MaterialButton btnBlock, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvRole = itemView.findViewById(R.id.tvRole);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnBlock = itemView.findViewById(R.id.btnBlock);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}