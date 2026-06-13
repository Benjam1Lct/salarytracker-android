package com.benjamin.salarytracker

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Système "Notivo" : cartes blanches arrondies, accent violet, widgets sombres. */

private val CardShape = RoundedCornerShape(22.dp)
private val PillShape = RoundedCornerShape(percent = 50)
private val FieldShape = RoundedCornerShape(14.dp)

@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

// ── Carte blanche ─────────────────────────────────────────────────────────────

@Composable
fun AppCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    padding: Dp = 18.dp,
    fill: Color = CardWhite,
    shadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .then(if (shadow) Modifier.shadow(10.dp, CardShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f)) else Modifier)
        .clip(CardShape)
        .background(fill)
        .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
        .padding(padding)
    Column(modifier = base, content = content)
}

// ── Boutons (pilule violette) ─────────────────────────────────────────────────

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    filled: Boolean = true,
    accent: Color = Color.Unspecified
) {
    val localAccent = if (accent == Color.Unspecified) MaterialTheme.colorScheme.primary else accent
    val fg = if (filled) Color.White else localAccent
    Row(
        modifier = modifier
            .clip(PillShape)
            .then(
                if (filled) Modifier.background(localAccent)
                else Modifier.background(localAccent.copy(alpha = 0.10f))
            )
            .pressable(onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Petit bouton icône carré (header) — variante active noire. */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 42.dp
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (active) WidgetGray else CardWhite)
            .then(if (!active) Modifier.border(BorderStroke(1.dp, OutlineLt), shape) else Modifier)
            .pressable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) Color.White else Ink, modifier = Modifier.size(20.dp))
    }
}

/** Sélecteur segmenté (Semaine / Mois / Année). Actif = pilule noire. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (active) WidgetGray else Color.Transparent)
                    .pressable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else InkMuted,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

// ── Champ de saisie ────────────────────────────────────────────────────────────

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, OutlineLt), FieldShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, null, tint = InkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = InkMuted, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Divers ──────────────────────────────────────────────────────────────────

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(OutlineLt))
}

@Composable
fun SectionLabelMini(text: String, modifier: Modifier = Modifier, color: Color = InkMuted) {
    Text(text, color = color, fontSize = 12.sp, letterSpacing = 0.3.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

@Composable
fun Dot(color: Color = Color.Unspecified, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val localColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(50)).background(localColor))
}

// ── Widget sombre : jauge en arc (style "Campaign Overview") ─────────────────

@Composable
fun ArcGaugeCard(
    title: String,
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    centerLabel: String,
    centerValue: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    dark: Boolean = true
) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1200, easing = FastOutSlowInEasing), label = "arc")

    val bg       = if (dark) WidgetDark else CardWhite
    val txt      = if (dark) OnWidget else Ink
    val mut      = if (dark) OnWidgetMut else InkMuted
    val trackCol = if (dark) WidgetTrack else MaterialTheme.colorScheme.surfaceVariant
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .then(if (!dark) Modifier.shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f)) else Modifier)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .padding(22.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = mut, fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
                if (onAction != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable { onAction() }
                            .padding(7.dp)
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Edit,
                            contentDescription = "Modifier l'objectif",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(leftValue, color = txt, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(leftLabel, color = mut, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rightValue, color = txt, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(rightLabel, color = mut, fontSize = 13.sp)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                // Jauge type speedomètre : arc de 240° ouvert vers le bas
                val startAngle = 150f
                val sweep = 240f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 13.dp.toPx()
                    val cx = size.width / 2f
                    val cy = size.height * 0.58f
                    val r = min(size.width / 2f, cy) - stroke
                    val topLeft = Offset(cx - r, cy - r)
                    val arcSize = Size(r * 2, r * 2)

                    // Piste complète
                    drawArc(trackCol, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    // Progression
                    if (anim > 0.001f) {
                        drawArc(
                            Brush.sweepGradient(listOf(secondaryColor, primaryColor, tertiaryColor, secondaryColor)),
                            startAngle, sweep * anim, false, topLeft, arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                    // Knob à l'extrémité
                    val a = Math.toRadians((startAngle + sweep * anim).toDouble())
                    val dx = cx + r * cos(a).toFloat()
                    val dy = cy + r * sin(a).toFloat()
                    drawCircle(primaryColor.copy(alpha = 0.25f), stroke / 2f + 9.dp.toPx(), Offset(dx, dy)) // halo
                    drawCircle(if (dark) Color.White else CardWhite, stroke / 2f + 4.dp.toPx(), Offset(dx, dy))
                    drawCircle(primaryColor, stroke / 2f, Offset(dx, dy))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = (-8).dp)
                ) {
                    Text(centerLabel, color = mut, fontSize = 12.sp)
                    Text(
                        centerValue,
                        color = txt,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp
                    )
                }
            }
        }
    }
}

/** Barre à ticks verticaux (progression). */
@Composable
fun TickBar(progress: Float, modifier: Modifier = Modifier, ticks: Int = 44) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "ticks")
    val filled = (ticks * anim).toInt()
    Row(modifier = modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(ticks) { i ->
            val color = when {
                i < filled - 4 -> WidgetGray
                i < filled -> MaterialTheme.colorScheme.primary
                else -> OutlineLt
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}
