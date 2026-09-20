package com.safeexit.app

import android.app.*
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.safeexit.shared.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); runCatching { FirebaseApp.initializeApp(this) }; NotificationHelper.createChannel(this); if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 10); setContent { SafeExitApp(this) } }
}

internal class NotificationHelper {
    companion object {
        const val CHANNEL = "safeexit-emergency-v2"
        private val vibrationPattern = longArrayOf(0, 900, 300, 900, 300, 1400)
        fun createChannel(context: Context) { context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Emergency alerts", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true); setVibrationPattern(vibrationPattern); setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()) }) }
        fun alert(context: Context, emergency: Emergency) { val manager = context.getSystemService(NotificationManager::class.java); val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("emergencyId", emergency.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val notification = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("EMERGENCY: ${emergency.type}").setContentText("${emergency.locationText} · Tap for Safe/Need Help").setContentIntent(openApp).setFullScreenIntent(openApp, true).setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX).setAutoCancel(true).setOngoing(false).setStyle(Notification.BigTextStyle().bigText("${emergency.message}\n\nLocation: ${emergency.locationText}\nOpen SafeExit to view the route and respond.")).build(); manager.notify(emergency.id.hashCode(), notification); if (Build.VERSION.SDK_INT >= 26) (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createWaveform(vibrationPattern, -1)) else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(vibrationPattern, -1) }
    }
}

@Composable fun SafeExitApp(context: Context) {
    val auth = remember { FirebaseAuthRepository() }; val users = remember { FirebaseUserRepository() }; val emergencies = remember { FirebaseEmergencyRepository() }; var profile by remember { mutableStateOf<UserProfile?>(null) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { profile = auth.currentUser(); loading = false }
    MaterialTheme { if (loading) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } else if (profile == null) AuthScreen(auth, onSuccess = { profile = it }, onError = { error = it }) else if (profile!!.role == UserRole.ADMIN) AdminScreen(profile!!, emergencies, users, onLogout = { profile = null }) else StudentScreen(profile!!, users, emergencies, context, onLogout = { profile = null }, onError = { error = it }); error?.let { message -> AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("OK") } }, text = { Text(message) }) } }
}

@Composable private fun AuthScreen(auth: FirebaseAuthRepository, onSuccess: (UserProfile) -> Unit, onError: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SafeExit", style = MaterialTheme.typography.headlineLarge)
        Text(if (register) "Student registration" else "Student login")
        if (register) {
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(studentId, { studentId = it }, label = { Text("Student ID (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(code, { code = it }, label = { Text("University code") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                val result = if (register) auth.registerStudent(name, email, password, code, studentId) else auth.signIn(email, password)
                busy = false
                result.onSuccess(onSuccess).onFailure { onError(it.message ?: "Authentication failed") }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Loading…" else if (register) "Create student account" else "Login") }
        if (!register) {
            TextButton(onClick = {
                scope.launch { auth.sendPasswordReset(email).onSuccess { onError("Password reset email sent.") }.onFailure { onError(it.message ?: "Could not send reset email") } }
            }) { Text("Forgot password?") }
        }
        TextButton(onClick = { register = !register }) { Text(if (register) "Already registered? Student login" else "New student? Register") }
        Text("Administrators use the web dashboard. Students use this Android app.", color = Color.Gray)
    }
}

@Composable private fun StudentScreen(profile: UserProfile, users: FirebaseUserRepository, emergencies: FirebaseEmergencyRepository, context: Context, onLogout: () -> Unit, onError: (String) -> Unit) {
    val areas = listOf("Room 101", "Room 102", "Library", "Room 3", "Main Hall", "Room 5", "Outside", "Other")
    var selectedArea by remember { mutableStateOf(if (areas.contains(profile.currentArea)) profile.currentArea else if (profile.currentArea.isBlank()) "" else "Other") }; var customArea by remember { mutableStateOf(if (selectedArea == "Other") profile.currentArea else "") }; var menuOpen by remember { mutableStateOf(false) }; var active by remember { mutableStateOf<Emergency?>(null) }; var handledKey by remember { mutableStateOf<String?>(null) }; var dismissedEmergencyId by remember { mutableStateOf(context.getSharedPreferences("safeexit_alerts", Context.MODE_PRIVATE).getString("dismissedEmergencyId", null)) }; var acknowledged by remember { mutableStateOf<SafetyStatus?>(null) }; var savedMessage by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    LaunchedEffect(profile.organizationId, dismissedEmergencyId) { emergencies.observeActiveEmergency(profile.organizationId).collect { emergency -> active = emergency?.takeUnless { it.id == dismissedEmergencyId }; if (emergency != null && emergency.id != dismissedEmergencyId && handledKey != "${emergency.id}:${emergency.version}") { handledKey = "${emergency.id}:${emergency.version}"; NotificationHelper.alert(context, emergency) } } }
    LaunchedEffect(Unit) { while (true) { users.updateLastSeen(); delay(60_000) } }
    val currentArea = if (selectedArea == "Other") customArea.trim() else selectedArea
    if (active != null) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("EMERGENCY", color = Color.Red, style = MaterialTheme.typography.displaySmall); Text(active!!.type.name.replace('_', ' '), style = MaterialTheme.typography.headlineMedium); Text("Location: ${active!!.locationText}", style = MaterialTheme.typography.titleLarge); Text(active!!.message, style = MaterialTheme.typography.bodyLarge); Text("Nearest exit: ${active!!.recommendedExit ?: "Follow official instructions"}", style = MaterialTheme.typography.titleMedium, color = Color(0xFFB3261E)); StudentMapPreview(currentArea, active!!.recommendedExit, active!!.blockedExits); Text("Your current area: ${currentArea.ifBlank { "Not entered" }}"); Text(SAFETY_DISCLAIMER, color = Color.DarkGray); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { Button({ val emergencyId = active!!.id; scope.launch { emergencies.acknowledgeEmergency(emergencyId, SafetyStatus.SAFE, currentArea).onSuccess { acknowledged = SafetyStatus.SAFE; dismissedEmergencyId = emergencyId; context.getSharedPreferences("safeexit_alerts", Context.MODE_PRIVATE).edit().putString("dismissedEmergencyId", emergencyId).apply(); active = null }.onFailure { onError(it.message ?: "Could not acknowledge") } } }, modifier = Modifier.weight(1f)) { Text("I Am Safe") }; Button({ val emergencyId = active!!.id; scope.launch { emergencies.acknowledgeEmergency(emergencyId, SafetyStatus.NEEDS_HELP, currentArea).onSuccess { acknowledged = SafetyStatus.NEEDS_HELP; dismissedEmergencyId = emergencyId; context.getSharedPreferences("safeexit_alerts", Context.MODE_PRIVATE).edit().putString("dismissedEmergencyId", emergencyId).apply(); active = null }.onFailure { onError(it.message ?: "Could not acknowledge") } } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("I Need Help") } }; acknowledged?.let { Text("Your response was recorded: ${it.name}", color = if (it == SafetyStatus.SAFE) Color(0xFF2E7D32) else Color.Red) }; Text("Follow official alarms, exit signs, university instructions, and emergency personnel.", color = Color.DarkGray); Spacer(Modifier.weight(1f)); TextButton({ FirebaseAuthRepository().signOut(); onLogout() }) { Text("Logout") } }
    } else Column(Modifier.padding(22.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("SafeExit", style = MaterialTheme.typography.headlineLarge); Text("${profile.displayName} · $DEMO_ORGANIZATION_NAME"); Text("SYSTEM STATUS: NORMAL", color = Color(0xFF2E7D32), style = MaterialTheme.typography.titleLarge); Text("Select your current area so the administrator can coordinate the response.", color = Color.DarkGray); Box { OutlinedButton({ menuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedArea.isBlank()) "Select current area" else selectedArea) }; DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { areas.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { selectedArea = item; menuOpen = false }) } } }; if (selectedArea == "Other") OutlinedTextField(customArea, { customArea = it }, label = { Text("Enter your area") }, modifier = Modifier.fillMaxWidth()); Button({ scope.launch { users.updateCurrentArea(currentArea).onSuccess { savedMessage = "Area updated" }.onFailure { onError(it.message ?: "Could not update area") } } }, modifier = Modifier.fillMaxWidth()) { Text("Save current area") }; savedMessage?.let { Text(it, color = Color(0xFF2E7D32)) }; Text("Your current map", style = MaterialTheme.typography.titleMedium); StudentMapPreview(currentArea, null, null); Spacer(Modifier.height(10.dp)); Text(SAFETY_DISCLAIMER, color = Color.Gray); Spacer(Modifier.weight(1f)); TextButton({ FirebaseAuthRepository().signOut(); onLogout() }) { Text("Logout") } }
}

@Composable private fun AdminScreen(profile: UserProfile, emergencies: FirebaseEmergencyRepository, users: FirebaseUserRepository, onLogout: () -> Unit) {
    var students by remember { mutableStateOf(emptyList<UserProfile>()) }; var active by remember { mutableStateOf<Emergency?>(null) }; var type by remember { mutableStateOf(EmergencyType.FIRE) }; var location by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var filter by remember { mutableStateOf(StudentFilter.ALL) }; val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { users.observeStudents(profile.organizationId).collect { students = it } }; LaunchedEffect(Unit) { emergencies.observeActiveEmergency(profile.organizationId).collect { active = it } }
    Column(Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("SafeExit Admin", style = MaterialTheme.typography.headlineLarge); TextButton({ FirebaseAuthRepository().signOut(); onLogout() }) { Text("Logout") } }; Text("$DEMO_ORGANIZATION_NAME · ${profile.displayName}"); Text("Student activity and location information are approximate and must not be treated as an official occupancy record.", color = Color.Gray); if (active == null) { Text("Start Emergency", style = MaterialTheme.typography.titleLarge); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EmergencyType.values().forEach { item -> FilterChip(type == item, { type = item }, label = { Text(item.name) }) } }; OutlinedTextField(location, { location = it }, label = { Text("Emergency location") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(message, { message = it }, label = { Text("Administrator message") }, modifier = Modifier.fillMaxWidth()); Button({ scope.launch { emergencies.createEmergency(profile.organizationId, type, location, message) } }) { Text("Send Emergency Alert") } } else { Text("ACTIVE EMERGENCY", color = Color.Red, style = MaterialTheme.typography.titleLarge); Text("${active!!.type} · ${active!!.locationText}: ${active!!.message}"); Button({ scope.launch { emergencies.endEmergency(active!!.id) } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("End Emergency") } }; Text("Students: ${students.size} · Safe ${students.count { it.safetyStatus == SafetyStatus.SAFE }} · Waiting ${students.count { it.safetyStatus == SafetyStatus.WAITING }} · Needs help ${students.count { it.safetyStatus == SafetyStatus.NEEDS_HELP }}"); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { StudentFilter.values().forEach { item -> FilterChip(filter == item, { filter = item }, label = { Text(item.name.replace('_', ' ')) }) } }; LazyColumn { items(students.filterFor(filter)) { student -> Text("${student.displayName} | ${student.studentId ?: "—"} | ${student.currentArea.ifBlank { "—" }} | ${student.safetyStatus} | ${if (student.isRecentlyActive()) "Recently Active" else "Not Recently Active"}", modifier = Modifier.padding(vertical = 5.dp)) } } }
}

@Composable private fun StudentMapPreview(currentArea: String, recommendedExit: String?, blockedExitsText: String?, exactPoint: MapPoint? = null) {
    val locations = mapOf("Room 101" to MapPoint(10.0, 8.0), "Room 102" to MapPoint(30.0, 8.0), "Library" to MapPoint(55.0, 10.0), "Room 3" to MapPoint(10.0, 24.0), "Main Hall" to MapPoint(41.5, 18.0), "Room 5" to MapPoint(48.0, 25.0), "Outside" to MapPoint(41.5, 2.0))
    val user = exactPoint ?: locations[currentArea] ?: locations.getValue("Room 101")
    val blocked = blockedExitsText.orEmpty().split(",").filter { it.isNotBlank() }.toSet()
    val route = SafeExitRouter(SafeExitMapState(availableExits = SafeExitMapDefaults.EXIT_IDS.filter { it !in blocked }.toSet())).calculateRoute(user) as? RoutingResult.Success
    val mapLeft = 0.0
    val mapTop = 0.0
    val mapWidth = 63.0
    val mapHeight = 30.0
    Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.width(300.dp).height(143.dp)) {
        val scale = minOf(size.width.toDouble() / mapWidth, size.height.toDouble() / mapHeight).toFloat()
        val offsetX = (size.width - (mapWidth * scale).toFloat()) / 2f
        val offsetY = (size.height - (mapHeight * scale).toFloat()) / 2f
        fun screen(point: MapPoint) = androidx.compose.ui.geometry.Offset(offsetX + ((point.x - mapLeft) * scale).toFloat(), offsetY + ((point.y - mapTop) * scale).toFloat())
        fun rect(left: Double, top: Double, width: Double, height: Double, color: Color) = drawRect(color, topLeft = screen(MapPoint(left, top)), size = androidx.compose.ui.geometry.Size((width * scale).toFloat(), (height * scale).toFloat()))
        drawRect(Color(0xFFF8FAFC)); rect(0.0, 0.0, 20.0, 14.0, Color(0xFFE1EEFF)); rect(20.0, 0.0, 20.0, 14.0, Color(0xFFE1EEFF)); rect(0.0, 16.0, 20.0, 14.0, Color(0xFFE1EEFF)); rect(20.0, 16.0, 20.0, 14.0, Color(0xFFE1EEFF)); rect(43.0, 0.0, 20.0, 20.0, Color(0xFFEFE8FF)); rect(43.0, 22.0, 10.0, 8.0, Color(0xFFEFE8FF)); rect(53.0, 22.0, 10.0, 8.0, Color(0xFFEFE8FF)); rect(0.0, 14.0, 40.0, 2.0, Color(0xFFFFF7CC)); rect(40.0, 0.0, 3.0, 30.0, Color(0xFFFFF7CC)); rect(43.0, 20.0, 20.0, 2.0, Color(0xFFFFF7CC))
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(55, 65, 81); textSize = 7f; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL) }
        fun label(text: String, point: MapPoint) { val position = screen(point); drawContext.canvas.nativeCanvas.drawText(text, position.x, position.y, labelPaint) }
        label("Room 101", MapPoint(10.0, 7.5)); label("Room 102", MapPoint(30.0, 7.5)); label("Room 3", MapPoint(10.0, 24.0)); label("Room 4", MapPoint(30.0, 24.0)); label("Library", MapPoint(53.0, 10.0)); label("Room 5", MapPoint(48.0, 26.0)); label("Toilet", MapPoint(58.0, 26.0))
        drawLine(Color(0xFF334155), screen(MapPoint(20.0, 0.0)), screen(MapPoint(20.0, 14.0)), strokeWidth = 1.5f); drawLine(Color(0xFF334155), screen(MapPoint(20.0, 16.0)), screen(MapPoint(20.0, 30.0)), strokeWidth = 1.5f); drawLine(Color(0xFF334155), screen(MapPoint(53.0, 22.0)), screen(MapPoint(53.0, 30.0)), strokeWidth = 1.5f)
        route?.let { path -> val points = listOf(path.userLocation) + path.path.drop(1).mapNotNull { SafeExitMapDefaults.vertexById[it]?.position }; points.zipWithNext().forEach { (from, to) -> drawLine(Color(0xFF1677FF), screen(from), screen(to), strokeWidth = 2f) } }
        SafeExitMapDefaults.vertices.filter { it.type == MapVertexType.DOOR || it.type == MapVertexType.JUNCTION }.forEach { vertex -> drawCircle(if (vertex.type == MapVertexType.DOOR) Color.Black else Color(0xFFF97316), radius = if (vertex.type == MapVertexType.DOOR) 1.5f else 2f, center = screen(vertex.position)) }
        SafeExitMapDefaults.EXIT_IDS.forEach { exitId -> val point = SafeExitMapDefaults.vertexById.getValue(exitId).position; val available = exitId !in blocked; drawCircle(if (!available) Color.Red else if (exitId == recommendedExit) Color(0xFF16A34A) else Color(0xFF64748B), radius = 4f, center = screen(point)) }
        drawCircle(Color(0xFFB3261E), radius = 5f, center = screen(user))
        }
    }
}
