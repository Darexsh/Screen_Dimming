package com.darexsh.screendimming;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
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

    private static volatile boolean running;

    private final OverlayRuntimeState runtimeState = new OverlayRuntimeState();
    private WindowManager windowManager;
    private View dimView;
    private int currentIntensity = 70;
    private int currentFilterType = OverlayPrefs.FILTER_BLACK;
    private boolean screenStateReceiverRegistered;
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stop(context);
            }
        }
    };

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
            currentIntensity = intensity;
            currentFilterType = filterType;
            startForegroundIfNeeded();
            setupOverlayIfNeeded();
            applyOverlayState(intensity, filterType);
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
            applyOverlayState(intensity, currentFilterType);
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
            applyOverlayState(currentIntensity, filterType);
            updateNotification();
            return START_STICKY;
        }

        if (ACTION_DECREASE.equals(action)) {
            if (!running) {
                return START_NOT_STICKY;
            }
            applyOverlayState(currentIntensity - NOTIFICATION_STEP, currentFilterType);
            OverlayPrefs.setIntensityPercent(this, currentIntensity);
            updateNotification();
            return START_STICKY;
        }

        if (ACTION_INCREASE.equals(action)) {
            if (!running) {
                return START_NOT_STICKY;
            }
            applyOverlayState(currentIntensity + NOTIFICATION_STEP, currentFilterType);
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
            registerScreenStateReceiverIfNeeded();
            running = true;
            broadcastStateChanged(true);
        } catch (RuntimeException e) {
            dimView = null;
            running = false;
            stopSelf();
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
        if (runtimeState.isForegroundStarted()) {
            return;
        }
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
        runtimeState.markForegroundStarted();
        runtimeState.recordNotificationIntensity(currentIntensity);
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
        if (dimView == null && windowManager == null && !running && !runtimeState.isForegroundStarted()) {
            return;
        }
        unregisterScreenStateReceiverIfNeeded();
        if (windowManager != null && dimView != null) {
            windowManager.removeView(dimView);
        }
        stopForeground(true);
        dimView = null;
        windowManager = null;
        running = false;
        runtimeState.reset();
        broadcastStateChanged(false);
    }

    private void applyOverlayState(int intensityPercent, int filterType) {
        if (dimView == null) {
            return;
        }
        currentIntensity = sanitizeIntensity(intensityPercent);
        currentFilterType = OverlayPrefs.sanitizeFilterType(filterType);
        if (!runtimeState.recordOverlay(currentIntensity, currentFilterType)) {
            broadcastStateChanged(running);
            return;
        }
        dimView.setAlpha(1f);
        dimView.setBackgroundColor(runtimeState.getCurrentOverlayColor());
        broadcastStateChanged(running);
    }

    private void updateNotification() {
        if (!runtimeState.recordNotificationIntensity(currentIntensity)) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private static int sanitizeIntensity(int intensityPercent) {
        return Math.max(0, Math.min(99, intensityPercent));
    }

    private void broadcastStateChanged(boolean isRunning) {
        if (!runtimeState.recordBroadcast(isRunning, currentIntensity)) {
            return;
        }
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

    private void registerScreenStateReceiverIfNeeded() {
        if (screenStateReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);
        screenStateReceiverRegistered = true;
    }

    private void unregisterScreenStateReceiverIfNeeded() {
        if (!screenStateReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver already unregistered.
        }
        screenStateReceiverRegistered = false;
    }
}
