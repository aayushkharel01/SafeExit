# SafeExit

SafeExit is a Kotlin Multiplatform emergency communication prototype for Demo University. It connects an administrator web dashboard with a student Android app so a university team can send a live emergency alert, see student status information, and collect acknowledgements.

The project shares its core models, validation, routing geometry, repository contracts, and filtering logic in `commonMain`, while keeping Android notifications and Firebase Android access platform-specific.

## What the prototype does

### Administrator web dashboard

- Administrator email/password login.
- Access control based on the Firestore `ADMIN` role.
- Live list of students connected to `demo-university`.
- Student area, safety status, last-seen time, and acknowledgement state.
- Emergency creation with type, location text, and message.
- Active emergency panel and end-emergency control.
- Counts for registered, recently active, waiting, safe, and needs-help students.
- Compact full-building 2D map with simple text locations and evacuation route display.
- Exit availability controls for the demonstration map.

### Student Android app

- Student registration using display name, email, password, organization code, and optional student ID.
- Student login and password-reset email.
- `SAFE101` joins the student to Demo University.
- Manual current-area selection, including Room 101, Room 102, Library, Main Hall, Room 5, Outside, and Other.
- Compact static building map showing the student’s selected area and recommended route.
- Real-time Firestore emergency listener.
- Android emergency notification with vibration and default notification sound.
- Emergency details screen with `I Am Safe` and `I Need Help` actions.
- Acknowledgement saved to Firestore for the administrator.

## Current scope and limitations

This is a working hackathon demonstration, not a certified emergency or occupancy system.

Not implemented in this milestone:

- GPS or continuous location tracking.
- Maps from a mapping provider.
- Outdoor or indoor route navigation.
- Fire-alarm sensors.
- SMS, phone calls, or automatic ringing.
- Firebase Cloud Messaging or Cloud Functions.
- Multiple universities or buildings.

The student app uses a Firestore real-time listener while it is running or recently backgrounded. Receiving an alert when the application is completely terminated may require Firebase Cloud Messaging, which is future work.

“SafeExit is a preparedness prototype. Follow official alarms, exit signs, university instructions, and emergency personnel.”

## Technology and architecture

```text
SafeExit/
├── composeApp/
│   ├── src/commonMain/       Shared models, validation, routing, contracts
│   ├── src/commonTest/       Shared deterministic tests
│   ├── src/androidMain/      Android Compose UI, Firebase, notifications
│   └── src/jsMain/           Browser administrator dashboard
├── firestore.rules            Firestore access control
├── firestore.indexes.json     Firestore query indexes
├── ARCHITECTURE.md             Presentation architecture notes
└── gradlew                     Gradle wrapper
```

Main shared files:

- `Models.kt` — users, emergencies, acknowledgements, roles, and statuses.
- `Repositories.kt` — authentication, user, and emergency repository contracts.
- `SafeExitRouting.kt` — metric floor-plan geometry, graph, and route calculation.
- `Validation.kt` — registration and emergency validation.
- `DashboardLogic.kt` — administrator student filtering and active-state helpers.

Android Firebase implementations are in `composeApp/src/androidMain/kotlin/com/safeexit/app/FirebaseRepositories.kt`. The Android UI and local notification behavior are in `MainActivity.kt`. The browser dashboard is in `composeApp/src/jsMain/kotlin/com/safeexit/web/Main.kt` and its browser shell/styles are in `composeApp/src/jsMain/resources/index.html`.

## Firebase setup

### Create Firebase services

In the Firebase Console:

1. Enable **Authentication → Sign-in method → Email/Password**.
2. Create a **Cloud Firestore** database.
3. Register an Android app with package name `exit.android`.
4. Download `google-services.json` and place it at `composeApp/google-services.json`. This file is ignored by Git and must never be committed.
5. Register a Firebase Web app. Browser Firebase settings are in `composeApp/src/jsMain/resources/index.html`. Do not add service-account keys or private credentials.
6. Deploy rules and indexes:

```bash
firebase login
firebase use exit-app-6b1f9
firebase deploy --only firestore:rules,firestore:indexes
```

### Create the demo organization

Create these Firestore documents:

```text
organizations/demo-university
  name: "Demo University"
  code: "SAFE101"
  activeEmergencyId: null
  createdAt: server timestamp

organizationCodes/SAFE101
  organizationId: "demo-university"
  active: true
```

### Create the administrator

1. Go to **Authentication → Users → Add user**.
2. Create an administrator email and password.
3. Copy the new user UID.
4. Create `users/{uid}` in Firestore:

```text
uid: "the-authentication-uid"
displayName: "SafeExit Administrator"
email: "administrator@example.com"
role: "ADMIN"
organizationId: "demo-university"
studentId: null
currentArea: ""
safetyStatus: "UNKNOWN"
lastSeenAt: server timestamp
createdAt: server timestamp
```

There is no public administrator registration. Student registration always creates the `STUDENT` role.

## Run locally

From the project root:

### Android student app

Connect an Android phone with USB debugging enabled or start an emulator:

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug
```

APK location:

```text
composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Register a student with `SAFE101`, select an area, and keep the app open for the real-time demonstration.

### Administrator web dashboard

Run the development web server from IntelliJ’s Gradle tool window, or try:

```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

If that task is unavailable, build the browser bundle:

```bash
./gradlew :composeApp:jsBrowserDevelopmentWebpack
```

The development dashboard is normally available at `http://localhost:8082/`.

Students use the Android app. The browser page is for administrators.

## Judge demonstration flow

1. Open the administrator dashboard.
2. Log in using the manually created `ADMIN` account.
3. Open the Android app on a second device or emulator.
4. Register a student with `SAFE101`.
5. Select an area and save it.
6. Confirm the student appears in the administrator dashboard.
7. Start an emergency with a type, location text, and message.
8. Confirm the Android device receives a notification and vibration.
9. Open the emergency screen and choose `I Am Safe` or `I Need Help`.
10. Confirm the acknowledgement appears in the dashboard.
11. End the emergency from the dashboard.

## Deploy the administrator dashboard to Vercel

Vercel hosts the generated static Compose for Web files. Firebase Authentication and Firestore continue to run directly from the browser.

1. Push this repository to GitHub.
2. Open [vercel.com](https://vercel.com) and choose **Add New → Project**.
3. Import `aayushkharel01/SafeExit`.
4. Choose **Other** as the framework preset.
5. Use:

```text
Build command: bash ./vercel-build.sh
Output directory: vercel-output
Install command: leave blank
```

6. Deploy and open the generated `https://...vercel.app` address.

The repository includes `vercel.json` and `vercel-build.sh`. The script builds the production Compose for Web bundle and combines the generated JavaScript, HTML, and Skiko runtime files into `vercel-output`.

If you want to verify the output locally:

```bash
./vercel-build.sh
find vercel-output -maxdepth 1 -type f -print
```

The Firebase Web configuration is compiled into the browser `index.html`, and Firestore rules must already be deployed. Vercel only hosts the administrator website; it does not package the Android app.

## Android APK QR code

For a local demo, serve the APK from the development computer:

```bash
python3 -m http.server 8765 \
  --bind 0.0.0.0 \
  --directory composeApp/build/outputs/apk/debug
```

The current local URL is:

```text
http://10.228.249.19:8765/composeApp-debug.apk
```

The phone and computer must be on the same Wi-Fi. A local QR code will not work for judges outside that network; upload the APK to a trusted file host or use a release artifact for remote judging.

## Testing

```bash
./gradlew :composeApp:test
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:jsBrowserDevelopmentWebpack
```

Tests cover validation, student filtering, routing geometry, exit availability, and emergency state behavior.

## Safety disclaimer

SafeExit is a preparedness prototype. Student activity and location information are approximate and must not be treated as an official occupancy record. This software must not replace official emergency systems, alarms, evacuation procedures, university instructions, or emergency personnel.

## Future work

- Firebase Cloud Messaging for terminated-app alert delivery.
- Institution-approved background services and audit logging.
- Multiple buildings, floors, and organizations.
- Real campus map calibration and indoor positioning.
- Institution-approved emergency integrations.
