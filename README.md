# SafeExit

SafeExit is a small Kotlin Multiplatform emergency communication prototype for Demo University. It has an Android student app and a browser administrator dashboard backed by Firebase Authentication and Cloud Firestore.

## Scope

This milestone supports email/password login, `SAFE101` organization joining, manual text areas, a demonstration floor-plan map with nearest-exit routing, real-time active-emergency listeners, Android local notifications/vibration, emergency acknowledgements, and an administrator student table. It intentionally does not include GPS, sensors, SMS, phone calls, or Cloud Functions.

“SafeExit is a preparedness prototype. Follow official alarms, exit signs, university instructions, and emergency personnel.”

Receiving an alert when the application is completely terminated may require Firebase Cloud Messaging, which is future work.

## Structure

`composeApp/src/commonMain` contains serializable models, validation, repository contracts, and filtering logic. `androidMain` contains Firebase Android implementations, Compose UI, notifications, vibration, and the Android entry point. `jsMain` contains the lightweight Firebase-compat browser dashboard. `firestore.rules` contains the demo security rules.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the presentation architecture, shared-versus-platform responsibilities, and Firebase data flow.

## Firebase setup

1. Register the Android app with package `exit.android` to match the current Firebase configuration. Keep `google-services.json` at `composeApp/google-services.json`; it is ignored and must never be committed.
2. A Web app is configured in `composeApp/src/jsMain/resources/index.html` for the provided `exit-app-6b1f9` Firebase project. These are client settings, not service-account secrets; keep Firestore rules enabled and do not add private keys.
3. In Authentication, enable Email/Password.
4. In Firestore, create these documents (or seed them through the console):
   - `organizations/demo-university`: `{name: "Demo University", code: "SAFE101", activeEmergencyId: null, createdAt: server timestamp}`
   - `organizationCodes/SAFE101`: `{organizationId: "demo-university", active: true}`
5. Create the demo administrator in Authentication. Then create `users/{uid}` with `uid`, `displayName`, `email`, `role: "ADMIN"`, `organizationId: "demo-university"`, `currentArea: "", safetyStatus: "UNKNOWN"`, and timestamps. Public registration always writes `STUDENT`.
6. Deploy rules with `firebase deploy --only firestore:rules,firestore:indexes` after installing/logging into the Firebase CLI.

The Android app currently uses a Firestore real-time listener and local Android notifications. Reliable delivery after Android has completely stopped the app requires Firebase Cloud Messaging and a trusted sender. That paid backend is intentionally postponed for this hackathon prototype because of the billing requirement.

## Run

Install Gradle 8.10+ or use the generated wrapper if your IDE creates one. From the project root:

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:jsBrowserDevelopmentExecutableDistribution
```

Install the resulting Android APK on an emulator/device. Serve the browser output from `composeApp/build/dist/js/productionExecutable` or use the IDE's JS browser run configuration. The exact Gradle task names can vary slightly with the installed Kotlin plugin; IntelliJ's Gradle tool window will show the available `jsBrowser...` task.

## Demo flow

Open the web dashboard with the manually created ADMIN account. Register a student in the Android app using `SAFE101`, choose a text area, and save it. The administrator's real-time table should show the student. Start an emergency, acknowledge it from Android, and end it from the dashboard.

## Safety and future work

Student activity and location information are approximate and must not be treated as an official occupancy record. This prototype is not an emergency dispatch or life-safety system. Future work may add FCM and a trusted sender for terminated-app delivery, stronger auditability, multiple organizations/buildings, and institution-approved operational integrations.
