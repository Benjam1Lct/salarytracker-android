package com.benjamin.salarytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*

@Composable
fun SubscriptionScreen(
    isSubscribed: Boolean,
    price: String?,
    aiUsage: AiUsage? = null,
    onSubscribe: () -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp, bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquareIconButton(Icons.Default.Close, onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.sub_header), color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
            }

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Purple),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.sub_title),
                color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.sub_subtitle), color = InkMuted, fontSize = 15.sp)

            Spacer(Modifier.height(28.dp))

            AppCard(padding = 20.dp) {
                FeatureLine(stringResource(R.string.sub_feature_1))
                Spacer(Modifier.height(14.dp))
                FeatureLine(stringResource(R.string.sub_feature_2))
                Spacer(Modifier.height(14.dp))
                FeatureLine(stringResource(R.string.sub_feature_3))
                Spacer(Modifier.height(14.dp))
                FeatureLine(stringResource(R.string.sub_feature_4))
            }

            Spacer(Modifier.height(28.dp))

            if (isSubscribed) {
                AppCard(padding = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = PosGreen, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.sub_active), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.sub_active_desc), color = InkMuted, fontSize = 13.sp)
                        }
                    }
                }
                if (aiUsage != null) {
                    Spacer(Modifier.height(16.dp))
                    AppCard(padding = 20.dp) {
                        Text(stringResource(R.string.sub_quota_title), color = InkMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Spacer(Modifier.height(14.dp))
                        QuotaBar(
                            label = stringResource(R.string.sub_quota_today, aiUsage.dailyUsed, aiUsage.dailyLimit),
                            fraction = aiUsage.dailyUsed.toFloat() / aiUsage.dailyLimit.coerceAtLeast(1)
                        )
                        Spacer(Modifier.height(12.dp))
                        QuotaBar(
                            label = stringResource(R.string.sub_quota_month, aiUsage.monthlyUsed, aiUsage.monthlyLimit),
                            fraction = aiUsage.monthlyUsed.toFloat() / aiUsage.monthlyLimit.coerceAtLeast(1)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                AppButton(
                    text = stringResource(R.string.sub_manage),
                    onClick = onManage,
                    filled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                AppButton(
                    text = price?.let { stringResource(R.string.sub_cta_price, it) } ?: stringResource(R.string.sub_cta),
                    onClick = onSubscribe,
                    leading = Icons.Default.AutoAwesome,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.sub_legal),
                    color = InkMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QuotaBar(label: String, fraction: Float) {
    val pct = fraction.coerceIn(0f, 1f)
    val barColor = if (pct >= 1f) NegRed else if (pct >= 0.8f) AmberAccent else Purple
    Column {
        Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(LavenderAlt)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Purple.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = PurpleDeep, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
