package com.example.beathouse.fragments;
import com.example.beathouse.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.example.beathouse.R;
import com.example.beathouse.models.NotificationSettings;
import com.example.beathouse.models.User;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;

public class NotificationSettingsFragment extends DialogFragment {

    private SwitchMaterial switchMessages, switchFollows, switchSales, switchPurchases, switchReviews;
    private MaterialTextView tvInfo;
    private View progressBar;
    private LinearLayout layoutSales, layoutPurchases;
    private View dividerSales, dividerPurchases;
    private FirestoreHelper firestoreHelper;
    private String currentUserId;
    private NotificationSettings settings;
    private User currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firestoreHelper = new FirestoreHelper();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        initViews(view);
        loadUserAndSettings();
        setupListeners();
    }

    private void initViews(View view) {
        switchMessages = view.findViewById(R.id.switch_messages);
        switchFollows = view.findViewById(R.id.switch_follows);
        switchSales = view.findViewById(R.id.switch_sales);
        switchPurchases = view.findViewById(R.id.switch_purchases);
        switchReviews = view.findViewById(R.id.switch_reviews);
        tvInfo = view.findViewById(R.id.tv_info);
        progressBar = view.findViewById(R.id.progress_bar);
        layoutSales = view.findViewById(R.id.layout_sales);
        layoutPurchases = view.findViewById(R.id.layout_purchases);
        dividerSales = view.findViewById(R.id.divider_sales);
        dividerPurchases = view.findViewById(R.id.divider_purchases);
    }

    private void loadUserAndSettings() {
        progressBar.setVisibility(View.VISIBLE);

        firestoreHelper.getUser(currentUserId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                if (!isAdded() || getActivity() == null) return;
                currentUser = (User) result;
                settings = currentUser.getNotificationSettings();

                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    updateUIByRole();
                    updateSwitches();
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    settings = new NotificationSettings();
                    updateSwitches();
                    Toast.makeText(getContext(),
                            getString(R.string.error_loading_settings) + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ✅ Скрываем/показываем настройки в зависимости от роли пользователя
    private void updateUIByRole() {
        if (currentUser == null) return;

        boolean isSeller = currentUser.isSeller();
        boolean isBuyer = currentUser.isBuyer();

        // Для продавца - показываем продажи, скрываем покупки
        // Для покупателя - показываем покупки, скрываем продажи
        if (isSeller) {
            // Продавец: показываем Sales, скрываем Purchases
            if (layoutSales != null) layoutSales.setVisibility(View.VISIBLE);
            if (dividerSales != null) dividerSales.setVisibility(View.VISIBLE);
            if (layoutPurchases != null) layoutPurchases.setVisibility(View.GONE);
            if (dividerPurchases != null) dividerPurchases.setVisibility(View.GONE);
        } else if (isBuyer) {
            // Покупатель: показываем Purchases, скрываем Sales
            if (layoutSales != null) layoutSales.setVisibility(View.GONE);
            if (dividerSales != null) dividerSales.setVisibility(View.GONE);
            if (layoutPurchases != null) layoutPurchases.setVisibility(View.VISIBLE);
            if (dividerPurchases != null) dividerPurchases.setVisibility(View.VISIBLE);
        } else {
            // По умолчанию показываем оба
            if (layoutSales != null) layoutSales.setVisibility(View.VISIBLE);
            if (dividerSales != null) dividerSales.setVisibility(View.VISIBLE);
            if (layoutPurchases != null) layoutPurchases.setVisibility(View.VISIBLE);
            if (dividerPurchases != null) dividerPurchases.setVisibility(View.VISIBLE);
        }
    }

    private void updateSwitches() {
        if (settings == null) return;
        switchMessages.setChecked(settings.isEnableMessages());
        switchFollows.setChecked(settings.isEnableFollows());
        switchSales.setChecked(settings.isEnableSales());
        switchPurchases.setChecked(settings.isEnablePurchases());
        switchReviews.setChecked(settings.isEnableReviews());
    }

    private void setupListeners() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            if (settings == null) return;

            int id = buttonView.getId();
            if (id == R.id.switch_messages) {
                settings.setEnableMessages(isChecked);
            } else if (id == R.id.switch_follows) {
                settings.setEnableFollows(isChecked);
            } else if (id == R.id.switch_sales) {
                settings.setEnableSales(isChecked);
            } else if (id == R.id.switch_purchases) {
                settings.setEnablePurchases(isChecked);
            } else if (id == R.id.switch_reviews) {
                settings.setEnableReviews(isChecked);
            }

            saveSettings();
        };

        switchMessages.setOnCheckedChangeListener(listener);
        switchFollows.setOnCheckedChangeListener(listener);
        switchSales.setOnCheckedChangeListener(listener);
        switchPurchases.setOnCheckedChangeListener(listener);
        switchReviews.setOnCheckedChangeListener(listener);
    }

    private void saveSettings() {
        if (settings == null) return;

        firestoreHelper.getUser(currentUserId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                if (!isAdded() || getActivity() == null) return;
                User user = (User) result;
                user.setNotificationSettings(settings);

                firestoreHelper.updateUser(user, new FirestoreHelper.FirestoreCallback() {
                    @Override
                    public void onSuccess(Object result) {
                        if (!isAdded() || getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            Toast.makeText(getContext(),
                                    getString(R.string.profile_saved),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded() || getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            Toast.makeText(getContext(),
                                    getString(R.string.error_saving_settings) + error,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(),
                            getString(R.string.error_loading_user) + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}