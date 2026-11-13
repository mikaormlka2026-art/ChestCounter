package com.chestcounter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chestcounter.ui.theme.Palette
import com.chestcounter.ui.theme.Spacing

enum class GlassTone { LIGHT, NAVY }

enum class GlassButtonKind { PRIMARY, GLASS }

data class DayItem(val weekday: String, val number: String, val count: Int)

data class NavEntry(val label: String, val glyph: String)

/** Surface en verre : dégradé translucide, contour lumineux, reflet en haut. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    tone: GlassTone = GlassTone.LIGHT
): Modifier {
    val navy = tone == GlassTone.NAVY
    val fill = if (navy) {
        Brush.linearGradient(listOf(Palette.PanelTop, Palette.PanelBottom))
    } else {
        Brush.linearGradient(listOf(Palette.GlassHigh, Palette.GlassLow))
    }
    val border = Brush.linearGradient(
        listOf(Palette.GlassBorder, Color(0x33FFFFFF), Color(0x80FFFFFF))
    )
    val base = if (navy) {
        this.shadow(
            elevation = 14.dp,
            shape = shape,
            clip = false,
            ambientColor = Color(0x501E1E8C),
            spotColor = Color(0x661E1E8C)
        )
    } else {
        this
    }
    return base
        .clip(shape)
        .background(fill, shape)
        .border(1.dp, border, shape)
        .drawWithContent {
            drawContent()
            val y = 1.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xE6FFFFFF), Color.Transparent)
                ),
                start = Offset(size.width * 0.12f, y),
                end = Offset(size.width * 0.88f, y),
                strokeWidth = y
            )
        }
}

private fun DrawScope.glowBlob(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float = 0.8f
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.45f),
                color.copy(alpha = 0f)
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Fond lilas avec halos bleu / violet / rose qui dérivent très lentement. */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Palette.BgTop, Palette.BgMid, Palette.BgBottom)
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = phase
            val w = size.width
            val h = size.height
            val r = maxOf(w, h)
            glowBlob(
                Offset(w * (0.02f + 0.08f * p), h * (0.68f - 0.05f * p)),
                r * 0.55f,
                Color(0xFF4F6BFF)
            )
            glowBlob(
                Offset(w * (1.0f - 0.06f * p), h * (0.22f + 0.05f * p)),
                r * 0.5f,
                Color(0xFFB46BFF)
            )
            glowBlob(
                Offset(w * (0.18f + 0.1f * p), h * (0.3f - 0.04f * p)),
                r * 0.3f,
                Palette.Pink,
                0.55f
            )
            glowBlob(
                Offset(w * (0.4f + 0.1f * p), h * 1.02f),
                r * 0.55f,
                Color(0xFF2A3DFF)
            )
            glowBlob(
                Offset(w * 0.2f, 0f),
                r * 0.4f,
                Palette.SkyBlue,
                0.6f
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.LIGHT,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit
) {
    val fg = if (tone == GlassTone.NAVY) Palette.OnAccent else Palette.Navy
    Column(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), tone)
            .padding(contentPadding)
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            content()
        }
    }
}

/** Petite tuile translucide, pensée pour être posée dans un GlassCard NAVY. */
@Composable
fun GlassTile(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(Palette.GlassTile, shape)
            .border(1.dp, Color(0x3DFFFFFF), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.OnAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xE6FFFFFF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: GlassButtonKind = GlassButtonKind.GLASS,
    contentColor: Color = Palette.Navy
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(110),
        label = "press"
    )
    val shape = RoundedCornerShape(24.dp)
    val look = if (kind == GlassButtonKind.PRIMARY) {
        Modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x552F46D8),
                spotColor = Color(0x667A3CFF)
            )
            .clip(shape)
            .background(Palette.AccentBrush, shape)
    } else {
        Modifier.glass(shape)
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .then(look)
            .background(Color.White.copy(alpha = if (pressed) 0.18f else 0f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (kind == GlassButtonKind.PRIMARY) Palette.OnAccent else contentColor,
            textAlign = TextAlign.Center
        )
    }
}

/** Gros compteur : dégradé dans le texte, petit rebond quand la valeur change. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge
) {
    val pulse = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(value) {
        if (first) {
            first = false
        } else {
            pulse.snapTo(1.1f)
            pulse.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f))
        }
    }
    Text(
        "$value",
        modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
            transformOrigin = TransformOrigin(0f, 0.5f)
        },
        style = style.copy(brush = Palette.CounterBrush)
    )
}

@Composable
fun DayStrip(
    days: List<DayItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { i, d ->
            val isSel = i == selected
            val p by animateFloatAsState(
                targetValue = if (isSel) 1f else 0f,
                animationSpec = tween(240),
                label = "day$i"
            )
            val src = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(shape)
                    .background(Color(0x66FFFFFF))
                    .border(1.dp, Color(0x99FFFFFF), shape)
                    .clickable(interactionSource = src, indication = null, role = Role.Tab) {
                        onSelect(i)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .alpha(p)
                        .background(Palette.AccentBrush)
                )
                val fg = lerp(Palette.Navy, Palette.OnAccent, p)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.weekday,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = fg,
                        maxLines = 1
                    )
                    Text(
                        d.number,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = fg
                    )
                    Text("${d.count}", fontSize = 10.sp, color = fg.copy(alpha = 0.75f))
                }
            }
        }
    }
}

/** Barres par jour (vraies données), qui montent à l'apparition. */
@Composable
fun TrendChart(
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(800),
        label = "bars"
    )
    val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val panel = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(panel)
            .background(Color(0x66FFFFFF))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            val src = remember { MutableInteractionSource() }
            val frac = ((v.toFloat() / maxV).coerceAtLeast(0.06f) * progress).coerceIn(0.02f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = src, indication = null, role = Role.Tab) {
                        onSelect(i)
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                val barBrush = if (i == selected) {
                    Brush.verticalGradient(listOf(Palette.Accent1, Palette.Navy))
                } else {
                    Brush.verticalGradient(listOf(Color(0x666450C8), Color(0x666450C8)))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(5.dp))
                        .background(barBrush)
                )
            }
        }
    }
}

@Composable
fun GlassNavBar(
    items: List<NavEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(33.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, e ->
            NavBarItem(e, i == selected) { onSelect(i) }
        }
    }
}

@Composable
private fun NavBarItem(entry: NavEntry, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    if (selected) {
        Row(
            Modifier
                .semantics { contentDescription = entry.label }
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Palette.AccentBrush)
                .clickable(interactionSource = src, indication = null, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(entry.glyph, fontSize = 18.sp, color = Palette.OnAccent)
            Spacer(Modifier.width(6.dp))
            Text(
                entry.label,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.OnAccent,
                maxLines = 1
            )
        }
    } else {
        Box(
            Modifier
                .semantics { contentDescription = entry.label }
                .size(44.dp)
                .clip(CircleShape)
                .clickable(interactionSource = src, indication = null, role = Role.Tab, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.glyph, fontSize = 20.sp, color = Palette.TextDim)
        }
    }
}

/** Version verticale de la navigation, pour les écrans larges (tablette). */
@Composable
fun GlassNavRail(
    items: List<NavEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .glass(RoundedCornerShape(30.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEachIndexed { i, e ->
            val sel = i == selected
            val fg = if (sel) Palette.OnAccent else Palette.TextDim
            Column(
                Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .then(if (sel) Modifier.background(Palette.AccentBrush) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(e.glyph, fontSize = 20.sp, color = fg)
                Text(
                    e.label,
                    fontSize = 10.sp,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
