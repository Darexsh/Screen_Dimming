package com.darexsh.screendimming;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import androidx.core.os.LocaleListCompat;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS_PERMISSION = 2101;

    private TextView overlayStatusValue;
    private TextView notificationStatusValue;
    private TextView currentLanguageValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguagePrefs.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        overlayStatusValue = findViewById(R.id.overlayStatusValue);
        notificationStatusValue = findViewById(R.id.notificationStatusValue);
        currentLanguageValue = findViewById(R.id.currentLanguageValue);
        Button checkButton = findViewById(R.id.checkOverlayButton);
        Button openSettingsButton = findViewById(R.id.openOverlaySettingsButton);
        Button grantNotificationButton = findViewById(R.id.grantNotificationButton);
        Button englishLanguageButton = findViewById(R.id.englishLanguageButton);
        Button germanLanguageButton = findViewById(R.id.germanLanguageButton);
        TextView settingsInfoButton = findViewById(R.id.settingsInfoButton);

        checkButton.setOnClickListener(v -> refreshOverlayStatus());
        openSettingsButton.setOnClickListener(v -> openOverlayPermissionScreen());
        grantNotificationButton.setOnClickListener(v -> onGrantNotificationPermissionClicked());
        englishLanguageButton.setOnClickListener(v -> setAppLanguage("en"));
        germanLanguageButton.setOnClickListener(v -> setAppLanguage("de"));
        settingsInfoButton.setOnClickListener(v -> showAppInfoDialog());

        refreshOverlayStatus();
        refreshNotificationStatus();
        refreshLanguageStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOverlayStatus();
        refreshNotificationStatus();
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
        String languageLabel = languageTag != null && languageTag.toLowerCase().startsWith("de")
                ? getString(R.string.language_german)
                : getString(R.string.language_english);
        currentLanguageValue.setText(getString(R.string.current_language_format, languageLabel));
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
