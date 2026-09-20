# SafeExit

SafeExit is a Kotlin Multiplatform emergency communication prototype for Demo University. It connects an administrator web dashboard with a Android app so a team can send a live emergency alert, see members status information, and collect acknowledgements, and guide them to the nearest exit.


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

