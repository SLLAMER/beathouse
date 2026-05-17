package com.example.beathouse.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.Log;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREF_LANGUAGE = "app_language";
    private static final String TAG = "LocaleHelper";

    // Метод для attachBaseContext - возвращает контекст с новым языком
    public static Context setLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            config.setLocales(new LocaleList(locale));
        } else {
            config.locale = locale;
        }

        return context.createConfigurationContext(config);
    }

    // Метод для обновления языка в существующем контексте
    public static void updateLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            config.setLocales(new LocaleList(locale));
        } else {
            config.locale = locale;
        }

        resources.updateConfiguration(config, resources.getDisplayMetrics());
        Log.d(TAG, "Locale updated to: " + languageCode);
    }

    public static void saveLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_LANGUAGE, languageCode).apply();
        Log.d(TAG, "Language saved: " + languageCode);
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String lang = prefs.getString(PREF_LANGUAGE, "en");
        Log.d(TAG, "Language loaded: " + lang);
        return lang;
    }

    public static void applyLanguage(Context context) {
        String languageCode = getLanguage(context);
        updateLocale(context, languageCode);
    }
}