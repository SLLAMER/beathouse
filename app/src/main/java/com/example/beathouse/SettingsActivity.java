package com.example.beathouse;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.databinding.ActivitySettingsBinding;
import com.example.beathouse.utils.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private RadioGroup rgLanguage;
    private RadioButton rbEnglish, rbRussian;
    private MaterialButton btnDeleteAccount;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
}