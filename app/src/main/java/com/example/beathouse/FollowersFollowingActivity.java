package com.example.beathouse;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.adapters.UsersAdapter;
import com.example.beathouse.models.User;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FollowersFollowingActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private View progressBar;

    private UsersAdapter adapter;
    private List<User> usersList = new ArrayList<>();
    private Map<String, User> usersMap = new HashMap<>();
    private FirebaseFirestore db;
    private String currentUserId;
    private String targetUserId;
    private String targetUserName;
    private int currentTab = 0;

    private ListenerRegistration followsListener;
    private List<ListenerRegistration> userListeners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_followers_following);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        targetUserId = getIntent().getStringExtra("user_id");
        targetUserName = getIntent().getStringExtra("user_name");

        if (targetUserId == null) targetUserId = currentUserId;

        initViews();
        setupToolbar();
        setupTabLayout();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        boolean showFollowButtons = !targetUserId.equals(currentUserId);
        adapter = new UsersAdapter(usersList, this, showFollowButtons);
        recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(targetUserName != null ? targetUserName : "Profile");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.followers)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.following)));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                clearListeners();
                if (currentTab == 0) {
                    loadFollowersRealtime();
                } else {
                    loadFollowingRealtime();
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Загружаем первую вкладку
        loadFollowersRealtime();
    }

    private void clearListeners() {
        if (followsListener != null) {
            followsListener.remove();
            followsListener = null;
        }
        for (ListenerRegistration reg : userListeners) {
            if (reg != null) reg.remove();
        }
        userListeners.clear();
    }

    private void loadFollowersRealtime() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        usersList.clear();
        usersMap.clear();
        adapter.updateUsers(usersList);

        followsListener = db.collection("follows")
                .whereEqualTo("followingId", targetUserId)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Error loading followers");
                        return;
                    }

                    if (snap == null || snap.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(getString(R.string.no_followers));
                        return;
                    }

                    List<String> followerIds = new ArrayList<>();
                    for (var doc : snap) {
                        String followerId = doc.getString("followerId");
                        if (followerId != null) followerIds.add(followerId);
                    }

                    if (followerIds.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(getString(R.string.no_followers));
                        return;
                    }

                    loadUsersRealtime(followerIds);
                });
    }

    private void loadFollowingRealtime() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        usersList.clear();
        usersMap.clear();
        adapter.updateUsers(usersList);

        followsListener = db.collection("follows")
                .whereEqualTo("followerId", targetUserId)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Error loading following");
                        return;
                    }

                    if (snap == null || snap.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(getString(R.string.no_following));
                        return;
                    }

                    List<String> followingIds = new ArrayList<>();
                    for (var doc : snap) {
                        String followingId = doc.getString("followingId");
                        if (followingId != null) followingIds.add(followingId);
                    }

                    if (followingIds.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(getString(R.string.no_following));
                        return;
                    }

                    loadUsersRealtime(followingIds);
                });
    }

    private void loadUsersRealtime(List<String> userIds) {
        for (ListenerRegistration reg : userListeners) {
            if (reg != null) reg.remove();
        }
        userListeners.clear();

        for (String userId : userIds) {
            ListenerRegistration reg = db.collection("users").document(userId)
                    .addSnapshotListener((doc, err) -> {
                        if (err != null || doc == null || !doc.exists()) return;

                        User user = User.fromMap(doc.getData());
                        if (user != null) {
                            user.setId(doc.getId());

                            if (!usersMap.containsKey(userId)) {
                                usersMap.put(userId, user);
                                usersList.add(user);
                            } else {
                                int index = usersList.indexOf(usersMap.get(userId));
                                if (index != -1) {
                                    usersList.set(index, user);
                                    usersMap.put(userId, user);
                                }
                            }

                            adapter.updateUsers(new ArrayList<>(usersList));

                            progressBar.setVisibility(View.GONE);
                            if (usersList.isEmpty()) {
                                tvEmpty.setVisibility(View.VISIBLE);
                                String emptyText = currentTab == 0 ? getString(R.string.no_followers) : getString(R.string.no_following);
                                tvEmpty.setText(emptyText);
                            } else {
                                tvEmpty.setVisibility(View.GONE);
                            }
                        }
                    });
            userListeners.add(reg);
        }
    }

    // ✅ Публичный метод для обновления данных (вызывается из UsersAdapter)
    public void refreshData() {
        if (currentTab == 0) {
            loadFollowersRealtime();
        } else {
            loadFollowingRealtime();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearListeners();
    }
}