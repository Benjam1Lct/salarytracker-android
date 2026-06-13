package com.benjamin.salarytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.benjamin.salarytracker.ui.theme.SalaryTrackerTheme
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SalaryViewModel = viewModel()
            val currentTheme by viewModel.appTheme.collectAsStateWithLifecycle()

            SalaryTrackerTheme(
                accentColor = when (currentTheme) {
                    "blue" -> Color(0xFF3B82F6)
                    "green" -> Color(0xFF10B981)
                    "orange" -> Color(0xFFF59E0B)
                    "red" -> Color(0xFFEF4444)
                    "pink" -> Color(0xFFEC4899)
                    else -> Color(0xFF8B7CF6) // purple
                }
            ) {
                SalaryTrackerApp(
                    viewModel = viewModel,
                    currentTheme = currentTheme,
                    onThemeChange = { viewModel.updateTheme(this, it) }
                )
            }
        }
    }
}

@Composable
fun SalaryTrackerApp(
    viewModel: SalaryViewModel,
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val currentJob by viewModel.currentJob.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val currentJobId by viewModel.currentJobId.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val autoRules by viewModel.autoRules.collectAsStateWithLifecycle()
    val payslips by viewModel.payslips.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val dailyReminderEnabled by viewModel.dailyReminderEnabled.collectAsStateWithLifecycle()
    val dailyReminderHour by viewModel.dailyReminderHour.collectAsStateWithLifecycle()
    val dailyReminderMinute by viewModel.dailyReminderMinute.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.setDailyReminder(context, true)
        } else {
            Toast.makeText(context, "Permission refusée : les rappels ne s'afficheront pas.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(userSession) {
        if (userSession == null && currentRoute != "login" && currentRoute != "loading") {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val scope = rememberCoroutineScope()
    var loginStatus by remember { mutableStateOf("IDLE") } // "IDLE", "CONNECTING", "SUCCESS"

    var showGoogleChooserFallback by remember { mutableStateOf(false) }
    var showGoogleErrorDialog by remember { mutableStateOf(false) }
    var googleErrorCode by remember { mutableStateOf("") }
    var googleErrorMessage by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email ?: ""
            val displayName = account.displayName ?: "Utilisateur Google"
            val photoUrl = account.photoUrl?.toString()
            val idToken = account.idToken

            if (idToken != null) {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener { authResult ->
                        if (authResult.isSuccessful) {
                            val firebaseUser = authResult.result?.user
                            if (firebaseUser != null) {
                                scope.launch {
                                    loginStatus = "SUCCESS"
                                    kotlinx.coroutines.delay(1200)
                                    viewModel.loginWithGoogle(
                                        uid = firebaseUser.uid,
                                        email = firebaseUser.email ?: email,
                                        name = firebaseUser.displayName ?: displayName,
                                        photoUrl = firebaseUser.photoUrl?.toString() ?: photoUrl
                                    )
                                    loginStatus = "IDLE"
                                }
                            }
                        } else {
                            val uid = account.id ?: "google_user"
                            scope.launch {
                                loginStatus = "SUCCESS"
                                kotlinx.coroutines.delay(1200)
                                viewModel.loginWithGoogle(uid, email, displayName, photoUrl)
                                loginStatus = "IDLE"
                            }
                        }
                    }
            } else {
                val uid = account.id ?: "google_user"
                scope.launch {
                    loginStatus = "SUCCESS"
                    kotlinx.coroutines.delay(1200)
                    viewModel.loginWithGoogle(uid, email, displayName, photoUrl)
                    loginStatus = "IDLE"
                }
            }
        } catch (e: ApiException) {
            android.util.Log.e("MainActivity", "Google Sign In task failed: Code ${e.statusCode}, ${e.message}")
            loginStatus = "IDLE"
            googleErrorCode = e.statusCode.toString()
            googleErrorMessage = when (e.statusCode) {
                10 -> "DEVELOPER_ERROR : L'empreinte SHA-1 de votre application n'est probablement pas enregistrée dans la console Firebase, ou la configuration dans google-services.json est incomplète."
                12500 -> "SIGN_IN_FAILED : Échec de la connexion. Vérifiez les services Google Play sur votre appareil."
                7 -> "NETWORK_ERROR : Erreur réseau. Vérifiez votre connexion Internet."
                else -> e.message ?: "Une erreur s'est produite lors de l'authentification Google."
            }
            showGoogleErrorDialog = true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Google Sign In task failed: ${e.message}")
            loginStatus = "IDLE"
            googleErrorCode = "Inconnue"
            googleErrorMessage = e.message ?: "Une erreur inattendue est survenue."
            showGoogleErrorDialog = true
        }
    }

    val triggerGoogleSignIn: () -> Unit = {
        loginStatus = "CONNECTING"
        try {
            val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()

            // Recherche dynamique du client_id web généré par le plugin google-services
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val defaultWebClientId = context.getString(resId)
                if (defaultWebClientId.isNotBlank()) {
                    builder.requestIdToken(defaultWebClientId)
                }
            }

            val gso = builder.build()
            val activity = context.findActivity()
            if (activity != null) {
                val client = GoogleSignIn.getClient(activity, gso)
                googleSignInLauncher.launch(client.signInIntent)
            } else {
                loginStatus = "IDLE"
                googleErrorCode = "Contexte"
                googleErrorMessage = "Impossible d'accéder au contexte de l'activité."
                showGoogleErrorDialog = true
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Google Sign In launch failed: ${e.message}")
            loginStatus = "IDLE"
            googleErrorCode = "Initialisation"
            googleErrorMessage = e.message ?: "Impossible d'initialiser le module Google Sign-In."
            showGoogleErrorDialog = true
        }
    }

    // Rattrapage de la saisie auto dès que règles + templates sont chargés
    LaunchedEffect(currentJobId, autoRules, templates) {
        if (autoRules.any { it.active } && templates.isNotEmpty()) {
            viewModel.runAutoGeneration()
        }
    }

    // Snapshot pour les widgets écran d'accueil
    val widgetContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(currentJob, entries) {
        currentJob?.let { job ->
            val s = SalaryCalculator.calculateMonthStats(job, entries, java.time.YearMonth.now())
            val daysLeft = job.endDate?.let {
                java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), it).coerceAtLeast(0).toInt()
            } ?: -1
            SalaryWidget.save(widgetContext, job.name, s.salaireNetEstime, job.targetMonthlySalary, daysLeft)
        }
    }

    var editingEntry by remember { mutableStateOf<DayEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = navController,
            startDestination = "loading",
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(180)) }
        ) {
            composable("loading") {
                val prefs = context.getSharedPreferences("salary_tracker_prefs", android.content.Context.MODE_PRIVATE)
                Column(
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(110.dp)
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
                LaunchedEffect(userSession, jobs) {
                    delay(500)
                    val session = userSession
                    if (session == null) {
                        navController.navigate("login") { popUpTo("loading") { inclusive = true } }
                    } else {
                        val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding_${session.uid}", false)
                        if (!hasSeenOnboarding) {
                            navController.navigate("onboarding") { popUpTo("loading") { inclusive = true } }
                        } else {
                            if (jobs.isNotEmpty()) {
                                navController.navigate("dashboard") { popUpTo("loading") { inclusive = true } }
                            } else {
                                delay(500)
                                if (jobs.isEmpty()) {
                                    navController.navigate("selection") { popUpTo("loading") { inclusive = true } }
                                } else {
                                    navController.navigate("dashboard") { popUpTo("loading") { inclusive = true } }
                                }
                            }
                        }
                    }
                }
            }

            composable("login") {
                val prefs = context.getSharedPreferences("salary_tracker_prefs", android.content.Context.MODE_PRIVATE)
                LoginScreen(
                    loginStatus = loginStatus,
                    onGoogleSignInClick = { triggerGoogleSignIn() },
                    onMockSignInClick = { name ->
                        scope.launch {
                            loginStatus = "CONNECTING"
                            delay(1000)
                            loginStatus = "SUCCESS"
                            delay(1200)
                            viewModel.loginMock(name)
                            loginStatus = "IDLE"
                        }
                    }
                )
                LaunchedEffect(userSession, jobs) {
                    val session = userSession
                    if (session != null) {
                        val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding_${session.uid}", false)
                        if (!hasSeenOnboarding) {
                            navController.navigate("onboarding") { popUpTo("login") { inclusive = true } }
                        } else {
                            if (jobs.isNotEmpty()) {
                                navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
                            } else {
                                navController.navigate("selection") { popUpTo("login") { inclusive = true } }
                            }
                        }
                    }
                }
            }

            composable("onboarding") {
                val prefs = context.getSharedPreferences("salary_tracker_prefs", android.content.Context.MODE_PRIVATE)
                OnboardingScreen(
                    onFinish = {
                        userSession?.let { session ->
                            prefs.edit().putBoolean("has_seen_onboarding_${session.uid}", true).apply()
                        }
                        if (jobs.isNotEmpty()) {
                            navController.navigate("dashboard") { popUpTo("onboarding") { inclusive = true } }
                        } else {
                            navController.navigate("selection") { popUpTo("onboarding") { inclusive = true } }
                        }
                    }
                )
            }

            composable("dashboard") {
                currentJob?.let { job ->
                    DashboardScreen(
                        job = job,
                        entries = entries,
                        templates = templates,
                        connectionStatus = connectionStatus,
                        onAddEntry = { editingEntry = null; navController.navigate("add_day") },
                        onEditEntry = { editingEntry = it; navController.navigate("add_day") },
                        onCreateTemplate = { navController.navigate("manage_templates") },
                        onSelectJob = { navController.navigate("selection") },
                        onSettings = { navController.navigate("settings") },
                        onUpdateTarget = { viewModel.updateTargetSalary(it) },
                        onSeeAll = {
                            navController.navigate("stats_salary") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() }
                    )
                }
            }

            composable("history") {
                currentJob?.let { job ->
                    HistoryScreen(
                        job = job,
                        entries = entries,
                        connectionStatus = connectionStatus,
                        onAddEntry = { date ->
                            editingEntry = DayEntry(
                                id = "",
                                jobId = job.id,
                                date = date,
                                startTime = java.time.LocalTime.of(8, 0),
                                endTime = java.time.LocalTime.of(17, 0),
                                pauseMinutes = 0
                            )
                            navController.navigate("add_day")
                        },
                        onEditEntry = { entry ->
                            editingEntry = entry
                            navController.navigate("add_day")
                        },
                        onSettings = { navController.navigate("settings") },
                        onSelectJob = { navController.navigate("selection") }
                    )
                }
            }

            composable("stats_salary") {
                currentJob?.let { job ->
                    StatsScreen(
                        job = job,
                        entries = entries,
                        mode = "salary",
                        connectionStatus = connectionStatus,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        onOpenPayslips = { navController.navigate("payslips") },
                        onImportFile = { uri, onSuccess, onError ->
                            viewModel.importFile(context, uri, onSuccess, onError)
                        },
                        onSettings = { navController.navigate("settings") },
                        onSelectJob = { navController.navigate("selection") }
                    )
                }
            }

            composable(
                "payslips",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) {
                currentJob?.let { job ->
                    PayslipScreen(
                        job = job,
                        entries = entries,
                        payslips = payslips,
                        connectionStatus = connectionStatus,
                        geminiApiKey = geminiApiKey,
                        onAddPayslip = { viewModel.addPayslip(it) },
                        onDeletePayslip = { viewModel.deletePayslip(it) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("stats_hours") {
                currentJob?.let { job ->
                    StatsScreen(
                        job = job,
                        entries = entries,
                        mode = "hours",
                        connectionStatus = connectionStatus,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        onImportFile = { uri, onSuccess, onError ->
                            viewModel.importFile(context, uri, onSuccess, onError)
                        },
                        onSettings = { navController.navigate("settings") },
                        onSelectJob = { navController.navigate("selection") }
                    )
                }
            }

            composable("auto_entry") {
                currentJob?.let { job ->
                    AutoEntryScreen(
                        job = job,
                        templates = templates,
                        autoRules = autoRules,
                        connectionStatus = connectionStatus,
                        onAddRule = { viewModel.addAutoRule(it) },
                        onDeleteRule = { viewModel.deleteAutoRule(it) },
                        onSettings = { navController.navigate("settings") },
                        onSelectJob = { navController.navigate("selection") },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                "selection",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) {
                JobSelectionScreen(
                    jobs = jobs,
                    selectedJobId = currentJobId ?: "",
                    onJobSelect = {
                        viewModel.selectJob(it.id)
                        navController.navigate("dashboard") { popUpTo("selection") { inclusive = true } }
                    },
                    onToggleMainJob = { viewModel.setMainJob(it) },
                    onAddJob = { navController.navigate("create_job") },
                    onEditJob = { navController.navigate("edit_job/${it.id}") },
                    onBack = { if (jobs.isNotEmpty()) navController.popBackStack() }
                )
            }

            composable(
                "add_day",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) {
                currentJob?.let { job ->
                    AddDayScreen(
                        job = job,
                        templates = templates,
                        existingEntry = editingEntry,
                        onAddEntry = { viewModel.addDayEntry(it) },
                        onDeleteEntry = { viewModel.deleteDayEntry(it) },
                        onManageTemplates = { navController.navigate("manage_templates") },
                        onImportFile = { uri, onSuccess, onError ->
                            viewModel.importFile(context, uri, onSuccess, onError)
                        },
                        onBack = { editingEntry = null; navController.popBackStack() }
                    )
                }
            }

            composable(
                "create_job",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) {
                CreateJobScreen(
                    geminiApiKey = geminiApiKey,
                    onJobCreated = { viewModel.addJob(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                "edit_job/{jobId}",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId")
                val jobToEdit = jobs.find { it.id == jobId }
                if (jobToEdit != null) {
                    CreateJobScreen(
                        existingJob = jobToEdit,
                        geminiApiKey = geminiApiKey,
                        onJobCreated = {},
                        onJobUpdated = { viewModel.updateJob(it) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                "settings",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) {
                SettingsScreen(
                    currentTheme = currentTheme,
                    connectionStatus = connectionStatus,
                    userSession = userSession,
                    geminiApiKey = geminiApiKey,
                    dailyReminderEnabled = dailyReminderEnabled,
                    dailyReminderHour = dailyReminderHour,
                    dailyReminderMinute = dailyReminderMinute,
                    onThemeChange = { onThemeChange(it) },
                    onSaveApiKey = { viewModel.saveGeminiApiKey(it) },
                    onToggleDailyReminder = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val permission = android.Manifest.permission.POST_NOTIFICATIONS
                                if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.setDailyReminder(context, true)
                                } else {
                                    requestPermissionLauncher.launch(permission)
                                }
                            } else {
                                viewModel.setDailyReminder(context, true)
                            }
                        } else {
                            viewModel.setDailyReminder(context, false)
                        }
                    },
                    onUpdateDailyReminderTime = { h, m ->
                        viewModel.setDailyReminder(context, dailyReminderEnabled, h, m)
                    },
                    onLogout = { viewModel.logout() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                "manage_templates",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) {
                ManageTemplatesScreen(
                    existingTemplates = templates,
                    onAddTemplate = { viewModel.addTemplate(it) },
                    onDeleteTemplate = { viewModel.deleteTemplate(it) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        // Custom floating bottom nav bar
        val showBottomBar = currentRoute in listOf("dashboard", "history", "stats_salary", "stats_hours", "auto_entry", "settings")
        if (showBottomBar) {
            val topLevelNav: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            AppBottomBar(
                currentRoute = currentRoute,
                onHome = { topLevelNav("dashboard") },
                onHistory = { topLevelNav("history") },
                onAuto = { topLevelNav("auto_entry") },
                onStatsHours = { topLevelNav("stats_hours") },
                onStatsSalary = { topLevelNav("stats_salary") },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showGoogleChooserFallback) {
        AlertDialog(
            onDismissRequest = { showGoogleChooserFallback = false },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Choisir un compte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "pour continuer vers SalaryTracker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val accounts = listOf(
                        "benjamin.dev.31@gmail.com" to "Benjamin",
                        "lucas.salarytracker@gmail.com" to "Lucas",
                        "hugo.salarytracker@gmail.com" to "Hugo"
                    )

                    accounts.forEach { (email, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val uid = "google_${name.lowercase().replace(" ", "_")}"
                                    viewModel.loginWithGoogle(uid, email, name, null)
                                    showGoogleChooserFallback = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.take(1),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    var showCustomEmailField by remember { mutableStateOf(false) }
                    var customEmail by remember { mutableStateOf("") }
                    var customName by remember { mutableStateOf("") }

                    if (!showCustomEmailField) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCustomEmailField = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Utiliser un autre compte", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Nom complet") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customEmail,
                                onValueChange = { customEmail = it },
                                label = { Text("Adresse e-mail Google") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showCustomEmailField = false }) {
                                    Text("Annuler")
                                }
                                Button(
                                    onClick = {
                                        if (customEmail.contains("@") && customName.isNotBlank()) {
                                            val uid = "google_${customName.lowercase().replace(" ", "_")}"
                                            viewModel.loginWithGoogle(uid, customEmail.trim(), customName.trim(), null)
                                            showGoogleChooserFallback = false
                                        }
                                    },
                                    enabled = customEmail.contains("@") && customName.isNotBlank()
                                ) {
                                    Text("Valider")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showGoogleErrorDialog) {
        val sha1 = getAppSHA1(context)
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

        AlertDialog(
            onDismissRequest = { showGoogleErrorDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Problème de connexion Google", color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "La connexion Google en direct a échoué (Code : $googleErrorCode).",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = googleErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Pour résoudre cela dans Firebase :", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("1. Package : com.benjamin.salarytracker", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2. Empreinte SHA-1 :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = sha1,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sha1))
                                Toast.makeText(context, "SHA-1 copié !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Copier SHA-1", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoogleErrorDialog = false
                        showGoogleChooserFallback = true
                    }
                ) {
                    Text("Utiliser le simulateur")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleErrorDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

fun getAppSHA1(context: android.content.Context): String {
    try {
        val packageName = context.packageName
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        val signatures = packageInfo.signatures
        if (signatures != null && signatures.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray())
            return digest.joinToString(":") { byte ->
                val hex = Integer.toHexString(byte.toInt() and 0xFF).uppercase(Locale.US)
                if (hex.length == 1) "0$hex" else hex
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to get SHA-1", e)
    }
    return "Indisponible"
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
