package com.darexsh.screendimming;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class LanguagePrefs {

    private static final String PREFS_NAME = "screen_dimming_language_prefs";
    private static final String KEY_LANGUAGE_TAG = "language_tag";
    private static final String DEFAULT_LANGUAGE_TAG = "";

    private LanguagePrefs() {
    }

    public static void applySavedLanguage(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        String languageTag = getSavedLanguageTag(context);
        LocaleListCompat targetLocales = languageTag == null || languageTag.trim().isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(languageTag);
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        if (!targetLocales.toLanguageTags().equalsIgnoreCase(currentLocales.toLanguageTags())) {
            AppCompatDelegate.setApplicationLocales(targetLocales);
        }
    }

    public static String getSavedLanguageTag(Context context) {
        return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG);
    }

    public static void saveLanguageTag(Context context, String languageTag) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE_TAG, languageTag)
                .apply();
    }
}
