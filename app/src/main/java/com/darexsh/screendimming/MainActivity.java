package com.darexsh.screendimming;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final int DEFAULT_DIM_PERCENT = 50;
    private static final long ICON_ANIMATION_MS = 460L;
    private static final int REQUEST_NOTIFICATIONS_PERMISSION = 2001;

    private View dimToggleButton;
    private ImageView sunIcon;
    private ImageView moonIcon;
    private TextView toggleStateText;
    private SeekBar intensitySeekBar;
    private TextView intensityLabel;
    private boolean hasPromptedPermissionThisSession;
    private boolean overlayEnabled;
    private boolean syncingIntensityFromService;
    private final BroadcastReceiver overlayStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (intent == null || !OverlayService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            overlayEnabled = intent.getBooleanExtra(OverlayService.EXTRA_RUNNING, false);
            int intensity = intent.getIntExtra(
                    OverlayService.EXTRA_CURRENT_INTENSITY,
                    OverlayPrefs.getIntensityPercent(MainActivity.this, DEFAULT_DIM_PERCENT)
            );
            syncingIntensityFromService = true;
            intensitySeekBar.setProgress(intensity);
            updateIntensityLabel(intensity);
            syncingIntensityFromService = false;
            renderToggleState(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguagePrefs.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        dimToggleButton = findViewById(R.id.dimToggleButton);
        sunIcon = findViewById(R.id.sunIcon);
        moonIcon = findViewById(R.id.moonIcon);
        toggleStateText = findViewById(R.id.toggleStateText);
        intensitySeekBar = findViewById(R.id.intensitySeekBar);
        intensityLabel = findViewById(R.id.intensityLabel);
        Button settingsButton = findViewById(R.id.settingsButton);

        applySystemInsets();
        requestNotificationsPermissionIfNeeded();

        int savedIntensity = OverlayPrefs.getIntensityPercent(this, DEFAULT_DIM_PERCENT);
        intensitySeekBar.setProgress(savedIntensity);
        updateIntensityLabel(savedIntensity);

        intensitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateIntensityLabel(progress);
                OverlayPrefs.setIntensityPercent(MainActivity.this, progress);
                if (!syncingIntensityFromService && overlayEnabled && fromUser) {
                    OverlayService.sendIntensityUpdate(MainActivity.this, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No-op.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // No-op.
            }
        });

        dimToggleButton.setOnClickListener(v -> onDimToggleClicked());
        settingsButton.setOnClickListener(v -> onSettingsClicked());
        renderToggleState(false);
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.mainRoot);
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(baseLeft, baseTop, baseRight, baseBottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionUi();

        if (!canDrawOverlays()) {
            overlayEnabled = false;
            renderToggleState(false);
            if (!hasPromptedPermissionThisSession) {
                hasPromptedPermissionThisSession = true;
                showOverlayPermissionDialog();
            }
            return;
        }

        hasPromptedPermissionThisSession = false;
        overlayEnabled = OverlayService.isRunning();
        renderToggleState(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(OverlayService.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this, overlayStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(overlayStateReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was not registered; ignore.
        }
    }

    private void refreshPermissionUi() {
        boolean hasPermission = canDrawOverlays();
        dimToggleButton.setAlpha(hasPermission || OverlayService.isRunning() ? 1f : 0.95f);
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(this);
    }

    private void openOverlayPermissionScreen() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void showOverlayPermissionDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_dialog_title)
                .setMessage(R.string.overlay_permission_dialog_message)
                .setPositiveButton(R.string.overlay_permission_dialog_grant, (d, which) -> openOverlayPermissionScreen())
                .setNegativeButton(R.string.overlay_permission_dialog_cancel, (d, which) -> d.dismiss())
                .show();
        tintDialogButtonsGray(dialog);
    }

    private void onSettingsClicked() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void onDimToggleClicked() {
        if (!overlayEnabled) {
            if (!canDrawOverlays()) {
                showOverlayPermissionDialog();
                return;
            }
            if (!hasNotificationPermission()) {
                requestNotificationsPermissionIfNeeded();
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.notification_permission_title)
                        .setMessage(R.string.notification_permission_message)
                        .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                        .show();
                tintDialogButtonsGray(dialog);
                return;
            }
            OverlayService.start(this, intensitySeekBar.getProgress());
            overlayEnabled = true;
        } else {
            OverlayService.stop(this);
            overlayEnabled = false;
        }
        renderToggleState(true);
    }

    private void renderToggleState(boolean animateIcon) {
        dimToggleButton.setBackgroundResource(
                overlayEnabled ? R.drawable.dim_toggle_active : R.drawable.dim_toggle_inactive
        );
        moonIcon.setImageResource(R.drawable.moon_icon_active);
        toggleStateText.setText(overlayEnabled ? R.string.dimming_on : R.string.dimming_off);

        if (animateIcon) {
            animateSkyIconTransition(overlayEnabled);
            return;
        }

        sunIcon.animate().cancel();
        moonIcon.animate().cancel();
        sunIcon.setRotation(0f);
        moonIcon.setRotation(0f);
        sunIcon.setTranslationY(0f);
        moonIcon.setTranslationY(0f);
        sunIcon.setScaleX(1f);
        sunIcon.setScaleY(1f);
        moonIcon.setScaleX(1f);
        moonIcon.setScaleY(1f);
        dimToggleButton.setScaleX(1f);
        dimToggleButton.setScaleY(1f);
        sunIcon.setAlpha(overlayEnabled ? 0f : 1f);
        moonIcon.setAlpha(overlayEnabled ? 1f : 0f);
    }

    private void animateSkyIconTransition(boolean toMoon) {
        dimToggleButton.animate().cancel();
        sunIcon.animate().cancel();
        moonIcon.animate().cancel();

        if (toMoon) {
            moonIcon.setAlpha(0f);
            moonIcon.setScaleX(0.62f);
            moonIcon.setScaleY(0.62f);
            moonIcon.setRotation(26f);
            moonIcon.setTranslationY(16f);
        } else {
            sunIcon.setAlpha(0f);
            sunIcon.setScaleX(0.62f);
            sunIcon.setScaleY(0.62f);
            sunIcon.setRotation(-26f);
            sunIcon.setTranslationY(16f);
        }

        ObjectAnimator sunFade = ObjectAnimator.ofFloat(sunIcon, View.ALPHA, sunIcon.getAlpha(), toMoon ? 0f : 1f);
        ObjectAnimator sunScale = ObjectAnimator.ofPropertyValuesHolder(
                sunIcon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, sunIcon.getScaleX(), toMoon ? 0.72f : 1.06f, toMoon ? 0.72f : 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, sunIcon.getScaleY(), toMoon ? 0.72f : 1.06f, toMoon ? 0.72f : 1f)
        );
        ObjectAnimator sunRotate = ObjectAnimator.ofFloat(sunIcon, View.ROTATION, sunIcon.getRotation(), toMoon ? -70f : 0f);
        ObjectAnimator sunLift = ObjectAnimator.ofFloat(sunIcon, View.TRANSLATION_Y, sunIcon.getTranslationY(), toMoon ? -12f : 0f);

        ObjectAnimator moonFade = ObjectAnimator.ofFloat(moonIcon, View.ALPHA, moonIcon.getAlpha(), toMoon ? 1f : 0f);
        ObjectAnimator moonScale = ObjectAnimator.ofPropertyValuesHolder(
                moonIcon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, moonIcon.getScaleX(), toMoon ? 1.08f : 0.72f, toMoon ? 1f : 0.72f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, moonIcon.getScaleY(), toMoon ? 1.08f : 0.72f, toMoon ? 1f : 0.72f)
        );
        ObjectAnimator moonRotate = ObjectAnimator.ofFloat(moonIcon, View.ROTATION, moonIcon.getRotation(), toMoon ? 0f : 70f);
        ObjectAnimator moonRise = ObjectAnimator.ofFloat(moonIcon, View.TRANSLATION_Y, moonIcon.getTranslationY(), toMoon ? 0f : -12f);

        ObjectAnimator buttonPulse = ObjectAnimator.ofPropertyValuesHolder(
                dimToggleButton,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f, 1f)
        );
        buttonPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        buttonPulse.setDuration(ICON_ANIMATION_MS);

        sunFade.setInterpolator(new AccelerateInterpolator(1.2f));
        sunScale.setInterpolator(new AccelerateInterpolator(1.2f));
        sunRotate.setInterpolator(new AccelerateInterpolator(1.1f));
        sunLift.setInterpolator(new AccelerateInterpolator(1.1f));

        moonFade.setInterpolator(new DecelerateInterpolator(1.3f));
        moonScale.setInterpolator(new DecelerateInterpolator(1.4f));
        moonRotate.setInterpolator(new DecelerateInterpolator(1.2f));
        moonRise.setInterpolator(new DecelerateInterpolator(1.2f));

        AnimatorSet set = new AnimatorSet();
        set.setDuration(ICON_ANIMATION_MS);
        set.playTogether(
                sunFade, sunScale, sunRotate, sunLift,
                moonFade, moonScale, moonRotate, moonRise,
                buttonPulse
        );
        set.start();
    }

    private void updateIntensityLabel(int intensity) {
        intensityLabel.setText(getString(R.string.dimming_intensity_format, intensity));
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void tintDialogButtonsGray(AlertDialog dialog) {
        int color = ContextCompat.getColor(this, R.color.ui_gray);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(color);
        }
    }

    private void requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (!hasNotificationPermission()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS_PERMISSION);
        }
    }
}
