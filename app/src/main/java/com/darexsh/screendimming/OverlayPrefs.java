package com.darexsh.screendimming;

import android.content.Context;
import android.content.SharedPreferences;

public final class OverlayPrefs {

    private static final String PREFS_NAME = "screen_dimming_prefs";
    private static final String KEY_INTENSITY_PERCENT = "intensity_percent";

    private OverlayPrefs() {
    }

    public static int getIntensityPercent(Context context, int defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_INTENSITY_PERCENT, defaultValue);
    }

    public static void setIntensityPercent(Context context, int value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_INTENSITY_PERCENT, value).apply();
    }
}
