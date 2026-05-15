# Sahyadri Samrakshane

Sahyadri Samrakshane is a Kotlin Android app for reporting ecological alerts in the Western Ghats. It helps trekkers and local communities capture a photo, attach high-precision GPS coordinates, cache reports in low-signal areas, and sync them to Firebase for a central forest-department style dashboard.

## Features Implemented

- Nature-friendly green and earth-tone UI.
- Alert types:
  - Forest Fire
  - Landslide
  - Illegal Tree Cutting
  - Wildlife Sighting
- CameraX photo capture for report evidence.
- FusedLocationProviderClient for high-accuracy GPS.
- Latitude, longitude, and accuracy shown on the report screen.
- Room database cache so reports are saved even with low signal.
- WorkManager background sync that uploads pending reports when internet returns.
- Firebase Firestore sync to the `ecological_alerts` collection.
- Alert statuses:
  - Reported
  - Verified
  - Team Dispatched
- Map screen for alert locations.
- Education/tips screen for eco-sensitive zone behavior.

## Android Studio Run Steps

1. Open Android Studio.
2. Select **File > Open** and open this folder:

   ```text
   C:\Users\DELL\Documents\Sahyadrisamrakshane\2026-05-11\1-the-problem-statement-urban-heat
   ```

3. Let Gradle sync.
4. Use **Embedded JDK** or **JDK 17** as the Gradle JVM.
5. Add your Google Maps key in `local.properties`:

   ```properties
   MAPS_API_KEY=your_google_maps_android_api_key
   ```

6. Keep your Firebase Android app package as:

   ```text
   com.hasiru.usiru
   ```

   The display name is Sahyadri Samrakshane, but the package ID is kept the same so your existing Firebase setup continues to work.

7. Make sure `app/google-services.json` is the real file downloaded from your Firebase project.
8. Run the app on a physical Android phone or an emulator with Google Play services.
9. Allow camera and location permissions.

## Firebase Setup

In Firebase Console:

- Enable **Authentication > Anonymous**.
- Enable **Cloud Firestore**.
- Use test mode for demo/student evaluation.

You do not need to create a collection manually. When the app syncs, it creates:

```text
ecological_alerts
```

## Success Crit eria Mapping

- Works with low signal: every alert is saved to Room first, then WorkManager syncs later when connected.
- GPS coordinates shown: the report screen displays latitude, longitude, and accuracy before submission.
- Nature-friendly UI: the app uses canopy green, moss, mist, and earth colors.
- Firebase dashboard: synced alerts appear in Firestore under `ecological_alerts`.
- Kotlin implementation: app logic, database, sync worker, and screens are written in Kotlin.
