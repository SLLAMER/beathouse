package com.example.beathouse;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.adapters.NotificationsAdapter;
import com.example.beathouse.NotificationSettingsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.beathouse.databinding.ActivityNotificationsBinding;

public class NotificationsActivity extends BaseActivity {

    private ActivityNotificationsBinding binding;
    private NotificationsAdapter adapter;
    private List<Map<String, Object>> notificationsList;
    private ListenerRegistration notificationsListener;
    private String currentUserId;
    private boolean isSelectionMode = false;
    private List<String> selectedNotificationIds = new ArrayList<>();
    private static final String TAG = "NotificationsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupToolbar();
        initViews();
        setupRecyclerView();
        setupDeleteButton();
        loadNotificationsRealtime();

        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            // Реалтайм слушатель и так обновит данные, но для уверенности:
            if (notificationsListener != null) {
                notificationsListener.remove();
            }
            loadNotificationsRealtime();
        });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.notifications));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notifications, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_mark_all_read) {
            markAllAsRead();
            return true;
        } else if (itemId == R.id.action_notification_settings) {
            openNotificationSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        notificationsList = new ArrayList<>();
    }

    private void setupDeleteButton() {
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelectedNotifications());
    }

    private void enableSelectionMode() {
        isSelectionMode = true;
        selectedNotificationIds.clear();
        binding.btnDeleteSelected.setVisibility(View.VISIBLE);
        adapter.setSelectionMode(true);
        adapter.setSelectedNotifications(selectedNotificationIds);
        invalidateOptionsMenu();
    }

    private void disableSelectionMode() {
        isSelectionMode = false;
        selectedNotificationIds.clear();
        binding.btnDeleteSelected.setVisibility(View.GONE);
        adapter.setSelectionMode(false);
        adapter.setSelectedNotifications(selectedNotificationIds);
        invalidateOptionsMenu();
    }

    private void toggleSelection(String notificationId) {
        if (selectedNotificationIds.contains(notificationId)) {
            selectedNotificationIds.remove(notificationId);
        } else {
            selectedNotificationIds.add(notificationId);
        }
        updateSelectionMode();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionMode() {
        if (selectedNotificationIds.isEmpty()) {
            disableSelectionMode();
        } else {
            binding.btnDeleteSelected.setText(getString(R.string.delete_selected) + " (" + selectedNotificationIds.size() + ")");
        }
    }

    private void deleteSelectedNotifications() {
        if (selectedNotificationIds.isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_notifications))
                .setMessage(getString(R.string.sure_delete_notifications) + " " + selectedNotificationIds.size() + "?")
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    deleteNotificationsFromFirestore();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void deleteNotificationsFromFirestore() {
        binding.progressBar.setVisibility(View.VISIBLE);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        for (String notificationId : selectedNotificationIds) {
            batch.delete(db.collection("notifications").document(notificationId));
        }

        batch.commit().addOnSuccessListener(a -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, getString(R.string.deleted) + " " + selectedNotificationIds.size() + " " + getString(R.string.notifications).toLowerCase(), Toast.LENGTH_SHORT).show();
            disableSelectionMode();
        }).addOnFailureListener(e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRecyclerView() {
        adapter = new NotificationsAdapter(notificationsList, this, new NotificationsAdapter.OnNotificationActionListener() {
            @Override
            public void onNotificationClick(Map<String, Object> notification, int position) {
                String notificationId = (String) notification.get("notificationId");
                if (notificationId != null) {
                    markAsRead(notificationId, position);
                }
            }

            @Override
            public void onLongClick(Map<String, Object> notification, int position) {
                String notificationId = (String) notification.get("notificationId");
                if (notificationId != null) {
                    enableSelectionMode();
                    toggleSelection(notificationId);
                }
            }

            @Override
            public void onSelectClick(Map<String, Object> notification, boolean selected) {
                String notificationId = (String) notification.get("notificationId");
                if (notificationId != null) {
                    if (selected) {
                        if (!selectedNotificationIds.contains(notificationId)) {
                            selectedNotificationIds.add(notificationId);
                        }
                    } else {
                        selectedNotificationIds.remove(notificationId);
                    }
                    updateSelectionMode();
                }
            }
        });

        binding.recyclerNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNotifications.setAdapter(adapter);
    }

    private void loadNotificationsRealtime() {
        binding.progressBar.setVisibility(View.VISIBLE);

        notificationsListener = FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(this, getString(R.string.error_loading_data), Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    if (snap == null) return;

                    List<Map<String, Object>> notifications = new ArrayList<>();
                    for (var doc : snap) {
                        notifications.add(doc.getData());
                    }

                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefreshLayout.setRefreshing(false);
                        notificationsList.clear();
                        notificationsList.addAll(notifications);
                        adapter.notifyDataSetChanged();

                        if (notifications.isEmpty()) {
                            binding.emptyState.setVisibility(View.VISIBLE);
                            binding.recyclerNotifications.setVisibility(View.GONE);
                        } else {
                            binding.emptyState.setVisibility(View.GONE);
                            binding.recyclerNotifications.setVisibility(View.VISIBLE);
                        }
                    });
                });
    }

    private void markAsRead(String notificationId, int position) {
        FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(a -> {
                    if (position < notificationsList.size()) {
                        notificationsList.get(position).put("read", true);
                        adapter.notifyItemChanged(position);
                    }
                })
                .addOnFailureListener(e -> {
                    // Игнорируем ошибку
                });
    }

    // ✅ Отметить все уведомления как прочитанные
    private void markAllAsRead() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.mark_all_read))
                .setMessage(getString(R.string.confirm_mark_all_read))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                    binding.progressBar.setVisibility(View.VISIBLE);

                    FirebaseFirestore.getInstance()
                            .collection("notifications")
                            .whereEqualTo("userId", currentUserId)
                            .whereEqualTo("read", false)
                            .get()
                            .addOnSuccessListener(queryDocumentSnapshots -> {
                                if (queryDocumentSnapshots.isEmpty()) {
                                    binding.progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, getString(R.string.all_already_read), Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                WriteBatch batch = FirebaseFirestore.getInstance().batch();
                                int count = queryDocumentSnapshots.size();

                                for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                                    batch.update(doc.getReference(), "read", true);
                                }

                                batch.commit().addOnSuccessListener(a -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    // Обновляем локальный список, если эти уведомления там есть
                                    for (Map<String, Object> notification : notificationsList) {
                                        notification.put("read", true);
                                    }
                                    adapter.notifyDataSetChanged();
                                    Toast.makeText(this, getString(R.string.marked_read_count, count), Toast.LENGTH_SHORT).show();
                                }).addOnFailureListener(e -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            })
                            .addOnFailureListener(e -> {
                                binding.progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // ✅ Открыть настройки уведомлений
    private void openNotificationSettings() {
        NotificationSettingsFragment fragment = new NotificationSettingsFragment();
        fragment.show(getSupportFragmentManager(), "notification_settings");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsListener != null) {
            notificationsListener.remove();
        }
    }
}