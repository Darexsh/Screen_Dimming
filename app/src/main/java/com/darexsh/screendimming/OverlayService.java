package com.darexsh.screendimming;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String ACTION_START = "com.darexsh.screendimming.action.START";
    private static final String ACTION_STOP = "com.darexsh.screendimming.action.STOP";
    private static final String ACTION_UPDATE_INTENSITY = "com.darexsh.screendimming.action.UPDATE_INTENSITY";
    private static final String ACTION_UPDATE_FILTER = "com.darexsh.screendimming.action.UPDATE_FILTER";
    private static final String ACTION_DECREASE = "com.darexsh.screendimming.action.DECREASE";
    private static final String ACTION_INCREASE = "com.darexsh.screendimming.action.INCREASE";
    private static final String ACTION_OPEN_APP = "com.darexsh.screendimming.action.OPEN_APP";
    public static final String ACTION_STATE_CHANGED = "com.darexsh.screendimming.action.STATE_CHANGED";
    public static final String EXTRA_RUNNING = "extra_running";
    public static final String EXTRA_CURRENT_INTENSITY = "extra_current_intensity";
    private static final String EXTRA_INTENSITY = "extra_intensity";
    private static final String EXTRA_FILTER_TYPE = "extra_filter_type";
    private static final String NOTIFICATION_CHANNEL_ID = "screen_dimming_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int NOTIFICATION_STEP = 5;
    private static final long TRIPLE_TAP_WINDOW_MS = 900L;

    private static volatile boolean running;

    private WindowManager windowManager;
    private View dimView;
    private View unlockGestureView;
    private int currentIntensity = 70;
    private int currentFilterType = OverlayPrefs.FILTER_BLACK;
    private long firstTapTs = 0L;
    private int tapCount = 0;

    public static void start(Context context, int intensityPercent) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_INTENSITY, sanitizeIntensity(intensityPercent));
        startServiceCompat(context, intent, true);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction(ACTION_STOP);
        startServiceCompat(context, intent, false);
    }

    public static void sendIntensityUpdate(Context context, int intensityPercent) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction(ACTION_UPDATE_INTENSITY);
        intent.putExtra(EXTRA_INTENSITY, sanitizeIntensity(intensityPercent));
        startServiceCompat(context, intent, false);
    }

    public static void sendFilterUpdate(Context context, int filterType) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction(ACTION_UPDATE_FILTER);
        intent.putExtra(EXTRA_FILTER_TYPE, OverlayPrefs.sanitizeFilterType(filterType));
        startServiceCompat(context, intent, false);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        int intensity = sanitizeIntensity(intent != null ? intent.getIntExtra(EXTRA_INTENSITY, 70) : 70);
        int filterType = OverlayPrefs.sanitizeFilterType(
                intent != null
                        ? intent.getIntExtra(
                        EXTRA_FILTER_TYPE,
                        OverlayPrefs.getFilterType(this, OverlayPrefs.FILTER_BLACK)
                )
                        : OverlayPrefs.getFilterType(this, OverlayPrefs.FILTER_BLACK)
        );

        if (ACTION_STOP.equals(action)) {
            teardownOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_OPEN_APP.equals(action)) {
            Intent openIntent = new Intent(this, MainActivity.class);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(openIntent);
            return START_STICKY;
        }

        if (ACTION_START.equals(action)) {
            startForegroundIfNeeded();
            setupOverlayIfNeeded();
            setupUnlockGestureIfNeeded();
            applyFilter(filterType);
            applyIntensity(intensity);
            OverlayPrefs.setIntensityPercent(this, intensity);
            OverlayPrefs.setFilterType(this, filterType);
            updateNotification();
            return START_STICKY;
        }

        if (ACTION_UPDATE_INTENSITY.equals(action)) {
            if (!running || dimView == null) {
                OverlayPrefs.setIntensityPercent(this, intensity);
                return START_NOT_STICKY;
            }
            startForegroundIfNeeded();
            applyIntensity(intensity);
            OverlayPrefs.setIntensityPercent(this, intensity);
            updateNotification();
            return START_STICKY;
        }

        if (ACTION_UPDATE_FILTER.equals(action)) {
            OverlayPrefs.setFilterType(this, filterType);
            if (!running || dimView == null) {
                currentFilterType = filterType;
                return START_NOT_STICKY;
            }
            startForegroundIfNeeded();
            applyFilter(filterType);
            updateNotification();
            return START_STICKY;
        }

        if (ACTION_DECREASE.equals(action)) {
            if (!running) {
                return START_NOT_STICKY;
            }
            applyIntensity(currentIntensity - NOTIFICATION_STEP);
            OverlayPrefs.setIntensityPercent(this, currentIntensity);
            updateNotification();
            return START_STICKY;
        }

        if (ACTION_INCREASE.equals(action)) {
            if (!running) {
                return START_NOT_STICKY;
            }
            applyIntensity(currentIntensity + NOTIFICATION_STEP);
            OverlayPrefs.setIntensityPercent(this, currentIntensity);
            updateNotification();
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        teardownOverlay();
        super.onDestroy();
    }

    private void setupOverlayIfNeeded() {
        if (dimView != null) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        dimView = new View(this);
        dimView.setBackgroundColor(Color.argb(0, 0, 0, 0));

        WindowManager.LayoutParams params = getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(dimView, params);
            running = true;
            broadcastStateChanged(true);
        } catch (RuntimeException e) {
            dimView = null;
            running = false;
            stopSelf();
        }
    }

    private void setupUnlockGestureIfNeeded() {
        if (unlockGestureView != null || windowManager == null) {
            return;
        }

        unlockGestureView = new View(this);
        unlockGestureView.setBackgroundColor(Color.TRANSPARENT);
        unlockGestureView.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN) {
                return false;
            }
            long now = System.currentTimeMillis();
            if (now - firstTapTs > TRIPLE_TAP_WINDOW_MS) {
                firstTapTs = now;
                tapCount = 1;
            } else {
                tapCount++;
                if (tapCount >= 3) {
                    vibrateUnlockFeedback();
                    stop(this);
                    tapCount = 0;
                    firstTapTs = 0L;
                    return true;
                }
            }
            return true;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dpToPx(52),
                dpToPx(52),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dpToPx(10);
        params.y = dpToPx(10);

        try {
            windowManager.addView(unlockGestureView, params);
        } catch (RuntimeException ignored) {
            unlockGestureView = null;
        }
    }

    private static WindowManager.LayoutParams getLayoutParams() {
        int layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
    }

    private void startForegroundIfNeeded() {
        createNotificationChannelIfNeeded();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, OverlayService.class);
        openIntent.setAction(ACTION_OPEN_APP);
        PendingIntent openPendingIntent = PendingIntent.getService(
                this, 1, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text, currentIntensity))
                .setSmallIcon(R.drawable.ic_stat_screendimming)
                .setOngoing(true)
                .setContentIntent(openPendingIntent)
                .addAction(
                        0,
                        getString(R.string.notification_action_minus),
                        buildServicePendingIntent(ACTION_DECREASE, 2)
                )
                .addAction(
                        0,
                        getString(R.string.notification_action_plus),
                        buildServicePendingIntent(ACTION_INCREASE, 3)
                )
                .addAction(
                        0,
                        getString(R.string.notification_action_off),
                        buildServicePendingIntent(ACTION_STOP, 4)
                )
                .build();
    }

    private PendingIntent buildServicePendingIntent(String action, int requestCode) {
        Intent actionIntent = new Intent(this, OverlayService.class);
        actionIntent.setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                actionIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void teardownOverlay() {
        if (windowManager != null && unlockGestureView != null) {
            windowManager.removeView(unlockGestureView);
        }
        if (windowManager != null && dimView != null) {
            windowManager.removeView(dimView);
        }
        unlockGestureView = null;
        dimView = null;
        windowManager = null;
        running = false;
        broadcastStateChanged(false);
    }

    private void applyIntensity(int intensityPercent) {
        if (dimView == null) {
            return;
        }
        currentIntensity = sanitizeIntensity(intensityPercent);
        int alpha = Math.round((currentIntensity / 100f) * 255f);
        dimView.setAlpha(1f);
        int color = getFilterColor(currentFilterType);
        dimView.setBackgroundColor((alpha << 24) | (color & 0x00FFFFFF));
        broadcastStateChanged(running);
    }

    private void applyFilter(int filterType) {
        currentFilterType = OverlayPrefs.sanitizeFilterType(filterType);
        applyIntensity(currentIntensity);
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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

    private void vibrateUnlockFeedback() {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = getSystemService(VibratorManager.class);
                vibrator = manager != null ? manager.getDefaultVibrator() : null;
                if (vibrator == null) {
                    vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                }
            } else {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(
                                new long[]{0L, 90L, 40L, 130L},
                                new int[]{0, 255, 0, 255},
                                -1
                        )
                );
            } else {
                vibrator.vibrate(220L);
            }
        } catch (RuntimeException ignored) {
            // Ignore vibration errors; unlock must still continue.
        }
    }

    private void broadcastStateChanged(boolean isRunning) {
        Intent stateIntent = new Intent(ACTION_STATE_CHANGED);
        stateIntent.setPackage(getPackageName());
        stateIntent.putExtra(EXTRA_RUNNING, isRunning);
        stateIntent.putExtra(EXTRA_CURRENT_INTENSITY, currentIntensity);
        sendBroadcast(stateIntent);
    }

    private static void startServiceCompat(Context context, Intent intent, boolean requiresForegroundStart) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (requiresForegroundStart) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } else {
            context.startService(intent);
        }
    }
}
