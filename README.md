# Spendly — Premium Offline Personal Expense Tracker for Android

Spendly is a native Android personal expense tracking application designed with a strict **local-first, privacy-by-default architecture**, automatic payment notification parsing, location-aware expense tagging, and a modern Material 3 fluid user experience.

---

## 🌟 Key Architecture & Privacy Guarantees

- **100% Offline & Private**: Zero backend servers, zero cloud databases, zero analytics SDKs, zero advertising networks, zero third-party telemetry.
- **Local Database (Room)**: Financial data remains strictly in the application's private sandbox storage on the user's phone.
- **Automatic Notification Parsing**: Listens for incoming transaction alerts from user-authorized payment apps (PhonePe, Google Pay, Paytm, BHIM, CRED, and SMS debits) and presents one-tap expense approval prompts.
- **Safe Notification Processing**: Sensitive notification texts are never stored long-term or logged to logcat in production. Raw text is discarded upon confirmation.
- **Optional Location Tagging**: When enabled by the user, associates latitude, longitude, and place names with expenses using Android's native `LocationManager` without requiring Google Play Services or remote tracking.
- **Complete User Ownership**: Users can export full transaction history to CSV and create or restore full JSON backups anytime via Android's Storage Access Framework (SAF).

---

## 📱 Features

1. **Dashboard (Home)**
   - Dynamic greeting and animated monthly spending total.
   - Month-over-month comparison percentage indicator.
   - Month-by-month navigation.
   - Notification permission setup guide banner.
   - Interactive detected expense candidate cards with one-tap "Add" and "Dismiss" actions.
   - Visual category spending progress bars.
   - Recent expenses list with color-coded direction indicators.

2. **Expenses & Transactions**
   - Full transaction history grouped by date with sticky headers and daily totals.
   - Real-time search with instant debounced filtering.
   - Quick category and source filters.
   - Swipe/tap delete with undo capability via Snackbar.

3. **Insights & Analytics**
   - Interactive monthly breakdown charts.
   - Category distribution percentages.
   - Top merchant spending rankings and transaction frequencies.
   - Period-over-period comparisons.

4. **Add / Edit Expense Sheet**
   - Quick manual expense logging.
   - High-contrast, formatted amount input in minor currency units (paise/cents).
   - Merchant autocomplete based on historical usage.
   - Category chips with distinctive color coding.
   - Optional user notes and automatic location tagging.

5. **Settings & Data Management**
   - **Automatic Detection**: Status badge and quick link to Android's Notification Access settings.
   - **Monitored Apps**: Granular switches to enable/disable detection per payment app (PhonePe, GPay, Paytm, etc.).
   - **Location Preferences**: Configurable capture modes (Off, On Save, Automatic, Manual) with runtime permission handling.
   - **Theme Selector**: System Default, Dark, and Light mode.
   - **Currency Selector**: INR (₹), USD ($), EUR (€), GBP (£).
   - **Data Export & Backup**:
     - Export to CSV (Spreadsheet compatible).
     - Full JSON Backup.
     - Restore from JSON Backup.
     - Reset / Clear all data (with confirmation dialog).
   - **Notification Parser Debugger**: In-app synthetic test tool to verify regex matching, confidence scoring, and pipeline stages without exposing real financial data.

---

## 🛠️ Technology Stack

- **Language**: Kotlin 2.3
- **UI Toolkit**: Jetpack Compose with Material 3
- **Dependency Injection**: Decoupled AppContainer pattern (Google Architecture Guide standard)
- **Database**: Room 2.8 with Coroutines Flow reactive streaming
- **Settings**: Jetpack DataStore (Preferences)
- **Lifecycle & Architecture**: ViewModel, Flow, StateFlow, Coroutines
- **Testing**: JUnit 4, Kotlinx Coroutines Test, AndroidX Test (27 Unit Tests, 100% Passing)

---

## 🚀 Build and Installation Instructions

### Prerequisites
- JDK 17 or higher
- Android SDK with platform API 36 / 35 (minSdk: 26, targetSdk: 36)
- Android Studio Ladybug / Meerkat or Gradle command line

### Building via Terminal

To build the debug APK:
```bash
./gradlew assembleDebug
```
The output APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To run all unit tests:
```bash
./gradlew testDebugUnitTest
```

### Installing on Device / Emulator

1. Connect your Android device via USB with USB debugging enabled, or start an Android Virtual Device (AVD).
2. Install the APK:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or directly run via Gradle:
```bash
./gradlew installDebug
```

---

## 📋 Features Tested, Features Not Tested, and Android Limitations

### Tested via Automated Unit Tests (27 / 27 Passed)
- [x] **Outgoing Payment Detection**: Regex parsing for PhonePe, Google Pay, Paytm, BHIM, CRED, Amazon Pay, and generic bank debits.
- [x] **Non-Expense Exclusions**: Verified OTP / auth codes, promotional loan offers, and pure balance queries are strictly excluded.
- [x] **Incoming Transfers**: Verified incoming transfers and peer payments are detected as CREDIT and NOT counted as expenses.
- [x] **Refunds & Failed Payments**: Verified refunds (CREDIT/REFUND) and failed transactions (FAILED) are excluded from expense totals.
- [x] **Multiple Amounts**: Verified parsing extracts the primary transaction amount instead of secondary cashback/fee amounts.
- [x] **Bank SMS with Balance**: Verified bank alerts with available balances at the end properly extract the debit amount.
- [x] **Currency Formatting**: Verified minor unit conversion (e.g. 14950 paise -> ₹149.50) and compact formats.
- [x] **Date Utilities**: Verified epoch conversions, month start/end boundaries, and relative date labels ("Today", "Yesterday").
- [x] **Expense Calculations**: Verified month-over-month percentage changes, category spending calculations, and exclusion rules.

### Android Limitations & Operational Notes
- **Notification Access (Android 4.3+)**: Requires explicit one-time user authorization in Android Settings (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). Android restricts apps from silently reading notifications.
- **Android 13+ (API 33) Notification Permission**: Posting notifications (such as "Possible expense detected") requires runtime `POST_NOTIFICATIONS` permission.
- **OEM Battery Optimization**: On aggressive battery-optimizing Android skins (MIUI, ColorOS, etc.), users may need to allow Spendly background execution so the `NotificationListenerService` is not killed.
- **Device Loss & Local Storage**: Because Spendly is 100% offline with zero cloud sync, uninstalling the app deletes local Room database files. Users must use the built-in "Backup JSON" or "Export CSV" to retain records before resetting their devices.
