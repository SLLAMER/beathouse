package com.example.beathouse;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.example.beathouse.databinding.ActivityBuyerBinding;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

public class BuyerMainActivity extends BaseActivity {
    private ActivityBuyerBinding binding;
    private String currentUserId;
    private FirebaseFirestore db;
    private MiniPlayer miniPlayer;
    private static final String TAG = "BuyerMainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseFirestore.getInstance();

        checkUserRoleAndProceed();
    }

    private void checkUserRoleAndProceed() {
        db.collection("users").document(currentUserId)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    String role = doc.getString("role");
                    Log.d(TAG, "Current user role: " + role);

                    if ("seller".equals(role)) {
                        Log.d(TAG, "Role is seller, redirecting to MainActivity");
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    initializeActivity();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check role: " + e.getMessage());
                    initializeActivity();
                });
    }

    private void initializeActivity() {
        binding = ActivityBuyerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        setupBuyerNavigation();

        if (getSupportFragmentManager().getFragments().isEmpty()) {
            loadFragment(new BuyerHomeFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }

    public void setMiniPlayer(MiniPlayer player) {
        this.miniPlayer = player;
    }

    public MiniPlayer getMiniPlayer() {
        return miniPlayer;
    }

    private void setupBuyerNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment f = null;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                f = new BuyerHomeFragment();
            } else if (id == R.id.nav_producers) {
                f = new ProducersFragment();
            } else if (id == R.id.nav_orders) {
                f = new BuyerOrdersFragment();
            } else if (id == R.id.nav_profile) {
                f = new BuyerProfileFragment();
            }
            if (f != null) loadFragment(f);
            return true;
        });
    }

    private void loadFragment(Fragment f) {
        if (f != null && !isFinishing() && !isDestroyed()) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_buyer, menu);
        setupNotificationBadge(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_cart) {
            startActivity(new Intent(this, BuyerCartActivity.class));
            return true;
        } else if (id == R.id.action_notifications) {
            startActivity(new Intent(this, NotificationsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
    }
}