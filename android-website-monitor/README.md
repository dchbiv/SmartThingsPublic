# Website Monitor (Android)

A small Android app that checks a website every 15 minutes in the background and
notifies you when:

- the site goes **down** (no response, timeout, or HTTP 4xx/5xx) or comes **back up**
- the page **content changes** (optional; compares a SHA-256 of the page)
- a piece of **text you choose** appears on or disappears from the page (optional)

Enter the URL, optionally the text to watch for, and tap **Start monitoring**. The app
keeps a log of recent checks. Tapping a notification opens the site in your browser.

## How it works

- Scheduling uses WorkManager periodic work at 15 minutes, the shortest interval
  Android allows for background jobs. It survives app restarts and reboots and only
  runs when the device has a network connection.
- Android may delay checks while the phone is in Doze / battery saver. For more
  regular timing, set the app's battery usage to **Unrestricted** in system settings.
- Pages with constantly changing content (timestamps, ads, tokens) will trigger a
  "changed" alert on every check; turn that switch off and use watched text instead.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. You can also open
this folder in Android Studio. The GitHub Actions workflow
`.github/workflows/android-website-monitor.yml` builds the APK on every push that
touches this folder and uploads it as a build artifact.
