package com.example.beathouse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.databinding.ActivityLoginBinding;
import com.example.beathouse.utils.UserSyncHelper;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private static final String TAG = "LoginActivity";
    private boolean isLoading = false;
    private Handler timeoutHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Проверяем, не авторизован ли пользователь уже
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Перенаправляем без задержки
            checkUserRoleAndNavigate(currentUser.getUid());
            return;
        }

        setupLoginMode();
        setupClickListeners();
    }

    private void setupLoginMode() {
        binding.tvSubtitle.setText(getString(R.string.sign_in_to_continue));
        binding.btnAction.setText(getString(R.string.sign_in));
        binding.roleSelector.setVisibility(View.GONE);
        binding.usernameLayout.setVisibility(View.GONE);
        binding.forgotPassword.setVisibility(View.VISIBLE);
        binding.switchMode.setText(getString(R.string.dont_have_account));
        binding.tvSwitchAction.setText(getString(R.string.create_account));
    }

    private void setupClickListeners() {
        binding.btnAction.setOnClickListener(v -> {
            if (isLoading) return;

            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (email.isEmpty()) {
                binding.etEmail.setError(getString(R.string.email_required));
                return;
            }
            if (password.isEmpty()) {
                binding.etPassword.setError(getString(R.string.password_required));
                return;
            }
            if (password.length() < 6) {
                binding.etPassword.setError(getString(R.string.password_min_length));
                return;
            }

            boolean isRegistrationMode = binding.usernameLayout.getVisibility() == View.VISIBLE;

            if (isRegistrationMode) {
                String username = binding.etUsername.getText().toString().trim();
                if (username.isEmpty()) {
                    binding.etUsername.setError(getString(R.string.username_required));
                    return;
                }
                String role = binding.rbSeller.isChecked() ? "seller" : "buyer";
                registerUser(email, password, username, role);
            } else {
                loginUser(email, password);
            }
        });

        binding.tvSwitchAction.setOnClickListener(v -> toggleMode());

        binding.forgotPassword.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                binding.etEmail.setError(getString(R.string.enter_email_first));
                return;
            }
            resetPassword(email);
        });
    }

    private void toggleMode() {
        boolean isLoginMode = binding.usernameLayout.getVisibility() == View.GONE;

        if (isLoginMode) {
            binding.usernameLayout.setVisibility(View.VISIBLE);
            binding.roleSelector.setVisibility(View.VISIBLE);
            binding.forgotPassword.setVisibility(View.GONE);
            binding.tvSubtitle.setText(getString(R.string.create_new_account));
            binding.tvSwitchAction.setText(getString(R.string.sign_in));
            binding.switchMode.setText(getString(R.string.already_have_account));
            binding.btnAction.setText(getString(R.string.sign_up));
            binding.rbBuyer.setChecked(true);
        } else {
            binding.usernameLayout.setVisibility(View.GONE);
            binding.roleSelector.setVisibility(View.GONE);
            binding.forgotPassword.setVisibility(View.VISIBLE);
            binding.tvSubtitle.setText(getString(R.string.sign_in_to_continue));
            binding.tvSwitchAction.setText(getString(R.string.create_account));
            binding.switchMode.setText(getString(R.string.dont_have_account));
            binding.btnAction.setText(getString(R.string.sign_in));
        }
    }

    private void loginUser(String email, String password) {
        showLoading(true);
        isLoading = true;

        timeoutHandler.postDelayed(() -> {
            if (isLoading) {
                showLoading(false);
                isLoading = false;
                Toast.makeText(this, getString(R.string.connection_timeout), Toast.LENGTH_LONG).show();
            }
        }, 10000);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    isLoading = false;

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            checkUserRoleAndNavigate(user.getUid());
                        }
                    } else {
                        showLoading(false);
                        String error = task.getException() != null ? task.getException().getMessage() : getString(R.string.login_failed);
                        if (error.contains("There is no user record")) {
                            Toast.makeText(this, getString(R.string.no_account), Toast.LENGTH_LONG).show();
                        } else if (error.contains("password is invalid")) {
                            Toast.makeText(this, getString(R.string.invalid_password), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, getString(R.string.error_prefix) + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void registerUser(String email, String password, String username, String role) {
        showLoading(true);
        isLoading = true;

        timeoutHandler.postDelayed(() -> {
            if (isLoading) {
                showLoading(false);
                isLoading = false;
                Toast.makeText(this, getString(R.string.connection_timeout), Toast.LENGTH_LONG).show();
            }
        }, 10000);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    isLoading = false;

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToFirestore(user.getUid(), email, username, role);
                        }
                    } else {
                        showLoading(false);
                        String error = task.getException() != null ? task.getException().getMessage() : getString(R.string.registration_failed);
                        if (error.contains("email address is already in use")) {
                            Toast.makeText(this, getString(R.string.email_in_use), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, getString(R.string.error_prefix) + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserToFirestore(String userId, String email, String username, String role) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", userId);
        userData.put("email", email);
        userData.put("username", username);
        userData.put("fullName", username);
        userData.put("role", role);
        userData.put("bio", "");
        userData.put("profileImage", "");
        userData.put("location", "");
        userData.put("socialInstagram", "");
        userData.put("socialTelegram", "");
        userData.put("socialVk", "");
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("emailVerified", true);
        userData.put("phone", "");
        userData.put("following", 0);
        userData.put("followers", 0);
        userData.put("rating", 0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSpent", 0.0);
        stats.put("totalEarned", 0.0);
        stats.put("beatsPurchased", 0);
        stats.put("beatsSold", 0);
        userData.put("stats", stats);

        db.collection("users").document(userId).set(userData)
                .addOnSuccessListener(a -> {
                    if ("seller".equals(role)) {
                        createProducerProfile(userId, username, email);
                    } else {
                        showLoading(false);
                        Toast.makeText(this, getString(R.string.account_created), Toast.LENGTH_LONG).show();
                        toggleMode();
                        binding.etEmail.setText("");
                        binding.etPassword.setText("");
                        binding.etUsername.setText("");
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void createProducerProfile(String userId, String username, String email) {
        Map<String, Object> producerData = new HashMap<>();
        producerData.put("producerId", userId);
        producerData.put("username", username);
        producerData.put("email", email);
        producerData.put("displayName", username);
        producerData.put("profileImage", "");
        producerData.put("bio", "");
        producerData.put("rating", 0.0);
        producerData.put("totalBeats", 0);
        producerData.put("totalSales", 0);
        producerData.put("totalRevenue", 0.0);
        producerData.put("followers", 0);
        producerData.put("following", 0);
        producerData.put("verified", false);
        producerData.put("featured", false);
        producerData.put("location", "");
        producerData.put("createdAt", System.currentTimeMillis());
        producerData.put("genres", new ArrayList<>());

        db.collection("producers").document(userId).set(producerData)
                .addOnSuccessListener(a -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.seller_account_created), Toast.LENGTH_LONG).show();
                    toggleMode();
                    binding.etEmail.setText("");
                    binding.etPassword.setText("");
                    binding.etUsername.setText("");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void checkUserRoleAndNavigate(String userId) {
        showLoading(true);

        db.collection("users").document(userId)
                .get(Source.SERVER)
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        showLoading(false);
                        Toast.makeText(this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                        mAuth.signOut();
                        return;
                    }

                    String role = userDoc.getString("role");
                    Log.d(TAG, "User role from DB (SERVER): " + role);

                    UserSyncHelper.syncUserData(userId); // ✅ Синхронизируем данные

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.reload().addOnCompleteListener(task -> {
                            Log.d(TAG, "User reloaded");
                        });
                    }

                    showLoading(false);

                    Intent intent;
                    if ("seller".equals(role)) {
                        intent = new Intent(this, MainActivity.class);
                    } else {
                        intent = new Intent(this, BuyerMainActivity.class);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error: " + e.getMessage());
                    Toast.makeText(this, getString(R.string.error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void resetPassword(String email) {
        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    Toast.makeText(this, task.isSuccessful() ? getString(R.string.reset_email_sent) : getString(R.string.reset_email_error), Toast.LENGTH_LONG).show();
                });
    }

    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.btnAction.setEnabled(!show);
            binding.btnAction.setAlpha(show ? 0.5f : 1.0f);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacksAndMessages(null);
    }
}