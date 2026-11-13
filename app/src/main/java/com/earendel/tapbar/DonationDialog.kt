package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

const val KOFI_URL = "https://ko-fi.com/earendel_lab"

fun openLink(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
    }
}

@Composable
fun donationGold(): Color =
    if (LocalIsDark.current) Color(0xFFFFC700) else Color(0xFF8F5E00)

@Composable
fun donationShimmerBase(): Color =
    if (LocalIsDark.current) Color(0xFFF2B705) else Color(0xFF8F5E00)

@Composable
fun donationShimmerHighlight(): Color =
    if (LocalIsDark.current) Color(0xFFFFF0A8) else Color(0xFFBF8A00)

@Composable
fun DonationHost(prefs: Prefs, homeVisible: Boolean) {
    val context = LocalContext.current
    var show by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(homeVisible) {
        if (!homeVisible || show) return@LaunchedEffect
        if (!prefs.hasCompletedLanguageOnboarding || !prefs.hasCompletedPermissionOnboarding) {
            return@LaunchedEffect
        }
        delay(1600)
        if (prefs.donationDue()) {
            prefs.recordDonationShown()
            show = true
        }
    }

    if (show) {
        DonationDialog(
            onDonate = {
                prefs.donationStopped = true
                show = false
                openLink(context, KOFI_URL)
            },
            onLater = { show = false },
            onStop = {
                prefs.donationStopped = true
                show = false
            }
        )
    }
}

@Composable
fun DonationDialog(
    onDonate: () -> Unit,
    onLater: () -> Unit,
    onStop: () -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val dark = LocalIsDark.current
    val gold = donationGold()
    var stopSelected by remember { mutableStateOf(false) }

    LaunchedEffect(stopSelected) {
        if (stopSelected) {
            delay(280)
            onStop()
        }
    }

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (LocalImmersive.current != null) Color.Transparent else overlayContainerColor(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Box(modifier = Modifier.immersiveSheetBackdrop()) {
                DonationDecor(modifier = Modifier.matchParentSize())
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(
                                    if (dark) R.drawable.donation_cat_dark else R.drawable.donation_cat_light
                                ),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(width = 44.dp, height = 48.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.donation_title_top),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 19.sp,
                                        lineHeight = 25.sp
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.donation_title_bottom),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 19.sp,
                                        lineHeight = 25.sp
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    color = gold
                                )
                            }
                        }
                        Text(
                            text = highlightedDonationText(stringResource(R.string.donation_body)),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        DonationButton(
                            text = stringResource(R.string.donation_donate),
                            index = 0,
                            containerColor = Color(0xFFFFC700),
                            contentColor = Color(0xFF1A1400),
                            iconRes = R.drawable.ic_kofi,
                            onClick = {
                                haptics.performLightTap(view)
                                onDonate()
                            }
                        )
                        DonationButton(
                            text = stringResource(R.string.donation_not_now),
                            index = 1,
                            containerColor = null,
                            contentColor = gold,
                            iconRes = null,
                            onClick = {
                                haptics.performLightTap(view)
                                onLater()
                            }
                        )
                    }

                    DonationStopRow(
                        isSelected = stopSelected,
                        onClick = {
                            if (!stopSelected) {
                                haptics.performSelection(view)
                                stopSelected = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DonationButton(
    text: String,
    index: Int,
    containerColor: Color?,
    contentColor: Color,
    iconRes: Int?,
    onClick: () -> Unit
) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Surface(
        shape = segmentedShape(index, 2),
        color = containerColor ?: if (LocalImmersive.current != null) {
            tint
        } else {
            tint.compositeOver(overlayContainerColor())
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DonationStopRow(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "DonationStopScale"
    )
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                role = Role.RadioButton
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
            ),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale),
            tint = dim
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.donation_never),
            style = MaterialTheme.typography.labelMedium,
            color = dim
        )
    }
}

@Composable
private fun DonationDecor(modifier: Modifier = Modifier) {
    val gold = donationGold()
    val dark = LocalIsDark.current
    val density = LocalDensity.current
    val radiusPx = with(density) { 46.dp.toPx() }
    val zigzag = remember(radiusPx) { zigzagPath(radiusPx, 12) }
    val transition = rememberInfiniteTransition(label = "DonationDecor")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DonationZigzagAngle"
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = if (dark) 0.14f else 0.18f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val cx = size.width - 12.dp.toPx()
            val cy = 12.dp.toPx()
            translate(left = cx, top = cy) {
                rotate(degrees = angle, pivot = Offset.Zero) {
                    drawPath(path = zigzag, color = gold, style = Fill)
                    drawPath(
                        path = zigzag,
                        color = gold,
                        style = Stroke(width = 6.dp.toPx(), join = StrokeJoin.Round)
                    )
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_kofi_outline),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = 104.dp)
                .size(60.dp),
            tint = gold.copy(alpha = if (dark) 0.13f else 0.16f)
        )
    }
}

private fun zigzagPath(radius: Float, points: Int): Path {
    val path = Path()
    val steps = points * 2
    val inner = radius * 0.84f
    for (i in 0 until steps) {
        val r = if (i % 2 == 0) radius else inner
        val a = (PI * 2.0 * i / steps).toFloat()
        val x = r * cos(a)
        val y = r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun highlightedDonationText(raw: String): AnnotatedString {
    val strong = SpanStyle(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val accent = SpanStyle(
        fontWeight = FontWeight.Medium,
        color = donationGold()
    )
    val pattern = remember { Regex("<(b|y)>(.*?)</\\1>") }
    return buildAnnotatedString {
        var last = 0
        for (match in pattern.findAll(raw)) {
            append(raw.substring(last, match.range.first))
            val style = if (match.groupValues[1] == "y") accent else strong
            withStyle(style) { append(match.groupValues[2]) }
            last = match.range.last + 1
        }
        append(raw.substring(last))
    }
}
