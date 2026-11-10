package com.earendel.tapbar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun HideFromAppsSection(prefs: Prefs, onOpenPicker: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    var selectedCount by remember { mutableIntStateOf(prefs.hardDisableApps.size) }
    var usageGranted by remember { mutableStateOf(AppWatcherService.hasUsageAccess(context)) }
    var shellReady by remember { mutableStateOf(SystemToggles.available(prefs.shellMethod)) }
    var secureOk by remember { mutableStateOf(AppWatcherService.hasSecureSettings(context)) }
    var paused by remember { mutableStateOf(prefs.hardDisableActive) }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = AppWatcherService.hasUsageAccess(context)
                selectedCount = prefs.hardDisableApps.size
                shellReady = SystemToggles.available(prefs.shellMethod)
                secureOk = AppWatcherService.hasSecureSettings(context)
                paused = prefs.hardDisableActive
                AppWatcherService.syncState(context)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            AppWatcherService.syncState(context)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.hide_from_apps_title),
            style = sectionHeaderStyle(),
            color = sectionHeaderColor(),
            modifier = Modifier.sectionHeaderInset()
        )

        val usageIdx = 0
        val setupIdx = if (usageGranted) 0 else 1
        val pickerIdx = (if (usageGranted) 0 else 1) + (if (secureOk) 0 else 1)
        val cardCount = pickerIdx + 1

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!usageGranted) {
                SegmentedCard(
                    index = usageIdx,
                    count = cardCount,
                    onClick = {
                        haptics.performLightTap(view)
                        openUsageAccessSettings(context)
                    }
                ) {
                    HideFromAppsRow(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.grant_usage_access),
                        desc = stringResource(R.string.usage_access_desc)
                    )
                }
            }

            if (!secureOk) {
                SegmentedCard(
                    index = setupIdx,
                    count = cardCount,
                    onClick = {
                        haptics.performLightTap(view)
                        onOpenSetup()
                    }
                ) {
                    HideFromAppsRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.hide_setup_title),
                        desc = stringResource(R.string.hide_setup_card_desc)
                    )
                }
            }

            SegmentedCard(
                index = pickerIdx,
                count = cardCount,
                onClick = {
                    haptics.performLightTap(view)
                    onOpenPicker()
                }
            ) {
                HideFromAppsRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = if (selectedCount == 0) {
                        stringResource(R.string.add_app)
                    } else {
                        stringResource(R.string.hide_from_apps_count, selectedCount)
                    },
                    desc = when {
                        selectedCount > 0 && paused -> stringResource(R.string.hide_from_apps_active)
                        selectedCount > 0 && !secureOk && !shellReady -> stringResource(R.string.hide_from_apps_needs_shell)
                        else -> stringResource(R.string.hide_from_apps_desc)
                    }
                )
            }
        }
    }
}

@Composable
private fun HideFromAppsRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun openUsageAccessSettings(context: Context) {
    val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
    try {
        context.startActivity(direct)
    } catch (_: Exception) {
        runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideFromAppsPickerScreen(prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    BackHandler(onBack = {
        haptics.performLightTap(view)
        onBack()
    })
    var apps by remember { mutableStateOf<List<AppEntry>>(cachedApps ?: emptyList()) }
    var selected by remember { mutableStateOf(prefs.hardDisableApps) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val valid = validCachedApps(context)
        if (valid.isNullOrEmpty()) {
            batchLoadApps(context) { newBatch -> apps = newBatch }
        } else if (valid.size != apps.size) {
            apps = valid
        }
    }

    val filteredApps = remember(apps, searchQuery, selected) {
        val q = searchQuery.trim().lowercase()
        val list = if (q.isEmpty()) {
            apps
        } else {
            val terms = q.split(' ').filter { it.isNotEmpty() }
            apps.filter { app ->
                val label = app.label.lowercase()
                val pkg = app.packageName.lowercase()
                terms.all { term -> label.contains(term) || pkg.contains(term) }
            }
        }
        list.sortedWith(
            compareByDescending<AppEntry> { selected.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
        )
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty() && filteredApps.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = maxOf(imePadding, navBarPadding) + 16.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hide_from_apps_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performLightTap(view)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
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
                placeholder = { Text(stringResource(R.string.search_apps), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.cd_search),
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
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (filteredApps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (apps.isEmpty()) stringResource(R.string.loading_apps) else stringResource(R.string.no_apps_found),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        key = { _, app -> app.packageName },
                        contentType = { _, _ -> "app_row" }
                    ) { index, app ->
                        val isSelected = selected.contains(app.packageName)
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "HideSelectScale"
                        )

                        Box(modifier = Modifier.animateItem()) {
                            AppRow(
                                app = app,
                                index = index,
                                totalCount = filteredApps.size,
                                onClick = {
                                    val newState = !isSelected
                                    haptics.performToggle(view, newState)
                                    val newSet = selected.toMutableSet()
                                    if (isSelected) newSet.remove(app.packageName) else newSet.add(app.packageName)
                                    selected = newSet
                                    prefs.hardDisableApps = newSet
                                    AppWatcherService.syncState(context)
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check_bold),
                                            contentDescription = stringResource(R.string.cd_selected),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer(scaleX = scale, scaleY = scale),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_radio_unselected),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer(scaleX = scale, scaleY = scale),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideFromAppsSetupScreen(prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    BackHandler(onBack = {
        haptics.performLightTap(view)
        onBack()
    })
    var granted by remember { mutableStateOf(AppWatcherService.hasSecureSettings(context)) }
    var shellReady by remember { mutableStateOf(SystemToggles.available(prefs.shellMethod)) }
    val command = "adb shell pm grant " + context.packageName + " android.permission.WRITE_SECURE_SETTINGS"
    val copiedText = stringResource(R.string.hide_setup_copied)
    val failedText = stringResource(R.string.hide_setup_failed)

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = AppWatcherService.hasSecureSettings(context)
                shellReady = SystemToggles.available(prefs.shellMethod)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hide_setup_title)) },
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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { pad ->
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarPadding + 16.dp)
        ) {
            if (granted) {
                SegmentedCard(index = 0, count = 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_bold),
                            contentDescription = stringResource(R.string.cd_selected),
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.hide_setup_done),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegmentedCard(
                        index = 0,
                        count = 3,
                        onClick = {
                            if (shellReady) {
                                haptics.performLightTap(view)
                                val cmd = "pm grant " + context.packageName + " android.permission.WRITE_SECURE_SETTINGS"
                                ShellAccess.run(context.applicationContext, cmd, prefs.shellMethod) { ok ->
                                    view.post {
                                        granted = AppWatcherService.hasSecureSettings(context)
                                        if (!ok && !granted) {
                                            Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        HideFromAppsRow(
                            icon = Icons.Rounded.PlayArrow,
                            title = stringResource(R.string.hide_setup_shell_title),
                            desc = if (shellReady) {
                                stringResource(R.string.hide_setup_shell_desc)
                            } else {
                                stringResource(R.string.hide_setup_shell_missing)
                            }
                        )
                    }
                    SegmentedCard(
                        index = 1,
                        count = 3,
                        onClick = {
                            haptics.performLightTap(view)
                            copyCommand(context, command, copiedText)
                        }
                    ) {
                        HideFromAppsRow(
                            icon = Icons.Rounded.Terminal,
                            title = stringResource(R.string.hide_setup_pc_title),
                            desc = stringResource(R.string.hide_setup_pc_desc)
                        )
                    }
                    SegmentedCard(
                        index = 2,
                        count = 3,
                        onClick = {
                            haptics.performLightTap(view)
                            copyCommand(context, command, copiedText)
                        }
                    ) {
                        Text(
                            text = command,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun copyCommand(context: Context, text: String, toastText: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("command", text))
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }
}
