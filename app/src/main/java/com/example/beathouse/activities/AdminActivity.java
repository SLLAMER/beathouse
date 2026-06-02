package com.example.beathouse.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beathouse.BaseActivity;
import com.example.beathouse.R;
import com.example.beathouse.adapters.AdminUsersAdapter;
import com.example.beathouse.databinding.ActivityAdminBinding;
import com.example.beathouse.models.User;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends BaseActivity implements AdminUsersAdapter.OnUserActionListener {

    private ActivityAdminBinding binding;
    private AdminUsersAdapter adapter;
    private List<User> userList = new ArrayList<>();
    private FirebaseFirestore db;
    private FirestoreHelper firestoreHelper;
    private static final String TAG = "AdminActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        firestoreHelper = new FirestoreHelper();

        setupRecyclerView();
        setupSearchView();
        loadUsers();

        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new AdminUsersAdapter(userList, this, this);
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvUsers.setAdapter(adapter);
    }

    private void setupSearchView() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });
    }

    private void loadUsers() {
        db.collection("users").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        User user = User.fromMap(doc.getData());
                        if (user != null && !user.getId().equals(getCurrentUserId())) {
                            userList.add(user);
                        }
                    }
                    adapter.updateList(userList);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading users", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getCurrentUserId() {
        return com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
    }

    @Override
    public void onBlockUser(User user) {
        boolean newBlockStatus = !user.isBlocked();
        db.collection("users").document(user.getId())
                .update("isBlocked", newBlockStatus)
                .addOnSuccessListener(aVoid -> {
                    user.setBlocked(newBlockStatus);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, newBlockStatus ? getString(R.string.user_blocked) : getString(R.string.user_unblocked), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDeleteUser(User user) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account)
                .setMessage(R.string.confirm_delete_user)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    firestoreHelper.deleteAccountCompletely(user.getId(), new FirestoreHelper.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            userList.remove(user);
                            adapter.updateList(userList);
                            Toast.makeText(AdminActivity.this, R.string.user_deleted, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(AdminActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}