package com.example.beathouse;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.utils.LocaleHelper;
import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

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

    private String getCurrentLocale() {
        Configuration config = getResources().getConfiguration();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return config.getLocales().get(0).getLanguage();
        } else {
            return config.locale.getLanguage();
        }
    }
}