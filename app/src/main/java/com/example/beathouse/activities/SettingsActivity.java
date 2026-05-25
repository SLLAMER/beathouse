package com.example.beathouse.activities;
import com.example.beathouse.R;
import com.example.beathouse.App;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.Context;
import android.content.SharedPreferences;
import com.example.beathouse.databinding.ActivitySettingsBinding;
import com.example.beathouse.utils.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase, LocaleHelper.getLanguage(newBase)));
    }

    private ActivitySettingsBinding binding;
    private RadioGroup rgLanguage;
    private RadioButton rbEnglish, rbRussian;
    private MaterialButton btnDeleteAccount;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applyLanguage(this);
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupToolbar();
        initViews();
        loadCurrentLanguage();
        setupListeners();
        setupDeleteAccountButton();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.settings));
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        rgLanguage = binding.rgLanguage;
        rbEnglish = binding.rbEnglish;
        rbRussian = binding.rbRussian;
        btnDeleteAccount = binding.btnDeleteAccount;

        rbEnglish.setText(getString(R.string.english));
        rbRussian.setText(getString(R.string.russian));

        btnDeleteAccount.setText(getString(R.string.delete_account));
    }

    private void loadCurrentLanguage() {
        String currentLang = LocaleHelper.getLanguage(this);

        if (currentLang.equals("ru")) {
            rbRussian.setChecked(true);
        } else {
            rbEnglish.setChecked(true);
        }
    }

    private void setupListeners() {
        binding.btnChangeEmail.setOnClickListener(v -> showChangeEmailDialog());
        binding.btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.btnClearStats.setOnClickListener(v -> showClearStatsDialog());

        rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String newLang;
            if (checkedId == R.id.rbRussian) {
                newLang = "ru";
            } else {
                newLang = "en";
            }

            String currentLang = LocaleHelper.getLanguage(this);

            if (!currentLang.equals(newLang)) {
                saveLanguageAndRestart(newLang);
            }
        });
    }

    private void saveLanguageAndRestart(String languageCode) {
        // Сохраняем язык
        LocaleHelper.saveLanguage(this, languageCode);

        // Применяем язык
        LocaleHelper.updateLocale(this, languageCode);

        Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_LONG).show();

        // Перезапускаем активити
        recreate();
    }

    private void setupDeleteAccountButton() {
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_account))
                .setMessage(getString(R.string.delete_account_confirmation))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> deleteAccount())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void deleteAccount() {
        btnDeleteAccount.setEnabled(false);
        btnDeleteAccount.setText(getString(R.string.deleting));

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            btnDeleteAccount.setEnabled(true);
            btnDeleteAccount.setText(getString(R.string.delete_account));
            return;
        }

        String userId = currentUser.getUid();
        deleteUserData(userId);
    }

    private void deleteUserData(String userId) {
        db.collection("users").document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    db.collection("producers").document(userId)
                            .delete()
                            .addOnFailureListener(e -> {
                                Log.d("Settings", "No producer data or already deleted");
                            });
                    deleteUserOrders(userId);
                })
                .addOnFailureListener(e -> {
                    btnDeleteAccount.setEnabled(true);
                    btnDeleteAccount.setText(getString(R.string.delete_account));
                    Toast.makeText(this, getString(R.string.error_deleting_data) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteUserOrders(String userId) {
        // Удаляем заказы, где пользователь покупатель
        db.collection("orders")
                .whereEqualTo("buyerId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                });

        // Удаляем заказы, где пользователь продавец
        db.collection("orders")
                .whereEqualTo("producerId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                });

        deleteAuthAccount();
    }

    private void deleteAuthAccount() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser != null) {
            currentUser.delete()
                    .addOnSuccessListener(aVoid -> {
                        btnDeleteAccount.setEnabled(true);
                        Toast.makeText(this, getString(R.string.account_deleted_successfully), Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finishAffinity();
                    })
                    .addOnFailureListener(e -> {
                        btnDeleteAccount.setEnabled(true);
                        btnDeleteAccount.setText(getString(R.string.delete_account));
                        Toast.makeText(this, getString(R.string.error_deleting_account) + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void showChangeEmailDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_change_email, null);
        android.widget.EditText etCurrentPassword = view.findViewById(R.id.etCurrentPassword);
        android.widget.EditText etNewEmail = view.findViewById(R.id.etNewEmail);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.change_email))
                .setView(view)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String password = etCurrentPassword.getText().toString().trim();
                    String newEmail = etNewEmail.getText().toString().trim();
                    if (!password.isEmpty() && !newEmail.isEmpty()) {
                        updateEmail(password, newEmail);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void updateEmail(String password, String newEmail) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        com.google.firebase.auth.AuthCredential credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(user.getEmail(), password);

        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                user.updateEmail(newEmail).addOnCompleteListener(emailTask -> {
                    if (emailTask.isSuccessful()) {
                        db.collection("users").document(user.getUid()).update("email", newEmail);
                        Toast.makeText(this, getString(R.string.email_updated), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.error_prefix) + emailTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, getString(R.string.wrong_password), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showChangePasswordDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        android.widget.EditText etCurrentPassword = view.findViewById(R.id.etCurrentPassword);
        android.widget.EditText etNewPassword = view.findViewById(R.id.etNewPassword);
        android.widget.EditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.change_password))
                .setView(view)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String currentPassword = etCurrentPassword.getText().toString().trim();
                    String newPassword = etNewPassword.getText().toString().trim();
                    String confirmPassword = etConfirmPassword.getText().toString().trim();

                    if (currentPassword.isEmpty() || newPassword.isEmpty()) return;

                    if (!newPassword.equals(confirmPassword)) {
                        Toast.makeText(this, getString(R.string.passwords_dont_match), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updatePassword(currentPassword, newPassword);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showClearStatsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_stats))
                .setMessage(getString(R.string.clear_stats_confirmation))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> clearStats())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void clearStats() {
        String userId = auth.getUid();
        if (userId == null) return;

        new com.example.beathouse.utils.FirestoreHelper().resetUserStats(userId, new com.example.beathouse.utils.FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                Toast.makeText(SettingsActivity.this, getString(R.string.stats_cleared), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SettingsActivity.this, getString(R.string.error_prefix) + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePassword(String currentPassword, String newPassword) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        com.google.firebase.auth.AuthCredential credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(user.getEmail(), currentPassword);

        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                user.updatePassword(newPassword).addOnCompleteListener(passTask -> {
                    if (passTask.isSuccessful()) {
                        Toast.makeText(this, getString(R.string.password_updated), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.error_prefix) + passTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, getString(R.string.wrong_password), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
