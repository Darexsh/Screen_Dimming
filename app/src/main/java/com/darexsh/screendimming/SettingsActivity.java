package com.darexsh.screendimming;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS_PERMISSION = 2101;
    private static final int BUTTON_BG_SELECTED = Color.parseColor("#5A647A");
    private static final int BUTTON_BG_UNSELECTED = Color.parseColor("#313847");
    private static final int BUTTON_STROKE_SELECTED = Color.parseColor("#9AA8CC");
    private static final int BUTTON_STROKE_UNSELECTED = Color.parseColor("#515A6B");
    private static final int BUTTON_TEXT_SELECTED = Color.parseColor("#FFFFFFFF");
    private static final int BUTTON_TEXT_UNSELECTED = Color.parseColor("#E6FFFFFF");

    private TextView overlayStatusValue;
    private TextView notificationStatusValue;
    private TextView currentLanguageValue;
    private TextView currentFilterValue;
    private MaterialButton filterBlackButton;
    private MaterialButton filterWarmButton;
    private MaterialButton filterRedButton;
    private MaterialButton filterBlueButton;
    private MaterialButton englishLanguageButton;
    private MaterialButton germanLanguageButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguagePrefs.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        overlayStatusValue = findViewById(R.id.overlayStatusValue);
        notificationStatusValue = findViewById(R.id.notificationStatusValue);
        currentLanguageValue = findViewById(R.id.currentLanguageValue);
        currentFilterValue = findViewById(R.id.currentFilterValue);
        Button checkButton = findViewById(R.id.checkOverlayButton);
        Button openSettingsButton = findViewById(R.id.openOverlaySettingsButton);
        Button grantNotificationButton = findViewById(R.id.grantNotificationButton);
        filterBlackButton = findViewById(R.id.filterBlackButton);
        filterWarmButton = findViewById(R.id.filterWarmButton);
        filterRedButton = findViewById(R.id.filterRedButton);
        filterBlueButton = findViewById(R.id.filterBlueButton);
        englishLanguageButton = findViewById(R.id.englishLanguageButton);
        germanLanguageButton = findViewById(R.id.germanLanguageButton);
        TextView settingsInfoButton = findViewById(R.id.settingsInfoButton);

        checkButton.setOnClickListener(v -> refreshOverlayStatus());
        openSettingsButton.setOnClickListener(v -> openOverlayPermissionScreen());
        grantNotificationButton.setOnClickListener(v -> onGrantNotificationPermissionClicked());
        filterBlackButton.setOnClickListener(v -> setOverlayFilter(OverlayPrefs.FILTER_BLACK));
        filterWarmButton.setOnClickListener(v -> setOverlayFilter(OverlayPrefs.FILTER_WARM));
        filterRedButton.setOnClickListener(v -> setOverlayFilter(OverlayPrefs.FILTER_RED));
        filterBlueButton.setOnClickListener(v -> setOverlayFilter(OverlayPrefs.FILTER_BLUE));
        englishLanguageButton.setOnClickListener(v -> setAppLanguage("en"));
        germanLanguageButton.setOnClickListener(v -> setAppLanguage("de"));
        settingsInfoButton.setOnClickListener(v -> showAppInfoDialog());

        applySystemInsets();
        refreshOverlayStatus();
        refreshNotificationStatus();
        refreshFilterStatus();
        refreshLanguageStatus();
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.settingsRoot);
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom + bars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOverlayStatus();
        refreshNotificationStatus();
        refreshFilterStatus();
        refreshLanguageStatus();
    }

    private void refreshOverlayStatus() {
        boolean granted = canDrawOverlays();
        String statusText = granted
                ? getString(R.string.overlay_permission_granted_short)
                : getString(R.string.overlay_permission_not_granted_short);
        String fullText = getString(R.string.overlay_permission_status_format, statusText);
        SpannableString styled = new SpannableString(fullText);
        int start = fullText.lastIndexOf(statusText);
        if (start >= 0) {
            int end = start + statusText.length();
            styled.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(
                            this,
                            granted ? R.color.status_granted : R.color.status_not_granted
                    )),
                    start,
                    end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        overlayStatusValue.setText(styled);
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(this);
    }

    private void refreshNotificationStatus() {
        boolean allowed = areNotificationsAllowed();
        String statusText = allowed
                ? getString(R.string.notification_permission_allowed_short)
                : getString(R.string.notification_permission_not_allowed_short);
        String fullText = getString(R.string.notification_permission_status_format, statusText);
        SpannableString styled = new SpannableString(fullText);
        int start = fullText.lastIndexOf(statusText);
        if (start >= 0) {
            int end = start + statusText.length();
            styled.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(
                            this,
                            allowed ? R.color.status_granted : R.color.status_not_granted
                    )),
                    start,
                    end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        notificationStatusValue.setText(styled);
    }

    private boolean areNotificationsAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private void onGrantNotificationPermissionClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS_PERMISSION);
            return;
        }
        openAppNotificationSettings();
    }

    private void refreshLanguageStatus() {
        String languageTag = LanguagePrefs.getSavedLanguageTag(this);
        boolean germanSelected = languageTag != null && languageTag.toLowerCase().startsWith("de");
        String languageLabel = germanSelected
                ? getString(R.string.language_german)
                : getString(R.string.language_english);
        currentLanguageValue.setText(getString(R.string.current_language_format, languageLabel));
        applySelectionStyle(englishLanguageButton, !germanSelected);
        applySelectionStyle(germanLanguageButton, germanSelected);
    }

    private void refreshFilterStatus() {
        int filterType = OverlayPrefs.getFilterType(this, OverlayPrefs.FILTER_BLACK);
        currentFilterValue.setText(getString(R.string.current_filter_format, getFilterLabel(filterType)));
        applySelectionStyle(filterBlackButton, filterType == OverlayPrefs.FILTER_BLACK);
        applySelectionStyle(filterWarmButton, filterType == OverlayPrefs.FILTER_WARM);
        applySelectionStyle(filterRedButton, filterType == OverlayPrefs.FILTER_RED);
        applySelectionStyle(filterBlueButton, filterType == OverlayPrefs.FILTER_BLUE);
    }

    private void setOverlayFilter(int filterType) {
        int sanitized = OverlayPrefs.sanitizeFilterType(filterType);
        OverlayPrefs.setFilterType(this, sanitized);
        refreshFilterStatus();
        if (OverlayService.isRunning()) {
            OverlayService.sendFilterUpdate(this, sanitized);
        }
    }

    private String getFilterLabel(int filterType) {
        switch (OverlayPrefs.sanitizeFilterType(filterType)) {
            case OverlayPrefs.FILTER_WARM:
                return getString(R.string.filter_warm);
            case OverlayPrefs.FILTER_RED:
                return getString(R.string.filter_red);
            case OverlayPrefs.FILTER_BLUE:
                return getString(R.string.filter_blue);
            case OverlayPrefs.FILTER_BLACK:
            default:
                return getString(R.string.filter_black);
        }
    }

    private void applySelectionStyle(MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? BUTTON_BG_SELECTED : BUTTON_BG_UNSELECTED));
        button.setStrokeColor(ColorStateList.valueOf(selected ? BUTTON_STROKE_SELECTED : BUTTON_STROKE_UNSELECTED));
        button.setTextColor(selected ? BUTTON_TEXT_SELECTED : BUTTON_TEXT_UNSELECTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void setAppLanguage(String languageTag) {
        LanguagePrefs.saveLanguageTag(this, languageTag);
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void openOverlayPermissionScreen() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void showAppInfoDialog() {
        String versionName = "1.0";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_app_info, null);
        TextView appName = content.findViewById(R.id.tv_app_name);
        TextView appVersion = content.findViewById(R.id.tv_app_version);
        TextView appDescription = content.findViewById(R.id.tv_app_description);
        TextView appDeveloper = content.findViewById(R.id.tv_app_developer);
        Button openEmail = content.findViewById(R.id.btn_open_email);
        Button openGithub = content.findViewById(R.id.btn_open_github);
        Button openGithubProfile = content.findViewById(R.id.btn_open_github_profile);
        Button openCoffee = content.findViewById(R.id.btn_open_coffee);

        appName.setText(R.string.app_info_name);
        appVersion.setText(getString(R.string.app_info_version, versionName));
        appDescription.setText(R.string.app_info_description);

        String developerLabel = getString(R.string.app_info_developer_label);
        String developerName = getString(R.string.app_info_developer_name);
        SpannableString developerText = new SpannableString(developerLabel + " " + developerName);
        int labelEnd = developerLabel.length();
        developerText.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.ui_gray)),
                0,
                labelEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        developerText.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                labelEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        appDeveloper.setText(developerText);

        openEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:sichler.daniel@gmail.com"));
            startActivity(intent);
        });

        openGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/darexsh"));
            startActivity(intent);
        });

        openGithubProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Darexsh"));
            startActivity(intent);
        });

        openCoffee.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/darexsh"));
            startActivity(intent);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.app_info_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.ui_gray));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS_PERMISSION) {
            refreshNotificationStatus();
        }
    }
}
