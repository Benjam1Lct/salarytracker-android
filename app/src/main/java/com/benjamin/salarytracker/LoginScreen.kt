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
import androidx.compose.ui.res.stringResource
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
                                    contentDescription = stringResource(R.string.login_logo_cd),
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
                            text = stringResource(R.string.login_tagline),
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
                                    text = stringResource(R.string.login_google),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

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
                                stringResource(R.string.login_or),
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
                                text = stringResource(R.string.login_local_account),
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
                                text = stringResource(R.string.login_import_data),
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
                    Text(stringResource(R.string.login_local_title))
                }
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.login_local_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mockName,
                        onValueChange = { mockName = it },
                        label = { Text(stringResource(R.string.login_profile_name)) },
                        placeholder = { Text(stringResource(R.string.login_profile_placeholder)) },
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
                    Text(stringResource(R.string.login_signin))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMockDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
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
            text = stringResource(R.string.login_connecting),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_connecting_desc),
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
                contentDescription = stringResource(R.string.login_success_cd),
                tint = Color(0xFF10B981),
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.login_success),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF047857)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_preparing),
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
            title = stringResource(R.string.ob_t1),
            description = stringResource(R.string.ob_d1)
        ),
        OnboardingSlideData(
            icon = Icons.Default.Business,
            title = stringResource(R.string.ob_t2),
            description = stringResource(R.string.ob_d2),
            accentColor = Color(0xFF3B82F6)
        ),
        OnboardingSlideData(
            icon = Icons.Default.Add,
            title = stringResource(R.string.ob_t3),
            description = stringResource(R.string.ob_d3),
            accentColor = Color(0xFF10B981)
        ),
        OnboardingSlideData(
            icon = Icons.Default.Schedule,
            title = stringResource(R.string.ob_t4),
            description = stringResource(R.string.ob_d4),
            accentColor = Color(0xFFF59E0B)
        ),
        OnboardingSlideData(
            icon = Icons.Default.AutoAwesome,
            title = stringResource(R.string.ob_t5),
            description = stringResource(R.string.ob_d5),
            accentColor = Color(0xFF8B7CF6)
        ),
        OnboardingSlideData(
            icon = Icons.Default.BarChart,
            title = stringResource(R.string.ob_t6),
            description = stringResource(R.string.ob_d6),
            accentColor = Color(0xFFEC4899)
        ),
        OnboardingSlideData(
            icon = Icons.Default.PlayArrow,
            title = stringResource(R.string.ob_t7),
            description = stringResource(R.string.ob_d7)
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
                        Text(stringResource(R.string.ob_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.ob_next))
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
                        Text(stringResource(R.string.ob_start), fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
    val genericError = stringResource(R.string.set_generic_error)
    val errSignup = stringResource(R.string.login_err_signup)
    val errSignin = stringResource(R.string.login_err_signin)

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
                        text = stringResource(R.string.login_email_section),
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
                    label = { Text(stringResource(R.string.set_email_addr)) },
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
                    label = { Text(stringResource(R.string.set_password)) },
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
                            text = if (isSignUpMode) stringResource(R.string.login_creating) else stringResource(R.string.login_signing),
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
                                Text(stringResource(R.string.login_have_account))
                            }
                            Button(
                                onClick = {
                                    if (email.contains("@") && password.length >= 6) {
                                        loading = true
                                        onSignUp(email.trim(), password) { success, err ->
                                            loading = false
                                            if (!success) {
                                                errorMessage = err ?: genericError
                                            }
                                        }
                                    } else {
                                        errorMessage = errSignup
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = email.isNotBlank() && password.isNotBlank()
                            ) {
                                Text(stringResource(R.string.login_signup), fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { isSignUpMode = true; errorMessage = null },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.login_create_account))
                            }
                            Button(
                                onClick = {
                                    if (email.contains("@") && password.isNotBlank()) {
                                        loading = true
                                        onSignIn(email.trim(), password) { success, err ->
                                            loading = false
                                            if (!success) {
                                                errorMessage = err ?: genericError
                                            }
                                        }
                                    } else {
                                        errorMessage = errSignin
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = email.isNotBlank() && password.isNotBlank()
                            ) {
                                Text(stringResource(R.string.login_signin), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
