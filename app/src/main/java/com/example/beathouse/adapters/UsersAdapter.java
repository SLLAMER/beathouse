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
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.activities.BuyerProfileDetailActivity;
import com.example.beathouse.activities.FollowersFollowingActivity;
import com.example.beathouse.activities.ProducerProfileActivity;
import com.example.beathouse.R;
import com.example.beathouse.models.User;
import com.example.beathouse.utils.FollowManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {

    private List<User> users;
    private Context context;
    private boolean showFollowButton;
    private FollowManager followManager;
    private String currentUserId;

    public UsersAdapter(List<User> users, Context context, boolean showFollowButton) {
        this.users = users;
        this.context = context;
        this.showFollowButton = showFollowButton;
        this.followManager = new FollowManager();

        // Получаем текущего пользователя
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);

        if (user == null) return;

        holder.tvUsername.setText(user.getUsername() != null ? user.getUsername() : "Unknown");
        holder.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");

        // Аватар
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            try {
                byte[] bytes = Base64.decode(user.getProfileImage(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    holder.ivAvatar.setImageBitmap(bitmap);
                } else {
                    holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            } catch (Exception e) {
                holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // Роль
        if (user.isSeller()) {
            holder.tvRole.setText("🎹 Producer");
            holder.tvRole.setTextColor(context.getColor(R.color.primary));
        } else {
            holder.tvRole.setText("🎵 Buyer");
            holder.tvRole.setTextColor(context.getColor(R.color.success));
        }

        // Кнопка Follow (показываем только если это не текущий пользователь и если разрешено)
        if (showFollowButton && currentUserId != null && !user.getId().equals(currentUserId)) {
            holder.btnFollow.setVisibility(View.VISIBLE);
            checkFollowStatus(user.getId(), holder.btnFollow, position);

            holder.btnFollow.setOnClickListener(v -> {
                toggleFollow(user.getId(), holder.btnFollow, position);
            });
        } else {
            holder.btnFollow.setVisibility(View.GONE);
        }

        // Клик по элементу - открываем профиль
        holder.itemView.setOnClickListener(v -> {
            if (user.isSeller()) {
                Intent intent = new Intent(context, ProducerProfileActivity.class);
                intent.putExtra("producer_id", user.getId());
                intent.putExtra("producer_name", user.getUsername());
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(context, BuyerProfileDetailActivity.class);
                intent.putExtra("buyer_id", user.getId());
                intent.putExtra("buyer_name", user.getUsername());
                context.startActivity(intent);
            }
        });
    }

    private void checkFollowStatus(String userId, MaterialButton button, int position) {
        if (currentUserId == null) {
            button.setText("Follow");
            button.setEnabled(true);
            return;
        }

        followManager.isFollowing(currentUserId, userId, new FollowManager.FollowCallback() {
            @Override
            public void onSuccess(boolean following) {
                button.setText(following ? "Following" : "Follow");
                button.setEnabled(true);
            }
            @Override
            public void onError(String error) {
                button.setText("Follow");
                button.setEnabled(true);
                Toast.makeText(context, "Error checking follow status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleFollow(String userId, MaterialButton button, int position) {
        if (currentUserId == null) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        button.setEnabled(false);

        followManager.isFollowing(currentUserId, userId, new FollowManager.FollowCallback() {
            @Override
            public void onSuccess(boolean following) {
                if (following) {
                    // ✅ Отписаться - используем unfollowUser
                    followManager.unfollowUser(currentUserId, userId, new FollowManager.FollowCallback() {
                        @Override
                        public void onSuccess(boolean result) {
                            button.setText("Follow");
                            button.setEnabled(true);
                            updateUserInList(userId, false);
                            // Обновляем экран FollowersFollowingActivity
                            refreshParentActivity();
                        }
                        @Override
                        public void onError(String error) {
                            button.setEnabled(true);
                            Toast.makeText(context, "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    // ✅ Подписаться - используем followUser
                    followManager.followUser(currentUserId, userId, new FollowManager.FollowCallback() {
                        @Override
                        public void onSuccess(boolean result) {
                            button.setText("Following");
                            button.setEnabled(true);
                            updateUserInList(userId, true);
                            // Обновляем экран FollowersFollowingActivity
                            refreshParentActivity();
                        }
                        @Override
                        public void onError(String error) {
                            button.setEnabled(true);
                            Toast.makeText(context, "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
            @Override
            public void onError(String error) {
                button.setEnabled(true);
                Toast.makeText(context, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUserInList(String userId, boolean isFollowing) {
        // Обновляем статус подписки в списке (для отображения)
        for (User user : users) {
            if (user.getId().equals(userId)) {
                // Можно добавить поле isFollowing в модель User, если нужно
                break;
            }
        }
    }

    private void refreshParentActivity() {
        if (context instanceof FollowersFollowingActivity) {
            ((FollowersFollowingActivity) context).refreshData();
        }
    }

    @Override
    public int getItemCount() {
        return users != null ? users.size() : 0;
    }

    public void updateUsers(List<User> newUsers) {
        this.users = newUsers;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername, tvEmail, tvRole;
        MaterialButton btnFollow;

        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvRole = itemView.findViewById(R.id.tvRole);
            btnFollow = itemView.findViewById(R.id.btnFollow);
        }
    }
}