# Privacy Policy – Screen Dimming

Last updated: 2026-05-21

## 1. Controller
Daniel Sichler (Darexsh)  
Email: sichler.daniel@gmail.com

## 2. Scope
This policy applies to the Screen Dimming Android app (`com.darexsh.screendimming`) distributed via this repository/APK releases.

## 3. What Screen Dimming processes
Screen Dimming processes technical app/device state required for dimming functionality, including:
- dimming intensity and selected color filter
- language preference
- current overlay/notification permission state used by app logic

## 4. Local data storage
Screen Dimming stores app settings locally on your device using `SharedPreferences`.

This includes intensity, filter choice, and language setting.

Screen Dimming does not upload these local settings to the developer.

## 5. Permissions used
Screen Dimming currently declares:
- `SYSTEM_ALERT_WINDOW` (to draw the dim overlay above other apps)
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` (to keep dimming active)
- `POST_NOTIFICATIONS` (for notification controls on Android 13+)

Permissions are used only to provide core dimming and control features.

## 6. Analytics, tracking, and crash reporting
Screen Dimming currently does **not** include third-party analytics SDKs or third-party crash reporting SDKs (such as Firebase Analytics/Crashlytics, Sentry, Mixpanel, etc.) in this project.

## 7. Network and external links
Screen Dimming does not require a dedicated backend API for core dimming functionality.

On user action, Screen Dimming can open external destinations via system intents, including:
- email (`mailto:`)
- Linktree
- Telegram
- GitHub
- Buy Me a Coffee

When you open external links, the privacy policies of those third-party services apply.

### Third-party services referenced by links
- GitHub: https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement
- Telegram: https://telegram.org/privacy
- Linktree: https://linktr.ee/s/privacy
- Buy Me a Coffee: https://www.buymeacoffee.com/privacy-policy

## 8. Data sharing
Based on the current app implementation in this repository, Screen Dimming does not transmit your locally stored settings to the developer.

## 9. Safety disclaimer
Maximum dimming is intentionally capped below 100% to reduce lockout risk.

Emergency/off behavior can depend on Android OS behavior, notification availability, and device settings.

## 10. Your choices
- You can stop dimming at any time from the app or notification controls.
- You can deny/revoke app permissions in Android settings.
- You can avoid opening external links.
- You can uninstall the app and clear its local data in Android settings.

## 11. Changes to this policy
This policy may be updated if app functionality or legal requirements change.
