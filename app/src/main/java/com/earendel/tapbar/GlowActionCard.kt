package com.earendel.tapbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GlowShape = RoundedCornerShape(GroupLargeCorner)

private class GlowPalette(
    val base: List<Color>,
    val tint: Color,
    val tintDeep: Color,
    val bloom: Color,
    val sheen: Color,
    val warm: Color,
    val scrim: Color,
    val accent: Color,
    val title: Color,
    val body: Color,
    val shadow: Color,
    val chipTop: Color,
    val chipBottom: Color
)

private val DarkGlow = GlowPalette(
    base = listOf(Color(0xFF241C06), Color(0xFF0F0D07)),
    tint = Color(0xFFFFCF4A),
    tintDeep = Color(0xFFD99A0B),
    bloom = Color(0xFFFFE08A),
    sheen = Color.White,
    warm = Color(0xFF9C5A06),
    scrim = Color(0xFF080602),
    accent = Color(0xFFFFD45E),
    title = Color(0xFFFFFAEB),
    body = Color(0xFFD9CCA3),
    shadow = Color(0xFFFFC53A),
    chipTop = Color(0xFFFFE08A),
    chipBottom = Color(0xFFE0A412)
)

private val LightGlow = GlowPalette(
    base = listOf(Color(0xFFFFF3C9), Color(0xFFFFE394)),
    tint = Color(0xFFFFD24D),
    tintDeep = Color(0xFFF2B21C),
    bloom = Color.White,
    sheen = Color.White,
    warm = Color(0xFFC98A12),
    scrim = Color(0xFFFFF6D6),
    accent = Color(0xFF8A5A00),
    title = Color(0xFF2B2100),
    body = Color(0xFF65500F),
    shadow = Color(0xFFE5A400),
    chipTop = Color(0xFFFFFFFF),
    chipBottom = Color(0xFFFFE08A)
)

@Composable
fun GlowActionRow(
    action: ActionEntry,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val isLight = !LocalIsDark.current
    val palette = if (isLight) LightGlow else DarkGlow

    val radioScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "GlowRadioScale"
    )

    val glowBoost by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(450),
        label = "GlowBoost"
    )

    val transition = rememberInfiniteTransition(label = "GlowFlow")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GlowOrbit"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 23000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GlowDrift"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(GlowShape)
            .background(Brush.linearGradient(palette.base))
            .clickable(onClick = onSelect)
            .drawBehind {
                val w = size.width
                val h = size.height

                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.tint.copy(alpha = 0.22f + 0.08f * glowBoost),
                            palette.tintDeep.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        start = Offset(w, 0f),
                        end = Offset(0f, h)
                    )
                )

                val ax = w * (0.78f + 0.2f * cos(orbit))
                val ay = h * (0.35f + 0.38f * sin(orbit))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.tint.copy(alpha = 0.55f + 0.1f * glowBoost),
                            Color.Transparent
                        ),
                        center = Offset(ax, ay),
                        radius = w * 0.52f
                    )
                )

                val bx = w * (0.25f - 0.2f * cos(drift))
                val by = h * (0.85f - 0.3f * sin(drift))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.warm.copy(alpha = if (isLight) 0.45f else 0.58f),
                            Color.Transparent
                        ),
                        center = Offset(bx, by),
                        radius = w * 0.45f
                    )
                )

                val ix = w * (0.6f + 0.38f * sin(drift + 1f))
                val iy = h * (0.5f + 0.5f * cos(orbit * 1.5f))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.scrim.copy(alpha = if (isLight) 0.35f else 0.7f),
                            Color.Transparent
                        ),
                        center = Offset(ix, iy),
                        radius = w * 0.34f
                    )
                )

                val edge = 0.5f + 0.14f * sin(drift * 2f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to palette.scrim.copy(alpha = if (isLight) 0.3f else 0.6f),
                            edge to palette.scrim.copy(alpha = if (isLight) 0.3f else 0.64f),
                            (edge + 0.38f) to Color.Transparent
                        )
                    )
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.bloom.copy(alpha = 0.3f + 0.1f * glowBoost),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.06f, 0f),
                        radius = w * 0.45f
                    )
                )

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            palette.sheen.copy(alpha = if (isLight) 0.5f else 0.14f),
                            palette.sheen.copy(alpha = if (isLight) 0.1f else 0.03f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.55f
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.chipTop.copy(alpha = if (isLight) 0.9f else 0.28f),
                                palette.chipBottom.copy(alpha = if (isLight) 0.7f else 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = palette.accent
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(action.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = stringResource(action.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.body,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                painter = painterResource(
                    if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                ),
                contentDescription = if (isSelected) stringResource(R.string.cd_selected) else stringResource(R.string.cd_not_selected),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(scaleX = radioScale, scaleY = radioScale),
                tint = palette.accent
            )
        }
    }
}