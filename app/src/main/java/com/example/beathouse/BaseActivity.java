package com.example.beathouse;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    protected TextView notificationBadge;
    private ListenerRegistration notificationsListener;

    @Override
    protected void attachBaseContext(Context newBase) {
        // Применяем сохраненный язык ДО создания Activity
        String language = LocaleHelper.getLanguage(newBase);
        Context context = LocaleHelper.setLocale(newBase, language);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Применяем язык перед super.onCreate
        String language = LocaleHelper.getLanguage(this);
        LocaleHelper.updateLocale(this, language);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startNotificationsListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopNotificationsListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Проверяем язык при возобновлении
        String language = LocaleHelper.getLanguage(this);
        String currentLocale = getCurrentLocale();

        if (!language.equals(currentLocale)) {
            LocaleHelper.updateLocale(this, language);
            recreate();
        }
    }

    protected void startNotificationsListener() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        stopNotificationsListener();

        notificationsListener = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .addSnapshotListener((snap, err) -> {
                    if (snap != null) {
                        updateNotificationBadge(snap.size());
                    }
                });
    }

    protected void stopNotificationsListener() {
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
    }

    protected void updateNotificationBadge(int count) {
        if (notificationBadge != null) {
            runOnUiThread(() -> {
                if (count > 0) {
                    notificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                    notificationBadge.setVisibility(View.VISIBLE);
                } else {
                    notificationBadge.setVisibility(View.GONE);
                }
            });
        }
    }


    protected void setupNotificationBadge(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_notifications);
        if (item != null) {
            item.setActionView(R.layout.layout_notification_badge);
            View actionView = item.getActionView();
            notificationBadge = actionView.findViewById(R.id.tv_badge);

            // Настройка иконки в зависимости от темы
            ImageView ivIcon = actionView.findViewById(R.id.iv_icon);
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_notifications);
            }

            actionView.setOnClickListener(v -> {
                onOptionsItemSelected(item);
            });

            // Trigger initial update
            startNotificationsListener();
        }
    }

    private String getCurrentLocale() {
        Configuration config = getResources().getConfiguration();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return config.getLocales().get(0).getLanguage();
        } else {
            return config.locale.getLanguage();
        }
    }
}