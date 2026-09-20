# SafeExit Architecture

SafeExit is a Kotlin Multiplatform prototype with two user-facing targets:

```text
                         Firebase
          Authentication · Cloud Firestore · Rules
                    Real-time document listeners
                              ▲
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                             │
┌───────┴────────┐                           ┌────────┴────────┐
│ Android target │                           │ Web target      │
│ Student app    │                           │ Admin dashboard │
│ Kotlin/Compose│                           │ Kotlin/JS       │
└───────┬────────┘                           └────────┬────────┘
        │                                             │
        └────────────── commonMain Kotlin ────────────┘
             Models · validation · contracts · state logic
```

## Shared Kotlin layer

Location: `composeApp/src/commonMain/kotlin/com/safeexit/shared`

`commonMain` contains the logic that should be identical on every platform:

- `Models.kt`: serializable users, emergencies, acknowledgements, roles, and statuses.
- `Repositories.kt`: platform-independent authentication, user, and emergency contracts.
- `Validation.kt`: registration and emergency-form validation.
- `DashboardLogic.kt`: student filtering, recent-activity rules, and shared application decisions.

The shared layer does not depend on Android APIs, browser DOM APIs, or Firebase SDK classes. This keeps the core logic portable and testable.

## Android target

Location: `composeApp/src/androidMain`

The Android target contains the native student experience:

- `MainActivity.kt`: Compose UI, login, registration, student home, emergency details, acknowledgement actions, local notification, vibration, sound, and session restoration.
- `FirebaseRepositories.kt`: Android Firebase Authentication and Firestore implementations of the shared repository contracts.
- `AndroidManifest.xml`: Android notification and vibration permissions.

Firebase-specific code stays in `androidMain` because Firebase Android APIs, notification APIs, and vibration APIs are platform-specific.

## Web target

Location: `composeApp/src/jsMain`

The web target contains the administrator experience:

- `web/Main.kt`: Kotlin/JS login, administrator access control, live student table, emergency creation/ending, filters, summary cards, and acknowledgement monitoring.
- `resources/index.html`: browser shell, Firebase web SDK loading, and dashboard styling.

The web dashboard uses Kotlin/JS and the Firebase browser SDK. It does not contain private server credentials.

## Firebase data flow

```text
Student Android app
  ├─ Firebase Auth: student account/session
  ├─ Firestore: profile, area, last-seen time, acknowledgement
  └─ Listener: active emergency for the student's organization

Administrator Kotlin/JS dashboard
  ├─ Firebase Auth: administrator login
  ├─ Firestore listener: students and acknowledgements
  ├─ Firestore write: create active emergency
  └─ Firestore write: end emergency
```

Firestore security rules enforce organization boundaries and role restrictions. Students can manage only their own profile and acknowledgement. Administrators can manage emergency documents and view students in their organization.

## Current boundary

The prototype uses Firestore listeners and Android local notifications while the student application process is available. Reliable delivery after Android has completely stopped the application requires Firebase Cloud Messaging plus a trusted sender. That backend is intentionally postponed because it requires a paid Firebase billing plan.

GPS, sensors, SMS, phone calls, and exact occupancy tracking are not part of this milestone. The current map is a manually configured demonstration floor plan, not a real building map.

## Map phase

The supplied demonstration floor plan is represented in shared Kotlin by `SafeExitRouting.kt`. The administrator dashboard renders it as a full-width floor-plan operations view with exit controls and nearest-exit routing. The Android emergency screen renders a larger student minimap using the same selected area, route, recommended exit, and blocked-exit information. Student locations remain manually selected areas until GPS or indoor positioning is deliberately added. Additional floors should be added as separately surveyed map data rather than invented coordinates.

## Why this demonstrates Kotlin Multiplatform

- The Android and web applications are both driven by Kotlin.
- Models and validation are written once in `commonMain`.
- Repository contracts are shared while Firebase implementations remain platform-specific.
- Android retains native notification and vibration behavior.
- The web dashboard retains browser-native deployment while reusing shared Kotlin logic.
- Tests target the shared layer rather than duplicating platform tests for basic business rules.
