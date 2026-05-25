package com.example.beathouse;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Locale;

public class App extends Application {
    private static final String TAG = "BeatHouseApp";
    private static boolean isAppInBackground = false;

    public interface AppLifecycleListener {
        void onAppBackgrounded();
        void onAppForegrounded();
    }

    private static AppLifecycleListener lifecycleListener;

    public static void setLifecycleListener(AppLifecycleListener listener) {
        lifecycleListener = listener;
    }

    public static boolean isAppInBackground() {
        return isAppInBackground;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Применяем локализацию
        LocaleHelper.applyLanguage(this);

        // Загружаем тему из настроек
        android.content.SharedPreferences prefs = getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        int mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(mode);

        try {
            FirebaseApp.initializeApp(this);
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            Log.d(TAG, "✅ Firebase initialized");
        } catch (Exception e) {
            Log.e(TAG, "❌ Firebase initialization failed: " + e.getMessage(), e);
        }

        setupLifecycleObserver();
    }

    private void setupLifecycleObserver() {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                isAppInBackground = false;
                Log.d(TAG, "App in foreground");
                if (lifecycleListener != null) lifecycleListener.onAppForegrounded();
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                isAppInBackground = true;
                Log.d(TAG, "App in background");
                if (lifecycleListener != null) lifecycleListener.onAppBackgrounded();
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
