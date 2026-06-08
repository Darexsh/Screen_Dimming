package com.darexsh.screendimming;

import android.graphics.Color;

final class OverlayRuntimeState {

    private Integer lastOverlayColor;
    private Integer lastNotificationIntensity;
    private Boolean lastBroadcastRunning;
    private Integer lastBroadcastIntensity;
    private boolean foregroundStarted;

    boolean recordOverlay(int intensityPercent, int filterType) {
        int nextColor = buildOverlayColor(intensityPercent, filterType);
        if (lastOverlayColor != null && lastOverlayColor == nextColor) {
            return false;
        }
        lastOverlayColor = nextColor;
        return true;
    }

    int getCurrentOverlayColor() {
        return lastOverlayColor != null ? lastOverlayColor : buildOverlayColor(0, OverlayPrefs.FILTER_BLACK);
    }

    boolean recordNotificationIntensity(int intensityPercent) {
        if (lastNotificationIntensity != null && lastNotificationIntensity == intensityPercent) {
            return false;
        }
        lastNotificationIntensity = intensityPercent;
        return true;
    }

    boolean recordBroadcast(boolean running, int intensityPercent) {
        if (lastBroadcastRunning != null
                && lastBroadcastIntensity != null
                && lastBroadcastRunning == running
                && lastBroadcastIntensity == intensityPercent) {
            return false;
        }
        lastBroadcastRunning = running;
        lastBroadcastIntensity = intensityPercent;
        return true;
    }

    boolean isForegroundStarted() {
        return foregroundStarted;
    }

    void markForegroundStarted() {
        foregroundStarted = true;
    }

    void reset() {
        lastOverlayColor = null;
        lastNotificationIntensity = null;
        lastBroadcastRunning = null;
        lastBroadcastIntensity = null;
        foregroundStarted = false;
    }

    static int buildOverlayColor(int intensityPercent, int filterType) {
        int alpha = Math.round((sanitizeIntensity(intensityPercent) / 100f) * 255f);
        int color = getFilterColor(filterType);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int sanitizeIntensity(int intensityPercent) {
        return Math.max(0, Math.min(99, intensityPercent));
    }

    private static int getFilterColor(int filterType) {
        switch (OverlayPrefs.sanitizeFilterType(filterType)) {
            case OverlayPrefs.FILTER_WARM:
                return Color.rgb(48, 24, 0);
            case OverlayPrefs.FILTER_RED:
                return Color.rgb(38, 0, 0);
            case OverlayPrefs.FILTER_BLUE:
                return Color.rgb(0, 14, 34);
            case OverlayPrefs.FILTER_BLACK:
            default:
                return Color.rgb(0, 0, 0);
        }
    }
}
