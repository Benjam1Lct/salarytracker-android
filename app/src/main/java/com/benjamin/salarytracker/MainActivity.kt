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
import androidx.compose.ui.res.stringResource
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

    /** Applique la langue choisie (Système / fr / en) avant la création de l'UI. */
    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = newBase
            .getSharedPreferences("salary_tracker_prefs", android.content.Context.MODE_PRIVATE)
            .getString("app_language", "system") ?: "system"
        super.attachBaseContext(applyAppLocale(newBase, lang))
    }

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
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val currentJob by viewModel.currentJob.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val currentJobId by viewModel.currentJobId.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val updateRequired by viewModel.updateRequired.collectAsStateWithLifecycle()
    val autoRules by viewModel.autoRules.collectAsStateWithLifecycle()
    val payslips by viewModel.payslips.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    val subscriptionPrice by viewModel.subscriptionPrice.collectAsStateWithLifecycle()
    val aiUsage by viewModel.aiUsage.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val useLocalAi by viewModel.useLocalAi.collectAsStateWithLifecycle()
    val dailyReminderEnabled by viewModel.dailyReminderEnabled.collectAsStateWithLifecycle()
    val dailyReminderHour by viewModel.dailyReminderHour.collectAsStateWithLifecycle()
    val dailyReminderMinute by viewModel.dailyReminderMinute.collectAsStateWithLifecycle()

    // Modal configuration clé Gemini — affichée quand l'utilisateur tente d'utiliser l'IA sans clé
    var showGeminiKeyModal by remember { mutableStateOf(false) }

    // Pré-remplissage lors de l'ajout d'un contrat rattaché à une entreprise existante
    // (companyId, companyName). null = création normale.
    var contractPreset by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Dialog de création d'entreprise
    var showCreateCompany by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Synchronise la langue depuis la BD (changement sur un autre appareil).
    LaunchedEffect(appLanguage) {
        viewModel.syncLanguageFromDb(context, appLanguage)
    }

    // Export des données locales (sauvegarde)
    val exportDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportLocalData(context, it) { ok ->
                Toast.makeText(context, if (ok) context.getString(R.string.ma_data_saved) else context.getString(R.string.ma_save_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Import d'une sauvegarde locale (depuis l'écran de connexion)
    val importDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importLocalData(context, it) { ok ->
                Toast.makeText(context, if (ok) context.getString(R.string.ma_data_imported) else context.getString(R.string.ma_invalid_backup), Toast.LENGTH_LONG).show()
            }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.setDailyReminder(context, true)
        } else {
            Toast.makeText(context, context.getString(R.string.ma_perm_denied), Toast.LENGTH_LONG).show()
        }
    }

    // Première ouverture : demande la permission de notification et active le rappel quotidien.
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("salary_tracker_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("asked_notif_permission", false)) {
            prefs.edit().putBoolean("asked_notif_permission", true).apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.setDailyReminder(context, true)
                } else {
                    // Le callback du launcher active le rappel si l'utilisateur accepte.
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.setDailyReminder(context, true)
            }
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

    var showGoogleErrorDialog by remember { mutableStateOf(false) }
    var googleErrorCode by remember { mutableStateOf("") }
    var googleErrorMessage by remember { mutableStateOf("") }



    // ─── Liaison de comptes (account linking) ─────────────────────────────
    var linkedProviders by remember { mutableStateOf(viewModel.linkedProviderIds()) }
    var googleAuthMode by remember { mutableStateOf("signin") } // "signin" | "link"

    val refreshProviders = { linkedProviders = viewModel.linkedProviderIds() }
    // Rafraîchit la liste des méthodes liées dès qu'une session est active
    LaunchedEffect(userSession) { if (userSession != null) linkedProviders = viewModel.linkedProviderIds() }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email ?: ""
            val displayName = account.displayName ?: context.getString(R.string.ma_google_user)
            val photoUrl = account.photoUrl?.toString()
            val idToken = account.idToken

            // Mode liaison : on rattache Google au compte déjà connecté.
            if (googleAuthMode == "link") {
                googleAuthMode = "signin"
                if (idToken != null) {
                    viewModel.linkWithGoogle(idToken) { ok, err ->
                        refreshProviders()
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.ma_google_linked) else (err ?: context.getString(R.string.ma_google_link_failed)),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.ma_google_token_missing), Toast.LENGTH_SHORT).show()
                }
                return@rememberLauncherForActivityResult
            }

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
                10 -> context.getString(R.string.ma_err_dev)
                12500 -> context.getString(R.string.ma_err_signin)
                7 -> context.getString(R.string.ma_err_network)
                else -> e.message ?: context.getString(R.string.ma_google_err_generic)
            }
            showGoogleErrorDialog = true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Google Sign In task failed: ${e.message}")
            loginStatus = "IDLE"
            googleErrorCode = "?"
            googleErrorMessage = e.message ?: context.getString(R.string.ma_google_err_unexpected)
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
                googleErrorCode = "Context"
                googleErrorMessage = context.getString(R.string.ma_google_err_context)
                showGoogleErrorDialog = true
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Google Sign In launch failed: ${e.message}")
            loginStatus = "IDLE"
            googleErrorCode = "Init"
            googleErrorMessage = e.message ?: context.getString(R.string.ma_google_err_init)
            showGoogleErrorDialog = true
        }
    }

    // Lance le sélecteur Google en mode LIAISON (pas de connexion, pas d'écran "CONNECTING").
    val triggerGoogleLink: () -> Unit = {
        googleAuthMode = "link"
        try {
            val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val webClientId = context.getString(resId)
                if (webClientId.isNotBlank()) builder.requestIdToken(webClientId)
            }
            val activity = context.findActivity()
            if (activity != null) {
                val client = GoogleSignIn.getClient(activity, builder.build())
                // Déconnecte le client pour forcer le choix d'un compte à lier
                client.signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(client.signInIntent)
                }
            } else {
                googleAuthMode = "signin"
                Toast.makeText(context, context.getString(R.string.ma_context_unavailable), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            googleAuthMode = "signin"
            Toast.makeText(context, context.getString(R.string.ma_google_error_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
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

    // Blocage de version : écran dédié, le reste de l'app est inaccessible.
    if (updateRequired) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        UpdateRequiredScreen(
            isLocalAccount = userSession?.isLocal == true,
            onOpenGithub = {
                try { uriHandler.openUri("https://github.com/Benjam1Lct/salarytracker-android/releases") } catch (_: Exception) {}
            },
            onDownloadData = { exportDataLauncher.launch("salarytracker_backup.dat") }
        )
        return
    }

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
                    onLocalSignInClick = { name ->
                        scope.launch {
                            loginStatus = "CONNECTING"
                            delay(800)
                            loginStatus = "SUCCESS"
                            delay(1000)
                            viewModel.loginLocal(name)
                            loginStatus = "IDLE"
                        }
                    },
                    onImportData = { importDataLauncher.launch("*/*") },
                    onEmailSignIn = { email, password, onResult ->
                        scope.launch {
                            loginStatus = "CONNECTING"
                            viewModel.signInWithEmailAndPassword(email, password) { success, err ->
                                if (success) {
                                    loginStatus = "SUCCESS"
                                    scope.launch {
                                        delay(1000)
                                        loginStatus = "IDLE"
                                    }
                                    onResult(true, null)
                                } else {
                                    loginStatus = "IDLE"
                                    onResult(false, err)
                                }
                            }
                        }
                    },
                    onEmailSignUp = { email, password, onResult ->
                        scope.launch {
                            loginStatus = "CONNECTING"
                            viewModel.signUpWithEmailAndPassword(email, password) { success, err ->
                                if (success) {
                                    loginStatus = "SUCCESS"
                                    scope.launch {
                                        delay(1000)
                                        loginStatus = "IDLE"
                                    }
                                    onResult(true, null)
                                } else {
                                    loginStatus = "IDLE"
                                    onResult(false, err)
                                }
                            }
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
                    val activeSessionStartTime = viewModel.activeSessionStartTime.collectAsState().value
                    val activeSessionPauseStartTime = viewModel.activeSessionPauseStartTime.collectAsState().value
                    val activeSessionTotalPauseMs = viewModel.activeSessionTotalPauseMs.collectAsState().value

                    DashboardScreen(
                        job = job,
                        entries = entries,
                        templates = templates,
                        connectionStatus = connectionStatus,
                        isSubscribed = isSubscribed,
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
                        onRefresh = { viewModel.refresh() },
                        importStatus = importStatus,
                        onDismissImport = { viewModel.clearImportStatus() },
                        activeSessionStartTime = activeSessionStartTime,
                        activeSessionPauseStartTime = activeSessionPauseStartTime,
                        activeSessionTotalPauseMs = activeSessionTotalPauseMs,
                        onStartWorkday = { viewModel.startWorkday() },
                        onStartPause = { viewModel.startPause() },
                        onEndPause = { viewModel.endPause() },
                        onEndWorkday = { viewModel.endWorkday() },
                        onCancelWorkday = { viewModel.cancelWorkday() }
                    )
                }
            }

            composable("history") {
                currentJob?.let { job ->
                    HistoryScreen(
                        job = job,
                        entries = entries,
                        connectionStatus = connectionStatus,
                        isSubscribed = isSubscribed,
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
                        isSubscribed = isSubscribed,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        onOpenPayslips = { navController.navigate("payslips") },
                        onImportFile = { uri, onSuccess, onError ->
                            viewModel.importFile(context, uri, onSuccess, onError) { showGeminiKeyModal = true }
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
                        useLocalAi = useLocalAi,
                        isSubscribed = isSubscribed,
                        onNeedGeminiKey = { showGeminiKeyModal = true },
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
                        isSubscribed = isSubscribed,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        onImportFile = { uri, onSuccess, onError ->
                            viewModel.importFile(context, uri, onSuccess, onError) { showGeminiKeyModal = true }
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
                        isSubscribed = isSubscribed,
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
                    companies = companies,
                    selectedJobId = currentJobId ?: "",
                    onJobSelect = {
                        viewModel.selectJob(it.id)
                        navController.navigate("dashboard") { popUpTo("selection") { inclusive = true } }
                    },
                    onToggleMainJob = { viewModel.setMainJob(it) },
                    onAddJob = { contractPreset = null; navController.navigate("create_job") },
                    onEditJob = { navController.navigate("edit_job/${it.id}") },
                    onAddContractToCompany = { companyId, companyName ->
                        contractPreset = companyId to companyName
                        navController.navigate("create_job")
                    },
                    onDeleteJob = { viewModel.deleteJob(it) },
                    onAddCompany = { showCreateCompany = true },
                    onDeleteCompany = { viewModel.deleteCompany(it) },
                    onBack = { if (jobs.isNotEmpty() || companies.isNotEmpty()) navController.popBackStack() }
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
                            viewModel.importFile(
                                context = context,
                                uri = uri,
                                onSuccess = { count ->
                                    onSuccess(count)
                                    // Navigation directe vers l'historique une fois l'importation réussie
                                    navController.navigate("history") {
                                        popUpTo("dashboard") { inclusive = false }
                                    }
                                },
                                onError = onError,
                                onNeedAiChoice = { showGeminiKeyModal = true }
                            )
                            // Redirige vers l'accueil : la progression de l'import y est affichée.
                            navController.navigate("dashboard") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                            }
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
                    useLocalAi = useLocalAi,
                    isSubscribed = isSubscribed,
                    presetCompanyId = contractPreset?.first,
                    presetCompanyName = contractPreset?.second,
                    onNeedGeminiKey = { showGeminiKeyModal = true },
                    onJobCreated = {
                        viewModel.addJob(it)
                        contractPreset = null
                        // Retour déterministe vers "Mes emplois" (une seule entrée dans la pile)
                        navController.navigate("selection") {
                            popUpTo("selection") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { contractPreset = null; navController.popBackStack() }
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
                        useLocalAi = useLocalAi,
                        isSubscribed = isSubscribed,
                        onNeedGeminiKey = { showGeminiKeyModal = true },
                        onJobCreated = {},
                        onJobUpdated = { viewModel.updateJob(it) },
                        onDeleteJob = { viewModel.deleteJob(it.id) },
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
                    isSubscribed = isSubscribed,
                    onOpenSubscription = { navController.navigate("subscription") },
                    appLanguage = appLanguage,
                    onLanguageChange = { viewModel.setLanguage(context, it) },
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
                    onDeleteAccount = {
                        viewModel.deleteAccount { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onDeleteAllEntries = {
                        viewModel.deleteAllEntries { count ->
                            Toast.makeText(context, context.getString(R.string.ma_days_deleted, count), Toast.LENGTH_SHORT).show()
                        }
                    },
                    linkedProviders = linkedProviders,
                    onLinkGoogle = { triggerGoogleLink() },
                    onLinkEmailAndPassword = { email, password, callback ->
                        viewModel.linkWithEmailAndPassword(email, password) { ok, err ->
                            refreshProviders()
                            callback(ok, err)
                            if (ok) {
                                Toast.makeText(context, context.getString(R.string.ma_email_linked), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onUnlinkProvider = { providerId ->
                        viewModel.unlinkProvider(providerId) { ok, err ->
                            refreshProviders()
                            Toast.makeText(
                                context,
                                if (ok) context.getString(R.string.ma_unlinked) else (err ?: context.getString(R.string.ma_unlink_failed)),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                "subscription",
                enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
                popExitTransition = { slideOutVertically(animationSpec = tween(300)) { it } }
            ) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                SubscriptionScreen(
                    isSubscribed = isSubscribed,
                    price = subscriptionPrice,
                    aiUsage = aiUsage,
                    onSubscribe = { context.findActivity()?.let { viewModel.launchSubscription(it) } },
                    onManage = {
                        try {
                            uriHandler.openUri("https://play.google.com/store/account/subscriptions?sku=${BillingManager.SUBSCRIPTION_ID}&package=com.benjamin.salarytracker")
                        } catch (_: Exception) {}
                    },
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
                    onRetrieveFromHistory = { onResult -> viewModel.importTemplatesFromEntries(onResult) },
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

    // ─── Dialog création d'entreprise ──────────────────────────────────
    if (showCreateCompany) {
        var companyNameInput by remember { mutableStateOf("") }
        var addBaseContract by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCreateCompany = false },
            icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.ma_new_company)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = companyNameInput,
                        onValueChange = { companyNameInput = it },
                        label = { Text(stringResource(R.string.ma_company_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { addBaseContract = !addBaseContract }
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = addBaseContract,
                            onCheckedChange = { addBaseContract = it }
                        )
                        Text(stringResource(R.string.ma_create_base_contract), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nm = companyNameInput.trim()
                        showCreateCompany = false
                        viewModel.createCompany(nm) { company ->
                            if (addBaseContract) {
                                contractPreset = company.id to company.name
                                navController.navigate("create_job")
                            }
                        }
                    },
                    enabled = companyNameInput.isNotBlank()
                ) { Text(stringResource(R.string.ma_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateCompany = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // ─── Modal clé Gemini ──────────────────────────────────────────────
    if (showGeminiKeyModal) {
        GeminiKeyModal(
            onSaveKey = { key ->
                viewModel.saveGeminiApiKey(key)
                showGeminiKeyModal = false
            },
            onUseLocalAi = {
                viewModel.declineGeminiForever()
                showGeminiKeyModal = false
            },
            onDismiss = { showGeminiKeyModal = false }
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
                    Text(stringResource(R.string.ma_google_problem), color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.ma_google_failed_code, googleErrorCode),
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
                            Text(stringResource(R.string.ma_firebase_fix), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("1. Package : com.benjamin.salarytracker", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.ma_sha1_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Toast.makeText(context, context.getString(R.string.ma_sha1_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.ma_copy_sha1), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showGoogleErrorDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

/** Normalise un numéro français au format E.164 (+33…), en retirant le 0 superflu. */
fun normalizeFrPhone(raw: String): String {
    var s = raw.replace(Regex("[ .\\-]"), "").trim()
    when {
        s.startsWith("0033") -> s = "+33" + s.substring(4)
        s.startsWith("+330") -> s = "+33" + s.substring(4)
        s.startsWith("0") -> s = "+33" + s.substring(1)
    }
    return s
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

/** Renvoie un contexte avec la locale forcée, ou inchangé si "system". */
fun applyAppLocale(base: android.content.Context, lang: String): android.content.Context {
    if (lang == "system" || lang.isBlank()) return base
    val locale = java.util.Locale.forLanguageTag(lang)
    java.util.Locale.setDefault(locale)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
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
