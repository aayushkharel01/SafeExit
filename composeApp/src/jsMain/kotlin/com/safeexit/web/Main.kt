package com.safeexit.web

import com.safeexit.shared.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLInputElement

private val firebase: dynamic = js("window.firebase")
private fun input(id: String): dynamic = document.getElementById(id)!!.asDynamic()
private fun fieldText(id: String): String = input(id).value as? String ?: ""
private fun set(id: String, value: String) { document.getElementById(id)!!.textContent = value }
private fun textValue(value: dynamic, fallback: String = ""): String = value as? String ?: fallback
private fun showToast(title: String, message: String) { val toast = document.getElementById("alertToast") ?: return; toast.innerHTML = "<strong>$title</strong><span>$message</span><small>Map preview: location text only in this prototype.</small>"; toast.removeAttribute("hidden"); window.setTimeout({ toast.setAttribute("hidden", "true") }, 7000) }
private fun firestoreObject(): dynamic = js("({})")
private val mapAreas = linkedMapOf("Room 101" to MapPoint(10.0, 8.0), "Room 102" to MapPoint(30.0, 8.0), "Library" to MapPoint(55.0, 10.0), "Room 3" to MapPoint(10.0, 24.0), "Main Hall" to MapPoint(41.5, 18.0), "Room 5" to MapPoint(48.0, 25.0), "Outside" to MapPoint(41.5, 2.0))
private fun routePoints(route: SafeExitRoute?): List<MapPoint> = route?.let { listOf(it.userLocation) + it.path.drop(1).mapNotNull { id -> SafeExitMapDefaults.vertexById[id]?.position } } ?: emptyList()
private fun pointAtProgress(points: List<MapPoint>, progress: Double): MapPoint {
    if (points.isEmpty()) return MapPoint(0.0, 0.0)
    if (points.size == 1) return points.first()
    val lengths = points.zipWithNext().map { kotlin.math.hypot(it.second.x - it.first.x, it.second.y - it.first.y) }
    val total = lengths.sum(); var target = total * progress.coerceIn(0.0, 1.0)
    points.zip(lengths).forEach { (point, length) -> if (target <= length) return MapPoint(point.x + (points[points.indexOf(point) + 1].x - point.x) * (target / length), point.y + (points[points.indexOf(point) + 1].y - point.y) * (target / length)); target -= length }
    return points.last()
}
private fun polyline(points: List<MapPoint>, css: String): String = if (points.size < 2) "" else "<polyline points='${points.joinToString(" ") { "${it.x},${it.y}" }}' class='$css'/>"
private fun mapSvg(state: SafeExitMapState, route: SafeExitRoute?, selected: MapPoint, showGraph: Boolean, progress: Double): String {
    val points = routePoints(route); val simulated = pointAtProgress(points, progress); val completed = if (points.size < 2) emptyList() else listOf(points.first(), simulated); val remaining = if (points.size < 2) emptyList() else listOf(simulated) + points.drop(1)
    val graph = if (showGraph) SafeExitMapDefaults.weightedEdges.joinToString("") { edge -> val a = SafeExitMapDefaults.vertexById.getValue(edge.from).position; val b = SafeExitMapDefaults.vertexById.getValue(edge.to).position; "<line x1='${a.x}' y1='${a.y}' x2='${b.x}' y2='${b.y}' class='map-edge'/>" } else ""
    val exits = SafeExitMapDefaults.EXIT_IDS.joinToString("") { id -> val point = SafeExitMapDefaults.vertexById.getValue(id).position; val available = id in state.availableExits; "<circle cx='${point.x}' cy='${point.y}' r='.75' class='${if (available) "map-exit" else "map-blocked"}'/><text x='${point.x}' y='${point.y - 1.15}' class='map-label'>${if (id == "N") "N / ENTRANCE" else "EXIT $id"}</text>" }
    val vertices = SafeExitMapDefaults.vertices.filter { it.type != MapVertexType.EXIT }.joinToString("") { vertex -> val p = vertex.position; if (vertex.id in state.unsafeVertices) "<text x='${p.x}' y='${p.y + 1}' class='unsafe-mark' data-vertex='${vertex.id}'>×</text>" else "<circle cx='${p.x}' cy='${p.y}' r='.3' class='${if (vertex.type == MapVertexType.JUNCTION) "map-junction" else "map-door"}' data-vertex='${vertex.id}'/><text x='${p.x}' y='${p.y - .65}' class='map-door-label'>${vertex.id}</text>" }
    val rooms = "<rect x='0' y='0' width='20' height='14' class='map-room'/><rect x='20' y='0' width='20' height='14' class='map-room'/><rect x='0' y='16' width='20' height='14' class='map-room'/><rect x='20' y='16' width='20' height='14' class='map-room'/><rect x='43' y='0' width='20' height='20' class='map-room alt'/><rect x='43' y='22' width='10' height='8' class='map-room alt'/><rect x='53' y='22' width='10' height='8' class='map-room alt'/><rect x='0' y='14' width='40' height='2' class='map-hall'/><rect x='40' y='0' width='3' height='30' class='map-hall'/><rect x='43' y='20' width='20' height='2' class='map-hall'/><line x1='20' y1='0' x2='20' y2='14' class='map-wall'/><line x1='20' y1='16' x2='20' y2='30' class='map-wall'/><line x1='53' y1='22' x2='53' y2='30' class='map-wall'/><text x='10' y='7' class='map-room-label'>ROOM 1</text><text x='30' y='7' class='map-room-label'>ROOM 2</text><text x='53' y='9' class='map-room-label'>LIBRARY</text><text x='10' y='24' class='map-room-label'>ROOM 3</text><text x='30' y='24' class='map-room-label'>ROOM 4</text><text x='48' y='26' class='map-room-label'>ROOM 5</text><text x='58' y='26' class='map-room-label'>TOILET</text><text x='41.5' y='17' class='map-room-label'>MAIN HALL</text>"
    val body = "<rect x='0' y='0' width='63' height='30' class='map-bg'/>$rooms$graph${polyline(points, "map-route")}$vertices$exits<circle cx='${simulated.x}' cy='${simulated.y}' r='.7' class='map-user'/><text x='${simulated.x}' y='${simulated.y - 1}' class='map-label'>YOU</text>"
    val main = "<svg id='floorMap' class='map-svg' viewBox='0 0 63 30' role='img' aria-label='SafeExit floor plan'>$body</svg>"
    val left = (simulated.x - 5.0).coerceIn(0.0, 53.0); val top = (simulated.y - 5.0).coerceIn(0.0, 20.0)
    val sub = "<svg id='subMap' class='sub-map-svg' viewBox='$left $top 10 10' role='img' aria-label='User sub-map'>$body</svg>"
    return "<div class='map-main-viewport'>$main</div>"
}

fun main() {
    document.getElementById("app")!!.innerHTML = """
      <main class="shell"><section id="login" class="card login-card"><h1>SafeExit Admin</h1><p class="muted">Demo University administrator dashboard</p><input id="email" placeholder="Administrator email" type="email"><input id="password" placeholder="Password" type="password"><button id="loginButton">Login as administrator</button><button id="googleButton">Continue with Google</button><button id="forgotPassword" class="secondary">Forgot password?</button><p class="muted">Administrator accounts are created manually in Firebase. Students register and log in from the Android app.</p><p id="error" class="error"></p></section>
      <section id="studentView" class="card" hidden><h1>Student home</h1><p id="studentName"></p><p class="status">Current system status: Normal</p><p>Demo University</p><button id="studentLogout">Logout</button></section>
      <section id="dashboard" class="card" hidden><h1>SafeExit Admin</h1><p id="adminName"></p><button id="logout">Logout</button><h2>Start Emergency</h2>
      <select id="type"><option>FIRE</option><option>GAS_LEAK</option><option>SECURITY</option><option>MEDICAL</option><option>OTHER</option></select><input id="location" placeholder="Emergency location"><textarea id="message" placeholder="Message"></textarea><button id="send">Send Emergency Alert</button><div id="active"></div><h2>Students</h2><p>Student activity and location information are approximate and must not be treated as an official occupancy record.</p><div id="students"></div></section></main>
    """.trimIndent()
    document.getElementById("loginButton")!!.addEventListener("click", { login(true) })
    document.getElementById("googleButton")!!.addEventListener("click", { googleLogin(true) })
    document.getElementById("forgotPassword")!!.addEventListener("click", { resetPassword() })
}

private fun registerStudent() {
    val displayName = (document.getElementById("displayName")!!.unsafeCast<HTMLInputElement>()).value.trim()
    val studentId = (document.getElementById("studentId")!!.unsafeCast<HTMLInputElement>()).value.trim()
    val email = (document.getElementById("registerEmail")!!.unsafeCast<HTMLInputElement>()).value.trim()
    val password = (document.getElementById("registerPassword")!!.unsafeCast<HTMLInputElement>()).value
    val code = (document.getElementById("organizationCode")!!.unsafeCast<HTMLInputElement>()).value.trim().uppercase()
    if (displayName.isBlank() || email.isBlank() || password.isBlank() || code != DEMO_ORGANIZATION_CODE) { set("registerError", "Enter your name, email, password, and the SAFE101 university code."); return }
    val auth = firebase.auth(); auth.createUserWithEmailAndPassword(email, password).then { result: dynamic ->
        val uid = result.user.uid
        val profile = firestoreObject(); profile.uid = uid; profile.displayName = displayName; profile.email = email; profile.role = "STUDENT"; profile.organizationId = DEMO_ORGANIZATION_ID; profile.studentId = studentId.ifBlank { null }; profile.currentArea = ""; profile.safetyStatus = "UNKNOWN"; profile.lastSeenAt = firebase.firestore.FieldValue.serverTimestamp(); profile.createdAt = firebase.firestore.FieldValue.serverTimestamp()
        firebase.firestore().collection("users").doc(uid).set(profile).then { showStudent(profile) }
    }.catch { error: dynamic -> set("registerError", error.message ?: "Could not create account") }
}

private fun resetPassword() {
    val email = input("email").value.trim()
    if (email.isBlank()) { set("error", "Enter your email first, then select Forgot password."); return }
    firebase.auth().sendPasswordResetEmail(email).then { set("error", "Password reset email sent. Check your inbox.") }.catch { error: dynamic -> set("error", error.message ?: "Could not send reset email") }
}

private fun googleLogin(administrator: Boolean) {
    val auth = firebase.auth(); val provider = firebase.auth.GoogleAuthProvider()
    auth.signInWithPopup(provider).then { result: dynamic ->
        val user = result.user; val ref = firebase.firestore().collection("users").doc(user.uid)
        ref.get().then { snap: dynamic ->
            if (!snap.exists) {
                if (administrator) { auth.signOut(); set("error", "Administrator Google accounts must be created manually in Firebase first.") }
                else { val profile = firestoreObject(); profile.uid = user.uid; profile.displayName = user.displayName ?: "Student"; profile.email = user.email; profile.role = "STUDENT"; profile.organizationId = DEMO_ORGANIZATION_ID; profile.studentId = null; profile.currentArea = ""; profile.safetyStatus = "UNKNOWN"; profile.lastSeenAt = firebase.firestore.FieldValue.serverTimestamp(); profile.createdAt = firebase.firestore.FieldValue.serverTimestamp(); ref.set(profile).then { showStudent(profile) } }
            } else { val profile = snap.data(); val expected = if (administrator) "ADMIN" else "STUDENT"; if (profile.role != expected) { auth.signOut(); set("error", "This Google account does not have the selected role.") } else if (administrator) showDashboard(user, profile) else showStudent(profile) }
        }
    }.catch { error: dynamic -> set("error", error.message ?: "Google sign-in failed") }
}

private fun login(administrator: Boolean) {
    val auth = firebase.auth(); auth.signInWithEmailAndPassword(input("email").value, input("password").value).then { result: dynamic ->
        firebase.firestore().collection("users").doc(result.user.uid).get().then { snap: dynamic ->
            val data = snap.data(); val expectedRole = if (administrator) "ADMIN" else "STUDENT"; if (data.role != expectedRole) { auth.signOut(); set("error", if (administrator) "Access denied: this account is not an administrator." else "This account is not a student account.") } else if (administrator) showDashboard(result.user, data) else showStudent(data)
        }
    }.catch { error: dynamic -> set("error", if (error.code == "auth/invalid-credential" || error.code == "auth/wrong-password") "Email/password is incorrect. If this administrator was created with Google, use Continue with Google, or reset the password." else error.message ?: "Login failed") }
}

private fun showStudent(profile: dynamic) {
    document.getElementById("login")!!.setAttribute("hidden", "true")
    document.getElementById("studentView")!!.removeAttribute("hidden")
    set("studentName", "Welcome, ${profile.displayName}. Your student login is working.")
    document.getElementById("studentLogout")!!.addEventListener("click", { firebase.auth().signOut(); window.location.reload() })
}

private fun showDashboard(user: dynamic, profile: dynamic) {
    document.getElementById("login")!!.setAttribute("hidden", "true")
    val dashboard = document.getElementById("dashboard")!!
    dashboard.removeAttribute("hidden")
    dashboard.innerHTML = """
      <div class="admin-shell">
        <header class="admin-header"><div><div class="eyebrow">SAFEXIT ADMIN</div><h1>Emergency operations dashboard</h1><p class="muted">Demo University · <span id="adminName"></span></p></div><div class="header-actions"><span class="connection"><span class="dot"></span> Firebase connected</span><button id="logout" class="secondary compact">Logout</button></div></header>
        <nav class="dashboard-tabs" aria-label="Dashboard sections"><button id="tabOverview" class="dashboard-tab active">Emergency</button><button id="tabMap" class="dashboard-tab">Live map</button><button id="tabStudents" class="dashboard-tab">Students</button></nav>
        <div id="dashboardError" class="error"></div><div id="alertToast" class="toast" hidden></div>
        <section class="summary-grid"><div class="summary-card"><span>Total students</span><strong id="totalStudents">0</strong></div><div class="summary-card"><span>Recently active</span><strong id="recentStudents">0</strong></div><div class="summary-card safe-card"><span>Safe</span><strong id="safeStudents">0</strong></div><div class="summary-card waiting-card"><span>Waiting</span><strong id="waitingStudents">0</strong></div><div class="summary-card help-card"><span>Needs help</span><strong id="helpStudents">0</strong></div><div class="summary-card"><span>Not acknowledged</span><strong id="unackStudents">0</strong></div></section>
        <div class="dashboard-grid"><section class="panel emergency-panel"><div class="panel-title"><div><div class="eyebrow">CONTROL CENTER</div><h2>Start emergency</h2></div><span id="emergencyBadge" class="badge neutral">NO ACTIVE ALERT</span></div><input id="location" type="hidden" value="Room 101"><label>Emergency type<select id="type"><option>FIRE</option><option>GAS_LEAK</option><option>SECURITY</option><option>MEDICAL</option><option>OTHER</option></select></label><label>Administrator message<textarea id="message" rows="3" placeholder="Tell students what they need to know"></textarea></label><button id="send">Send emergency alert</button><p class="muted small">Incident area is selected on the Live map page and will be sent automatically with this alert.</p></section></div>
        <section id="activePanel" class="panel active-panel"><div class="panel-title"><div><div class="eyebrow">LIVE STATUS</div><h2>Active emergency</h2></div></div><div id="active">No active emergency. The system is ready.</div></section>
        <div class="dashboard-grid lower-grid"><section class="panel map-panel"><div class="panel-title"><div><div class="eyebrow">LOCATION OPERATIONS</div><h2>Floor plan and nearest exit</h2><p class="muted">Choose the incident area here. The same selection is used automatically when you send the alert.</p></div><span class="badge neutral">63 × 30 METRES</span></div><div class="map-toolbar"><label>Emergency area<select id="mapArea">${mapAreas.keys.joinToString("") { "<option>$it</option>" }}</select></label><div class="map-toggles"><label class="inline-toggle"><input id="showGraph" type="checkbox"> Show graph</label><label class="inline-toggle"><input id="followPath" type="range" min="0" max="100" value="0"> Follow path</label><div class="zoom-controls"><button id="zoomOut" class="map-control">−</button><button id="zoomReset" class="map-control">100%</button><button id="zoomIn" class="map-control">+</button></div></div></div><p id="nearestExit" class="route-result">Choose an area to calculate the nearest available exit.</p><div class="map-legend"><span><i class="legend-dot user"></i>YOU</span><span><i class="legend-dot route"></i>Remaining route</span><span><i class="legend-dot complete"></i>Travelled route</span><span><i class="legend-dot exit"></i>Available exit</span><span><i class="legend-dot blocked"></i>Unavailable exit</span></div><div id="floorMapContainer" class="map-svg-wrap"></div><div class="exit-controls"><strong>Exit controls</strong><button id="toggleN" class="map-control">North</button><button id="toggleS" class="map-control">South</button><button id="toggleW" class="map-control">West</button><button id="toggleE" class="map-control">East</button></div><p class="muted small">Tap a door or junction marker to mark it unsafe. Unsafe vertices are excluded from route calculation.</p></section><section class="panel safety-panel"><div class="eyebrow">SAFETY NOTICE</div><h2>Operational reminder</h2><p>Student activity and location information are approximate and must not be treated as an official occupancy record.</p></section></div>
        <section class="panel students-panel"><div class="panel-title"><div><div class="eyebrow">ORGANIZATION ROSTER</div><h2>Students</h2></div><div class="filters"><button id="filterAll" class="filter active">All</button><button id="filterWaiting" class="filter">Waiting</button><button id="filterSafe" class="filter">Safe</button><button id="filterHelp" class="filter">Needs help</button><button id="filterUnack" class="filter">Not acknowledged</button></div></div><div class="table-wrap"><table><thead><tr><th>Name</th><th>Student ID</th><th>Email</th><th>Current area</th><th>Status</th><th>Last update</th><th>Connection</th><th>Acknowledgement</th></tr></thead><tbody id="students"><tr><td colspan="8" class="empty">Waiting for student data…</td></tr></tbody></table></div></section>
      </div>
    """.trimIndent()
    val overviewSection = dashboard.querySelector(".dashboard-grid")!!.asDynamic()
    val mapSection = dashboard.querySelector(".lower-grid")!!.asDynamic()
    val studentsSection = dashboard.querySelector(".students-panel")!!.asDynamic()
    val activeSection = dashboard.querySelector("#activePanel")!!.asDynamic()
    fun selectTab(tab: String) {
        if (tab == "map") { dashboard.appendChild(mapSection); dashboard.appendChild(overviewSection) } else { dashboard.insertBefore(overviewSection, mapSection) }
        overviewSection.style.display = if (tab == "overview") "grid" else "none"
        if (tab == "map") overviewSection.style.display = "grid"
        mapSection.style.display = if (tab == "map") "grid" else "none"
        studentsSection.style.display = if (tab == "students") "block" else "none"
        activeSection.style.display = if (tab == "map") "block" else "none"
        listOf("Overview", "Map", "Students").forEach { name -> document.getElementById("tab$name")!!.classList.remove("active") }
        document.getElementById("tab${tab.replaceFirstChar { it.uppercase() }}")!!.classList.add("active")
    }
    document.getElementById("tabOverview")!!.addEventListener("click", { selectTab("overview") })
    document.getElementById("tabMap")!!.addEventListener("click", { selectTab("map") })
    document.getElementById("tabStudents")!!.addEventListener("click", { selectTab("students") })
    selectTab("map")
    document.getElementById("showGraph")!!.parentElement!!.remove()
    set("adminName", profile.displayName ?: user.email ?: "Administrator")
    val db = firebase.firestore(); val org = profile.organizationId
    var currentFilter = "ALL"
    var students = emptyArray<dynamic>()
    var acknowledgements = emptyMap<String, String>()
    var removeAcknowledgementListener: dynamic = null
    var mapState = SafeExitMapState()
    var mapZoom = 1.0
    var showGraph = false
    var followProgress = 0.0

    fun renderMap() {
        val selectedArea = fieldText("mapArea").ifBlank { mapAreas.keys.first() }
        val selectedPoint = mapAreas[selectedArea] ?: mapAreas.values.first()
        val route = SafeExitRouter(mapState).calculateRoute(selectedPoint) as? RoutingResult.Success
        document.getElementById("location")!!.asDynamic().value = selectedArea
        document.getElementById("floorMapContainer")!!.innerHTML = mapSvg(mapState, route, selectedPoint, showGraph, followProgress)
        document.getElementById("floorMap")!!.asDynamic().style.transform = "scale($mapZoom)"
        set("nearestExit", route?.let { "Nearest available exit: ${it.destinationExit} · ${it.totalDistanceMetres.toString()} metres" } ?: "No available exit can be reached from this area.")
        SafeExitMapDefaults.EXIT_IDS.forEach { exitId ->
            val button = document.getElementById("toggle$exitId")!!
            button.textContent = ""
            button.setAttribute("aria-checked", (exitId in mapState.availableExits).toString())
            button.className = if (exitId in mapState.availableExits) "switch map-control on" else "switch map-control off"
        }
    }

    renderMap()
    document.getElementById("mapArea")!!.addEventListener("change", { renderMap() })
    document.getElementById("followPath")!!.addEventListener("input", { followProgress = (input("followPath").value as String).toDouble() / 100.0; renderMap() })
    document.getElementById("floorMapContainer")!!.addEventListener("click", { event -> val id = (event.target as? Element)?.getAttribute("data-vertex") ?: return@addEventListener; mapState = mapState.copy(unsafeVertices = if (id in mapState.unsafeVertices) mapState.unsafeVertices - id else mapState.unsafeVertices + id); followProgress = 0.0; input("followPath").value = "0"; renderMap() })
    SafeExitMapDefaults.EXIT_IDS.forEach { exitId -> document.getElementById("toggle$exitId")!!.addEventListener("click", { mapState = mapState.copy(availableExits = if (exitId in mapState.availableExits) mapState.availableExits - exitId else mapState.availableExits + exitId); renderMap() }) }

    fun renderStudents() {
        val now: Double = kotlin.js.Date.now()
        val visible = students.filter { student ->
            val status = textValue(student.data().safetyStatus, "UNKNOWN")
            when (currentFilter) {
                "WAITING" -> status == "WAITING"
                "SAFE" -> status == "SAFE"
                "HELP" -> status == "NEEDS_HELP"
                "UNACK" -> status == "UNKNOWN" || status == "WAITING"
                else -> true
            }
        }
        val rows = visible.joinToString("") { student ->
            val data = student.data(); val studentDocumentId = textValue(student.id); val timestamp = data.lastSeenAt; val last = if (timestamp == null) "Not available" else "Updated"; val recently = timestamp != null && now - (timestamp.toDate().getTime() as Double) <= 120000.0; val status = textValue(data.safetyStatus, "UNKNOWN"); val ack = acknowledgements[studentDocumentId] ?: "Not acknowledged"; "<tr><td><strong>${data.displayName ?: "—"}</strong></td><td>${data.studentId ?: "—"}</td><td>${data.email ?: "—"}</td><td>${data.currentArea ?: "—"}</td><td><span class='status-chip ${status.lowercase()}'>$status</span></td><td>$last</td><td><span class='${if (recently) "recent" else "not-recent"}'>${if (recently) "Recently Active" else "Not Recently Active"}</span></td><td>${ack}</td></tr>"
        }
        document.getElementById("students")!!.innerHTML = if (rows.isBlank()) "<tr><td colspan='8' class='empty'>No students match this filter.</td></tr>" else rows
        set("totalStudents", students.size.toString()); set("recentStudents", students.count { val t = it.data().lastSeenAt; t != null && now - (t.toDate().getTime() as Double) <= 120000.0 }.toString()); set("safeStudents", students.count { textValue(it.data().safetyStatus) == "SAFE" }.toString()); set("waitingStudents", students.count { textValue(it.data().safetyStatus) == "WAITING" }.toString()); set("helpStudents", students.count { textValue(it.data().safetyStatus) == "NEEDS_HELP" }.toString()); set("unackStudents", students.count { textValue(it.data().safetyStatus) == "UNKNOWN" || textValue(it.data().safetyStatus) == "WAITING" }.toString())
    }

    db.collection("users").where("organizationId", "==", org).where("role", "==", "STUDENT").onSnapshot({ snapshot: dynamic -> students = snapshot.docs.unsafeCast<Array<dynamic>>(); renderStudents() }, { error: dynamic -> set("dashboardError", "Student data unavailable: ${error.message ?: "Firestore permissions need to be published."}") })
    db.collection("emergencies").where("organizationId", "==", org).where("active", "==", true).limit(1).onSnapshot({ snapshot: dynamic ->
        val docs = snapshot.docs.unsafeCast<Array<dynamic>>(); if (removeAcknowledgementListener != null) removeAcknowledgementListener(); acknowledgements = emptyMap()
        if (docs.isEmpty()) { set("active", "No active emergency. The system is ready."); set("emergencyBadge", "NO ACTIVE ALERT") } else { val emergency = docs[0]; val emergencyDocumentId = textValue(emergency.id); val data = emergency.data(); val emergencyType = textValue(data.type, "OTHER"); set("emergencyBadge", "ACTIVE · $emergencyType"); document.getElementById("active")!!.innerHTML = "<div class='active-content'><div><span class='status-chip needs_help'>$emergencyType</span><h3>${data.locationText}</h3><p>${data.message}</p><p><strong>Nearest exit: ${data.recommendedExit ?: "Not calculated"}</strong></p><p class='muted'>Created by ${data.createdBy ?: "administrator"}</p></div><button id='endEmergency' class='danger'>End emergency</button></div>"; document.getElementById("endEmergency")!!.addEventListener("click", { if (window.confirm("End this emergency for all students?")) { val update = firestoreObject(); update.active = false; update.endedAt = firebase.firestore.FieldValue.serverTimestamp(); update.version = firebase.firestore.FieldValue.increment(1); emergency.ref.update(update).then { db.collection("organizations").doc(org).update("activeEmergencyId", null) }.catch { error: dynamic -> set("dashboardError", error.message ?: "Could not end emergency.") } } }); removeAcknowledgementListener = db.collection("emergencies").doc(emergencyDocumentId).collection("acknowledgements").onSnapshot { ackSnapshot: dynamic -> acknowledgements = ackSnapshot.docs.unsafeCast<Array<dynamic>>().associate { acknowledgement -> val acknowledgementId = textValue(acknowledgement.id); acknowledgementId to textValue(acknowledgement.data().status, "Acknowledged") }; renderStudents() } }
    }, { error: dynamic -> set("dashboardError", "Emergency data unavailable: ${error.message ?: "Firestore permissions need to be published."}") })
    document.getElementById("send")!!.addEventListener("click", { val location = fieldText("location"); val message = fieldText("message"); val type = fieldText("type"); try { val selectedArea = fieldText("mapArea"); val selectedPoint = mapAreas[selectedArea] ?: mapAreas.values.first(); val route = SafeExitRouter(mapState).calculateRoute(selectedPoint) as? RoutingResult.Success; if (Validation.emergency(location, message).isFailure) { set("dashboardError", "Emergency location and message are required."); return@addEventListener }; if (route == null) { set("dashboardError", "No available exit can be reached from the selected map area."); return@addEventListener }; if (!window.confirm("Send this emergency alert to all Demo University students? Nearest exit: ${route.destinationExit}")) return@addEventListener; val emergency = firestoreObject(); emergency.organizationId = org; emergency.active = true; emergency.type = type; emergency.locationText = location; emergency.message = message; emergency.recommendedExit = route.destinationExit; emergency.blockedExits = SafeExitMapDefaults.EXIT_IDS.filter { it !in mapState.availableExits }.joinToString(","); emergency.mapArea = selectedArea; emergency.createdBy = user.uid; emergency.createdAt = firebase.firestore.FieldValue.serverTimestamp(); emergency.version = 1; db.collection("emergencies").add(emergency).then { ref: dynamic -> showToast("Emergency alert sent", "$type · $location · Exit ${route.destinationExit}"); set("dashboardError", "Students are being alerted through Firestore."); db.collection("organizations").doc(org).update("activeEmergencyId", ref.id).catch { error: dynamic -> set("dashboardError", "Alert sent. Organization status marker could not be updated: ${error.message ?: "permission error"}") } }.catch { error: dynamic -> set("dashboardError", error.message ?: "Could not send emergency.") } } catch (error: dynamic) { set("dashboardError", error.message ?: "Could not prepare emergency alert.") } })
    listOf("All" to "ALL", "Waiting" to "WAITING", "Safe" to "SAFE", "Help" to "HELP", "Unack" to "UNACK").forEach { (name, value) -> document.getElementById("filter$name")!!.addEventListener("click", { currentFilter = value; listOf("All", "Waiting", "Safe", "Help", "Unack").forEach { document.getElementById("filter$it")!!.classList.remove("active") }; document.getElementById("filter$name")!!.classList.add("active"); renderStudents() }) }
    document.getElementById("logout")!!.addEventListener("click", { firebase.auth().signOut(); window.location.reload() })
}
