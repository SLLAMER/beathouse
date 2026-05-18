package com.example.beathouse;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Locale;

public class App extends Application {
    private static final String TAG = "BeatHouseApp";

    @Override
    public void onCreate() {
        super.onCreate();

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
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

    }
}