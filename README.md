# Driver Ledger

An Android MVP for cab/auto drivers to track UPI and cash income, expenses, and net earnings.

## MVP features
- Driving Mode UI
- Manual payment entry UI
- Manual expense entry UI
- Weekly summary UI
- Android NotificationListenerService for GPay/PhonePe/Paytm-style incoming payment notifications
- Voice confirmation with Android Text-to-Speech

## Important implementation note
Automatic payment detection is intentionally based on notification text rather than pretending to have direct access to private GPay/PhonePe/Paytm transaction APIs.

The current listener recognizes common incoming-payment wording, extracts amounts formatted like ₹350, Rs 350, or INR 350, saves them to a local Room database, and announces the real detected amount by voice (not a hardcoded demo value). Manual "+ Payment" / "− Expense" entries save to the same database. All totals on screen (today, this week) are computed live from stored data.

Before production release, still add:
- Stronger app/package and message parsing (more banks/wallets, more phrasings)
- Driver settings for which payment apps to listen to
- A clear consent/onboarding flow explaining why Notification Access is requested
- Daily/monthly report screens, not just today/week
- Backup/export
- Robust testing against real notification formats from each app
- Compliance review for Google Play policies (Notification Access has extra Play Store review requirements)

## CI
`.github/workflows/android.yml` builds the debug APK on every push/PR to `main` via GitHub Actions, so you'll see a green/red check on GitHub confirming it compiles.

## Note on the Gradle wrapper
This repo does not include the `gradle/wrapper/gradle-wrapper.jar` binary. Open the project in Android Studio once and it will offer to generate the wrapper automatically (or run `gradle wrapper` from a terminal with Gradle installed). CI does not need it — the workflow installs Gradle directly.

## Build
Open this folder in Android Studio and sync Gradle.

The app targets Android 8.0+ and Android API 35.
