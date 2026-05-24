package com.example.beathouse;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.example.beathouse.databinding.ActivityMainBinding;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private androidx.activity.result.ActivityResultLauncher<Intent> createBeatLauncher;
    private String currentUserId;
    private FirebaseFirestore db;
    private static final String TAG = "MainActivity";
    private int lastSelectedTabId = R.id.nav_home;

    public void switchToTab(int tabId) {
        if (binding != null && binding.bottomNavigation != null) {
            binding.bottomNavigation.setSelectedItemId(tabId);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initActivityResultLaunchers();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseFirestore.getInstance();

        // Принудительно проверяем роль при каждом запуске
        checkUserRoleAndProceed();
    }

    private void initActivityResultLaunchers() {
        createBeatLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    int resultCode = result.getResultCode();
                    Intent data = result.getData();
                    Log.d(TAG, "📸 CREATE_BEAT_REQUEST result received - resultCode: " + resultCode);

                    if (resultCode == RESULT_CANCELED) {
                        Log.d(TAG, "❌ Beat upload cancelled, restoring tab: " + lastSelectedTabId);
                        if (binding != null && binding.bottomNavigation != null) {
                            binding.bottomNavigation.setSelectedItemId(lastSelectedTabId);
                        }
                        return;
                    }

                    if (resultCode == RESULT_OK) {
                        Log.d(TAG, "✅ Beat upload result received!");
                        handleCreateBeatSuccess(data);
                    }
                }
        );
    }

    private void handleCreateBeatSuccess(Intent data) {
        String beatId = data != null ? data.getStringExtra("BEAT_ID") : null;
        Log.d(TAG, "  Beat ID: " + beatId);

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof HomeFragment) {
            ((HomeFragment) currentFragment).refreshBeatsList();
            Log.d(TAG, "🔄 HomeFragment refreshed");
        }

        if (binding != null && binding.bottomNavigation != null) {
            lastSelectedTabId = R.id.nav_home;
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        db.collection("users").document(currentUserId)
                .update("role", "seller")
                .addOnSuccessListener(a -> Log.d(TAG, "✅ User role updated to seller"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update role: " + e.getMessage()));

        createProducerProfile();

        Toast.makeText(this, getString(R.string.upload_success), Toast.LENGTH_LONG).show();
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

                    if (!"seller".equals(role)) {
                        Log.d(TAG, "Role is not seller, redirecting to BuyerMainActivity");
                        Intent intent = new Intent(this, BuyerMainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    // Роль продавец - продолжаем загрузку MainActivity
                    initializeActivity();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check role: " + e.getMessage());
                    initializeActivity();
                });
    }

    private void initializeActivity() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        setupSellerNavigation();

        if (getSupportFragmentManager().getFragments().isEmpty()) {
            loadFragment(new HomeFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        Log.d(TAG, "MainActivity created for user: " + currentUserId);
    }

    private void setupSellerNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_upload) {
                lastSelectedTabId = binding.bottomNavigation.getSelectedItemId();
                createBeatLauncher.launch(new Intent(this, CreateBeatActivity.class));
                return true;
            } else if (itemId == R.id.nav_buyers) {
                selectedFragment = new BuyersListFragment();
            } else if (itemId == R.id.nav_sales) {
                selectedFragment = new SellerSalesFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                lastSelectedTabId = itemId;
                loadFragment(selectedFragment);
            }
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        if (fragment != null && !isFinishing() && !isDestroyed()) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            Log.d(TAG, "Fragment loaded: " + fragment.getClass().getSimpleName());
        }
    }


    private void createProducerProfile() {
        db.collection("producers").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Log.d(TAG, "Creating producer profile for: " + currentUserId);
                        db.collection("users").document(currentUserId).get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc.exists()) {
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("producerId", currentUserId);
                                        data.put("username", userDoc.getString("username"));
                                        data.put("email", userDoc.getString("email"));
                                        data.put("displayName", userDoc.getString("username"));
                                        data.put("profileImage", userDoc.getString("profileImage"));
                                        data.put("bio", userDoc.getString("bio"));
                                        data.put("rating", 0.0);
                                        data.put("totalBeats", 0);
                                        data.put("totalSales", 0);
                                        data.put("totalRevenue", 0.0);
                                        data.put("followers", 0);
                                        data.put("following", 0);
                                        data.put("verified", false);
                                        data.put("featured", false);
                                        data.put("location", userDoc.getString("location"));
                                        data.put("createdAt", System.currentTimeMillis());
                                        data.put("genres", new ArrayList<>());

                                        db.collection("producers").document(currentUserId).set(data)
                                                .addOnSuccessListener(a -> Log.d(TAG, "✅ Producer profile created"))
                                                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to create producer: " + e.getMessage()));
                                    }
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to get user data: " + e.getMessage()));
                    } else {
                        Log.d(TAG, "Producer profile already exists");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to check producer: " + e.getMessage()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_seller, menu);
        setupNotificationBadge(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_notifications) {
            startActivity(new Intent(this, NotificationsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MainActivity onResume");
    }
}