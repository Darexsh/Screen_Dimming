package com.darexsh.screendimming;

import android.content.Context;
import android.content.SharedPreferences;

public final class OverlayPrefs {

    private static final String PREFS_NAME = "screen_dimming_prefs";
    private static final String KEY_INTENSITY_PERCENT = "intensity_percent";
    private static final String KEY_FILTER_TYPE = "filter_type";

    public static final int FILTER_BLACK = 0;
    public static final int FILTER_WARM = 1;
    public static final int FILTER_RED = 2;
    public static final int FILTER_BLUE = 3;

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

    public static int getFilterType(Context context, int defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sanitizeFilterType(prefs.getInt(KEY_FILTER_TYPE, defaultValue));
    }

    public static void setFilterType(Context context, int filterType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_FILTER_TYPE, sanitizeFilterType(filterType)).apply();
    }

    public static int sanitizeFilterType(int filterType) {
        if (filterType < FILTER_BLACK || filterType > FILTER_BLUE) {
            return FILTER_BLACK;
        }
        return filterType;
    }
}
