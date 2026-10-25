# Human Tasks - Attune

| # | Task |
|---|------|
| 1 | Install Android Studio, set ANDROID_HOME, run sdkmanager --licenses |
| 2 | Grant notification access on a real phone (Profile > Notification access, or the in-app guide) |
| 3 | Handle Restricted settings on sideloaded APKs (Android 13+): App info > menu > Allow restricted settings |
| 4 | Exempt from battery optimization on your device brand |
| 5 | Test with real apps: WhatsApp, Instagram, YouTube (queueing, grouping, suppression, Open Now) |
| 6 | Run a 25-min session with screen off to verify the foreground service completes it on time |
| 7 | Upgrade test: install the old v1 APK, create data, install the new APK, confirm the migration keeps data |
| 8 | Calibrate priors.json after real sessions, or replace PredictionEngine with a trained AttentionPredictor |
| 9 | App signing and Play Console (post-prototype) |

FocusListenerService.kt has NOT been verified on a physical device yet.
Demo samples are on by default (BuildConfig.DEMO_MODE_DEFAULT) and can be turned off in Profile > Demo data.
