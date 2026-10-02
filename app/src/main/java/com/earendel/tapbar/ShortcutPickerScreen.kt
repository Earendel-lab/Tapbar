package com.earendel.tapbar

import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val shortcutIconCache = LruCache<String, ImageBitmap>(100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutPickerScreen(
    prefs: Prefs,
    gestureMode: Int = 0,
    zone: Int = 0,
    onBack: () -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    BackHandler(onBack = {
        haptics.performLightTap(view)
        onBack()
    })
    val context = LocalContext.current
    var appsWithShortcuts by remember { mutableStateOf<List<AppWithShortcuts>>(AppShortcuts.cached() ?: emptyList()) }
    var isLoading by remember { mutableStateOf(appsWithShortcuts.isEmpty()) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedAppPkg by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val selectedUri = when (gestureMode) {
        1 -> prefs.getDoubleTapShortcutUri(zone)
        2 -> prefs.getTripleTapShortcutUri(zone)
        3 -> prefs.getLongPressShortcutUri(zone)
        else -> prefs.getSingleTapShortcutUri(zone)
    }
    val selectedType = when (gestureMode) {
        1 -> prefs.getDoubleTapType(zone)
        2 -> prefs.getTripleTapType(zone)
        3 -> prefs.getLongPressType(zone)
        else -> prefs.getSingleTapType(zone)
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            AppShortcuts.loadAll(context, forceRefresh = true)
        }
        appsWithShortcuts = loaded
        isLoading = false
    }

    val filteredApps = remember(appsWithShortcuts, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            appsWithShortcuts
        } else {
            appsWithShortcuts.mapNotNull { appItem ->
                val appMatches = appItem.appLabel.lowercase().contains(q) || appItem.packageName.lowercase().contains(q)
                if (appMatches) {
                    appItem
                } else {
                    val matchingShortcuts = appItem.shortcuts.filter { sc ->
                        sc.label.lowercase().contains(q)
                    }
                    if (matchingShortcuts.isNotEmpty()) {
                        appItem.copy(shortcuts = matchingShortcuts)
                    } else null
                }
            }
        }
    }

    val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = maxOf(imePadding, navBarPadding) + 16.dp

    val screenTitle = when (gestureMode) {
        1 -> "Double tap shortcut"
        2 -> "Triple tap shortcut"
        3 -> "Long press shortcut"
        else -> "Choose app shortcut"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performLightTap(view)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding())
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search shortcuts...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            haptics.performLightTap(view)
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (isLoading && filteredApps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Loading shortcuts...", color = MaterialTheme.colorScheme.onSurface)
                }
            } else if (filteredApps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No shortcuts found", color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = bottomPadding,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = filteredApps,
                        key = { _, item -> item.packageName }
                    ) { index, appItem ->
                        val isExpanded = if (searchQuery.isNotEmpty()) true else (expandedAppPkg == appItem.packageName)
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            label = "ChevronRotation"
                        )

                        var appIcon by remember(appItem.packageName) {
                            mutableStateOf<ImageBitmap?>(shortcutIconCache.get(appItem.packageName))
                        }

                        if (appIcon == null) {
                            LaunchedEffect(appItem.packageName) {
                                appIcon = withContext(Dispatchers.IO) {
                                    runCatching {
                                        context.packageManager
                                            .getApplicationIcon(appItem.packageName)
                                            .toBitmap(72, 72)
                                            .asImageBitmap()
                                    }.getOrNull()?.also { shortcutIconCache.put(appItem.packageName, it) }
                                }
                            }
                        }

                        SegmentedCard(
                            index = index,
                            count = filteredApps.size
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(
                                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (searchQuery.isEmpty()) {
                                                haptics.performExpandCollapse(view)
                                                expandedAppPkg = if (isExpanded) null else appItem.packageName
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (appIcon != null) {
                                        Image(
                                            bitmap = appIcon!!,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(36.dp))
                                    }

                                    Spacer(Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appItem.appLabel,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${appItem.shortcuts.size} shortcut${if (appItem.shortcuts.size > 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .rotate(chevronRotation),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = fadeIn(tween(180)) + expandVertically(tween(200, easing = FastOutSlowInEasing)),
                                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            thickness = 0.5.dp
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f))
                                                .padding(vertical = 4.dp)
                                        ) {
                                            appItem.shortcuts.forEach { shortcut ->
                                                val isSelected = selectedType == 2 && selectedUri == shortcut.intentUri
                                                val radioScale by animateFloatAsState(
                                                    targetValue = if (isSelected) 1.15f else 1.0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "ShortcutRadioScale"
                                                )

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            haptics.performSelection(view)
                                                            when (gestureMode) {
                                                                1 -> {
                                                                prefs.setDoubleTapType(zone, 2)
                                                                prefs.setDoubleTapShortcutUri(zone, shortcut.intentUri)
                                                                prefs.setDoubleTapShortcutLabel(zone, shortcut.label)
                                                                prefs.setDoubleTapTargetPkg(zone, shortcut.packageName)
                                                                prefs.setDoubleTapTargetLabel(zone, appItem.appLabel)
                                                                }
                                                                2 -> {
                                                                prefs.setTripleTapType(zone, 2)
                                                                prefs.setTripleTapShortcutUri(zone, shortcut.intentUri)
                                                                prefs.setTripleTapShortcutLabel(zone, shortcut.label)
                                                                prefs.setTripleTapTargetPkg(zone, shortcut.packageName)
                                                                prefs.setTripleTapTargetLabel(zone, appItem.appLabel)
                                                                }
                                                                3 -> {
                                                                prefs.setLongPressType(zone, 2)
                                                                prefs.setLongPressShortcutUri(zone, shortcut.intentUri)
                                                                prefs.setLongPressShortcutLabel(zone, shortcut.label)
                                                                prefs.setLongPressTargetPkg(zone, shortcut.packageName)
                                                                prefs.setLongPressTargetLabel(zone, appItem.appLabel)
                                                                }
                                                                else -> {
                                                                prefs.setSingleTapType(zone, 2)
                                                                prefs.setSingleTapShortcutUri(zone, shortcut.intentUri)
                                                                prefs.setSingleTapShortcutLabel(zone, shortcut.label)
                                                                prefs.setSingleTapTargetPkg(zone, shortcut.packageName)
                                                                prefs.setSingleTapTargetLabel(zone, appItem.appLabel)
                                                                    if (zone == 0) {
                                                                        prefs.targetPackage = shortcut.packageName
                                                                        prefs.targetLabel = appItem.appLabel
                                                                    }
                                                                }
                                                            }
                                                            onBack()
                                                        }
                                                        .padding(start = 28.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    Spacer(Modifier.width(12.dp))

                                                    Text(
                                                        text = shortcut.label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    Spacer(Modifier.width(12.dp))

                                                    Icon(
                                                        painter = painterResource(
                                                            if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                                                        ),
                                                        contentDescription = if (isSelected) "Selected" else "Not selected",
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .graphicsLayer(scaleX = radioScale, scaleY = radioScale),
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
