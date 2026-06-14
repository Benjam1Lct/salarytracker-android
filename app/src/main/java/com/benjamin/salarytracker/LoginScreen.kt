package com.benjamin.salarytracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    loginStatus: String,
    onGoogleSignInClick: () -> Unit,
    onPhoneSignInClick: (String) -> Unit,
    onVerifyOtp: (String) -> Unit,
    phoneOtpState: PhoneOtpState,
    onLocalSignInClick: (String) -> Unit,
    onImportData: () -> Unit = {},
    onEmailSignIn: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onEmailSignUp: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    var mockName by remember { mutableStateOf("") }
    var showMockDialog by remember { mutableStateOf(false) }

    Crossfade(targetState = loginStatus, label = "LoginState") { state ->
        when (state) {
            "CONNECTING" -> { ConnectingStateView() }
            "SUCCESS" -> { SuccessStateView() }
            else -> {
                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradient)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // App Logo / Icon — animé (entrée en ressort + flottement + halo)
                        val logoIntro = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(Unit) {
                            logoIntro.animateTo(
                                1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                        val logoTransition = rememberInfiniteTransition(label = "logo")
                        val floatDp by logoTransition.animateFloat(
                            initialValue = -7f, targetValue = 7f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "float"
                        )
                        val breathe by logoTransition.animateFloat(
                            initialValue = 0.97f, targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "breathe"
                        )
                        val haloAlpha by logoTransition.animateFloat(
                            initialValue = 0.10f, targetValue = 0.30f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1900, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "halo"
                        )

                        Box(contentAlignment = Alignment.Center) {
                            // Halo pulsant derrière le logo
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .graphicsLayer {
                                        val s = logoIntro.value * breathe
                                        scaleX = s; scaleY = s
                                        translationY = floatDp.dp.toPx()
                                        alpha = logoIntro.value * haloAlpha
                                    }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            // Logo
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .graphicsLayer {
                                        val s = logoIntro.value * breathe
                                        scaleX = s; scaleY = s
                                        translationY = floatDp.dp.toPx()
                                        rotationZ = (1f - logoIntro.value) * -10f
                                        alpha = logoIntro.value
                                    }
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = "Logo de l'application",
                                    modifier = Modifier.size(100.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "SalaryTracker",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Gérez vos heures de travail, simulez vos salaires et optimisez votre livret d'heures en un clin d'œil.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        // ─── Connexion Google ───────────────────────────────
                        Button(
                            onClick = onGoogleSignInClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Badge "G" Google sur pastille blanche
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continuer avec Google",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ─── Connexion par téléphone ────────────────────────
                        PhoneLoginSection(
                            phoneOtpState = phoneOtpState,
                            onPhoneSignInClick = onPhoneSignInClick,
                            onVerifyOtp = onVerifyOtp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // ─── Connexion par E-mail & Mot de passe ────────────
                        EmailPasswordLoginSection(
                            onSignIn = onEmailSignIn,
                            onSignUp = onEmailSignUp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // ─── Divider ────────────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                " ou ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ─── Compte local (sur l'appareil) ──────────────────
                        TextButton(
                            onClick = { showMockDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Compte local (données sur cet appareil)",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // ─── Importer une sauvegarde ─────────────────────────
                        TextButton(
                            onClick = onImportData,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Importer des données",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog invité
    if (showMockDialog) {
        AlertDialog(
            onDismissRequest = { showMockDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compte local")
                }
            },
            text = {
                Column {
                    Text(
                        "Saisissez un nom de profil. Toutes vos données resteront stockées uniquement sur cet appareil (aucune synchronisation cloud).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mockName,
                        onValueChange = { mockName = it },
                        label = { Text("Nom du profil") },
                        placeholder = { Text("Ex: Lucas, Hugo, Benjamin...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mockName.isNotBlank()) {
                            onLocalSignInClick(mockName.trim())
                            showMockDialog = false
                        }
                    },
                    enabled = mockName.isNotBlank()
                ) {
                    Text("Se connecter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMockDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

/** État du flux d'authentification par téléphone */
sealed interface PhoneOtpState {
    object Idle : PhoneOtpState
    object SendingCode : PhoneOtpState
    data class WaitingForCode(val phoneNumber: String) : PhoneOtpState
    object VerifyingCode : PhoneOtpState
    data class Error(val message: String) : PhoneOtpState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneLoginSection(
    phoneOtpState: PhoneOtpState,
    onPhoneSignInClick: (String) -> Unit,
    onVerifyOtp: (String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("+33") }
    var otpCode by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Connexion par téléphone (SMS)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ArrowForward else Icons.Default.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                when (phoneOtpState) {
                    is PhoneOtpState.Idle, is PhoneOtpState.Error -> {
                        if (phoneOtpState is PhoneOtpState.Error) {
                            Text(
                                phoneOtpState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Numéro de téléphone") },
                            placeholder = { Text("+33 6 12 34 56 78") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onPhoneSignInClick(phoneNumber.trim()) },
                            enabled = phoneNumber.length >= 10,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Envoyer le code SMS", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    is PhoneOtpState.SendingCode -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Envoi du code SMS…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is PhoneOtpState.WaitingForCode -> {
                        Text(
                            "Code envoyé au ${phoneOtpState.phoneNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it },
                            label = { Text("Code de vérification (6 chiffres)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onVerifyOtp(otpCode) },
                            enabled = otpCode.length == 6,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Vérifier le code", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    is PhoneOtpState.VerifyingCode -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Vérification du code…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectingStateView() {
    val infiniteTransition = rememberInfiniteTransition(label = "connecting")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer(rotationZ = rotation),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Connexion en cours...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Veuillez patienter pendant l'authentification sécurisée",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SuccessStateView() {
    val scale = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
                .clip(CircleShape)
                .background(Color(0xFFD1FAE5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Succès",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Connexion réussie !",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF047857)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Préparation de votre livret d'heures...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Onboarding ────────────────────────────────────────────────────────────────

data class OnboardingSlideData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val accentColor: Color? = null
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val slides = listOf(
        OnboardingSlideData(
            icon = Icons.Default.WavingHand,
            title = "Bienvenue sur SalaryTracker 👋",
            description = "Calculez vos salaires nets, suivez vos heures supplémentaires et gérez vos plannings au quotidien sans effort."
        ),
        OnboardingSlideData(
            icon = Icons.Default.Business,
            title = "Ajouter une entreprise 🏢",
            description = "Créez vos emplois (CDI, CDD, intérim, mission…) en quelques secondes. Importez votre contrat par IA pour tout remplir automatiquement.",
            accentColor = Color(0xFF3B82F6)
        ),
        OnboardingSlideData(
            icon = Icons.Default.Add,
            title = "Saisir vos journées 📅",
            description = "Appuyez sur + dans le tableau de bord ou le calendrier pour enregistrer vos heures. Choisissez un template ou saisissez manuellement.",
            accentColor = Color(0xFF10B981)
        ),
        OnboardingSlideData(
            icon = Icons.Default.Schedule,
            title = "Créer des templates ⏱️",
            description = "Définissez vos horaires types (matin, journée, nuit…) pour saisir une journée en un seul tap. Personnalisez les pauses.",
            accentColor = Color(0xFFF59E0B)
        ),
        OnboardingSlideData(
            icon = Icons.Default.AutoAwesome,
            title = "Saisie automatique ⚡",
            description = "Configurez des règles pour remplir automatiquement votre livret d'heures chaque jour. L'app rattrape les journées manquées à chaque ouverture.",
            accentColor = Color(0xFF8B7CF6)
        ),
        OnboardingSlideData(
            icon = Icons.Default.BarChart,
            title = "Analyser vos statistiques 📊",
            description = "Suivez vos gains nets, comparez avec vos fiches de paie importées et détectez les écarts. L'IA analyse vos bulletins automatiquement.",
            accentColor = Color(0xFFEC4899)
        ),
        OnboardingSlideData(
            icon = Icons.Default.PlayArrow,
            title = "C'est parti ! 🚀",
            description = "Vous êtes prêt(e) à commencer. Créez votre premier emploi et commencez à suivre vos heures dès maintenant."
        )
    )

    val slidesCount = slides.size
    val pagerState = rememberPagerState(pageCount = { slidesCount })
    val coroutineScope = rememberCoroutineScope()

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val slide = slides[page]
                OnboardingSlideView(
                    icon = slide.icon,
                    title = slide.title,
                    description = slide.description,
                    accentColor = slide.accentColor ?: MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(24.dp))

            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until slidesCount) {
                    val isSelected = i == pagerState.currentPage
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 8.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "indicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage < slidesCount - 1) {
                    TextButton(onClick = onFinish) {
                        Text("Passer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Suivant")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.NavigateNext, null)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Commencer !", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingSlideView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    accentColor: Color = Color.Unspecified
) {
    val effectiveColor = if (accentColor == Color.Unspecified) MaterialTheme.colorScheme.primary else accentColor

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(effectiveColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveColor,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun EmailPasswordLoginSection(
    onSignIn: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSignUp: (String, String, (Boolean, String?) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Connexion par e-mail & mot de passe",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowForward else Icons.Default.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Adresse e-mail") },
                    placeholder = { Text("exemple@mail.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Mot de passe") },
                    placeholder = { Text("••••••••") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isSignUpMode) "Création du compte…" else "Connexion…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSignUpMode) {
                            OutlinedButton(
                                onClick = { isSignUpMode = false; errorMessage = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("J'ai un compte")
                            }
                            Button(
                                onClick = {
                                    if (email.contains("@") && password.length >= 6) {
                                        loading = true
                                        onSignUp(email.trim(), password) { success, err ->
                                            loading = false
                                            if (!success) {
                                                errorMessage = err ?: "Une erreur est survenue."
                                            }
                                        }
                                    } else {
                                        errorMessage = "Veuillez entrer un e-mail valide et un mot de passe de 6 caractères min."
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = email.isNotBlank() && password.isNotBlank()
                            ) {
                                Text("S'inscrire", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { isSignUpMode = true; errorMessage = null },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Créer compte")
                            }
                            Button(
                                onClick = {
                                    if (email.contains("@") && password.isNotBlank()) {
                                        loading = true
                                        onSignIn(email.trim(), password) { success, err ->
                                            loading = false
                                            if (!success) {
                                                errorMessage = err ?: "Une erreur est survenue."
                                            }
                                        }
                                    } else {
                                        errorMessage = "Veuillez entrer un e-mail et un mot de passe valides."
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = email.isNotBlank() && password.isNotBlank()
                            ) {
                                Text("Se connecter", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
