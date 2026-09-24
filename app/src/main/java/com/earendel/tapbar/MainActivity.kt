@file:OptIn(ExperimentalCoroutinesApi::class)

package com.earendel.tapbar

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private var cachedApps: List<AppEntry>? = null
private val iconCache = LruCache<String, ImageBitmap>(250)
private val iconDispatcher = Dispatchers.IO.limitedParallelism(2)

object DeviceCapability {
    val isHighEnd: Boolean by lazy {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        cores >= 8 && maxMemoryMb >= 256
    }

    val initialBatchSize: Int get() = if (isHighEnd) 24 else 12
    val chunkBatchSize: Int get() = if (isHighEnd) 30 else 15
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private val themeMode = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        prefs = Prefs(this)
        themeMode.intValue = prefs.themeMode
        setContent { AppRoot(prefs, themeMode) }

        val appCtx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            if (cachedApps == null) {
                batchLoadApps(appCtx) { _ -> }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        themeMode.intValue = prefs.themeMode
        TapZone.previewRequested = true
        if (prefs.serviceEnabled && !TapAccessibilityService.isEnabled(this) && Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
        TapZone.active?.setPreview(true)

        val target = prefs.targetPackage
        if (target != null && packageManager.getLaunchIntentForPackage(target) == null) {
            prefs.targetPackage = null
            prefs.targetLabel = null
        }
        val blocked = prefs.blockedPackages
        if (blocked.isNotEmpty()) {
            val validBlocked = blocked.filter { packageManager.getLaunchIntentForPackage(it) != null }.toSet()
            if (validBlocked.size != blocked.size) {
                prefs.blockedPackages = validBlocked
            }
        }
    }

    override fun onPause() {
        super.onPause()
        TapZone.previewRequested = false
        TapZone.active?.setPreview(false)
        if (!prefs.serviceEnabled && !TapAccessibilityService.isEnabled(this)) {
            OverlayService.stop(this)
        }
    }
}

@Composable
fun AppRoot(prefs: Prefs, themeMode: MutableIntState) {
    var screen by remember { mutableStateOf("home") }

    StatusTapTheme(themeMode = themeMode.intValue) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == "picker" || targetState == "filter_picker") {
                    (slideInVertically(
                        initialOffsetY = { (it * 0.08f).toInt() },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(150))
                        )
                } else {
                    fadeIn(animationSpec = tween(200))
                        .togetherWith(
                            slideOutVertically(
                                targetOffsetY = { (it * 0.08f).toInt() },
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(180))
                        )
                }
            },
            label = "ScreenTransition"
        ) { currentScreen ->
            when (currentScreen) {
                "picker" -> AppPickerScreen(prefs) { screen = "home" }
                "filter_picker" -> FilterPickerScreen(prefs) { screen = "home" }
                else -> HomeScreen(
                    prefs = prefs,
                    onOpenPicker = { screen = "picker" },
                    onOpenFilterPicker = { screen = "filter_picker" }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: Prefs,
    onOpenPicker: () -> Unit,
    onOpenFilterPicker: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var a11yOn by remember { mutableStateOf(TapAccessibilityService.isEnabled(context)) }
    var enabled by remember { mutableStateOf(prefs.serviceEnabled) }
    var autoStart by remember { mutableStateOf(prefs.autoStart) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = TapAccessibilityService.isEnabled(context)
                hasOverlay = Settings.canDrawOverlays(context)
                enabled = prefs.serviceEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var x by remember { mutableIntStateOf(prefs.posX) }
    var y by remember { mutableIntStateOf(prefs.posY) }
    var w by remember { mutableIntStateOf(prefs.zoneWidth) }
    var h by remember { mutableIntStateOf(prefs.zoneHeight) }

    val targetLabel = prefs.targetLabel ?: "Clock"
    val targetPackage = prefs.targetPackage

    var targetIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(targetPackage) {
        if (targetPackage != null) {
            targetIcon = iconCache.get(targetPackage)
            if (targetIcon == null) {
                targetIcon = withContext(iconDispatcher) {
                    runCatching {
                        context.packageManager
                            .getApplicationIcon(targetPackage)
                            .toBitmap(80, 80)
                            .asImageBitmap()
                    }.getOrNull()?.also { iconCache.put(targetPackage, it) }
                }
            }
        }
    }

    val cutoutLeft  = WindowInsets.displayCutout.getLeft(density, layoutDir)
    val cutoutRight = WindowInsets.displayCutout.getRight(density, layoutDir)

    LaunchedEffect(Unit) {
        if (!prefs.hasSetInitialPosition) {
            val leftDp  = (cutoutLeft  / density.density).toInt()
            val rightDp = (cutoutRight / density.density).toInt()

            val detectedX = when {
                leftDp > 16 -> leftDp + 8
                rightDp > 16 -> 12
                else -> 12
            }
            x = detectedX
            prefs.posX = detectedX
            prefs.hasSetInitialPosition = true

            TapZone.active?.updateGeometry(x, y, w, h)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun pushLiveGeometry() {
        val live = TapZone.active
        if (live != null) {
            live.updateGeometryLive(x, y, w, h)
        }
    }

    fun saveGeometry() {
        prefs.posX = x
        prefs.posY = y
        prefs.zoneWidth = w
        prefs.zoneHeight = h
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Tapbar") },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Settings",
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
                .verticalScroll(rememberScrollState())
                .padding(
                    top = pad.calculateTopPadding(),
                    bottom = navBarPadding + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (!a11yOn) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Accessibility Service",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SegmentedCard(index = 0, count = 1) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Without the accessibility service, Android forces the zone underneath "
                                    + "the status bar and the system takes those taps. Turn the service "
                                    + "on to put the zone on the clock itself.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val componentName = ComponentName(context, TapAccessibilityService::class.java).flattenToString()
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        putExtra(":settings:fragment_args_key", componentName)
                                        putExtra(":settings:show_fragment_args", Bundle().apply {
                                            putString(":settings:fragment_args_key", componentName)
                                        })
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text("Turn on accessibility service")
                            }
                        }
                    }
                }
            }

            if (!hasOverlay && !a11yOn) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Permission needed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SegmentedCard(index = 0, count = 1) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Tapbar needs the \"Display over other apps\" permission to place " +
                                    "its tap zone on screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:" + context.packageName)
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text("Grant permission")
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Tap zone",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegmentedCard(index = 0, count = 2) {
                        SwitchRow(
                            label = "Enable tap zone",
                            checked = enabled,
                            onChange = {
                                enabled = it
                                prefs.serviceEnabled = it
                                if (it && !a11yOn && Settings.canDrawOverlays(context)) OverlayService.start(context)
                                if (!it) {
                                    OverlayService.stop(context)
                                    TapZone.active?.detach()
                                } else {
                                    TapZone.active?.attach()
                                }
                            }
                        )
                    }
                    SegmentedCard(index = 1, count = 2) {
                        SwitchRow(
                            label = "Start automatically on boot",
                            checked = autoStart,
                            onChange = {
                                autoStart = it
                                prefs.autoStart = it
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Position and size",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegmentedCard(index = 0, count = 4) {
                        DpSlider(
                            label = "Horizontal position",
                            value = x,
                            min = 0f,
                            max = 360f,
                            onChange = { x = it; pushLiveGeometry() },
                            onChangeFinished = { saveGeometry() }
                        )
                    }
                    SegmentedCard(index = 1, count = 4) {
                        DpSlider(
                            label = "Vertical position",
                            value = y,
                            min = 0f,
                            max = 200f,
                            onChange = { y = it; pushLiveGeometry() },
                            onChangeFinished = { saveGeometry() }
                        )
                    }
                    SegmentedCard(index = 2, count = 4) {
                        DpSlider(
                            label = "Width",
                            value = w,
                            min = 24f,
                            max = 360f,
                            onChange = { w = it; pushLiveGeometry() },
                            onChangeFinished = { saveGeometry() }
                        )
                    }
                    SegmentedCard(index = 3, count = 4) {
                        DpSlider(
                            label = "Height",
                            value = h,
                            min = 16f,
                            max = 120f,
                            onChange = { h = it; pushLiveGeometry() },
                            onChangeFinished = { saveGeometry() }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "App to open on tap",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val blockedCount = prefs.blockedPackages.size
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegmentedCard(
                        index = 0,
                        count = 2,
                        onClick = { onOpenPicker() }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    targetLabel,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Tap to change target app",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                if (targetIcon != null) {
                                    Image(
                                        bitmap = targetIcon!!,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(40.dp))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    SegmentedCard(
                        index = 1,
                        count = 2,
                        onClick = { onOpenFilterPicker() }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (blockedCount == 0) "No apps blocked"
                                    else "$blockedCount app${if (blockedCount > 1) "s" else ""} blocked",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Block in apps (Filter list)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_block_red),
                                    contentDescription = "Block in apps",
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.Unspecified
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val isClockApp: Boolean
)

private fun getSearchRelevanceScore(app: AppEntry, query: String): Int {
    if (query.isEmpty()) return 0
    val label = app.label.lowercase()
    val pkg = app.packageName.lowercase()

    return when {
        label.startsWith(query) -> 100
        label.split(' ', '-').any { it.startsWith(query) } -> 80
        label.contains(query) -> 50
        pkg.contains(query) -> 20
        else -> 0
    }
}

@Composable
fun AppRow(
    app: AppEntry,
    index: Int,
    totalCount: Int,
    trailingContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(iconCache.get(app.packageName)) }

    if (icon == null) {
        LaunchedEffect(app.packageName) {
            icon = withContext(iconDispatcher) {
                runCatching {
                    context.packageManager
                        .getApplicationIcon(app.packageName)
                        .toBitmap(72, 72)
                        .asImageBitmap()
                }.getOrNull()?.also { iconCache.put(app.packageName, it) }
            }
        }
    }

    SegmentedCard(
        index = index,
        count = totalCount,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon!!,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (app.isClockApp) {
                    Text(
                        text = "Clock app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            trailingContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var apps by remember { mutableStateOf<List<AppEntry>>(cachedApps ?: emptyList()) }
    var selected by remember { mutableStateOf(prefs.targetPackage) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (cachedApps == null || cachedApps!!.isEmpty()) {
            batchLoadApps(context) { newBatch ->
                apps = newBatch
            }
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            apps
        } else {
            val terms = q.split(' ').filter { it.isNotEmpty() }
            apps.filter { app ->
                val label = app.label.lowercase()
                val pkg = app.packageName.lowercase()
                terms.all { term -> label.contains(term) || pkg.contains(term) }
            }.sortedWith(
                compareByDescending<AppEntry> { getSearchRelevanceScore(it, q) }
                    .thenByDescending { it.isClockApp }
                    .thenBy { it.label.lowercase() }
            )
        }
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
                title = { Text("Choose app") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                placeholder = { Text("Search apps...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
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

            if (filteredApps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (apps.isEmpty()) "Loading apps..." else "No matching apps",
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
                        val isSelected = selected == app.packageName
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "RadioScale"
                        )

                        Box(modifier = Modifier.animateItem()) {
                            AppRow(
                                app = app,
                                index = index,
                                totalCount = filteredApps.size,
                                onClick = {
                                    selected = app.packageName
                                    prefs.targetPackage = app.packageName
                                    prefs.targetLabel = app.label
                                    onBack()
                                },
                                trailingContent = {
                                    Icon(
                                        painter = painterResource(
                                            if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                                        ),
                                        contentDescription = if (isSelected) "Selected" else "Not selected",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer(scaleX = scale, scaleY = scale),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
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
fun FilterPickerScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var apps by remember { mutableStateOf<List<AppEntry>>(cachedApps ?: emptyList()) }
    var blockedSet by remember { mutableStateOf(prefs.blockedPackages) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (cachedApps == null || cachedApps!!.isEmpty()) {
            batchLoadApps(context) { newBatch ->
                apps = newBatch
            }
        }
    }

    val filteredApps = remember(apps, searchQuery, blockedSet) {
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
            compareByDescending<AppEntry> { blockedSet.contains(it.packageName) }
                .thenByDescending { if (q.isNotEmpty()) getSearchRelevanceScore(it, q) else 0 }
                .thenByDescending { it.isClockApp }
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
                title = { Text("Filter list (Block apps)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                placeholder = { Text("Search apps...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
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

            if (filteredApps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (apps.isEmpty()) "Loading apps..." else "No matching apps",
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
                        val isBlocked = blockedSet.contains(app.packageName)
                        val scale by animateFloatAsState(
                            targetValue = if (isBlocked) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "BlockScale"
                        )

                        Box(modifier = Modifier.animateItem()) {
                            AppRow(
                                app = app,
                                index = index,
                                totalCount = filteredApps.size,
                                onClick = {
                                    val newSet = blockedSet.toMutableSet()
                                    if (isBlocked) {
                                        newSet.remove(app.packageName)
                                    } else {
                                        newSet.add(app.packageName)
                                    }
                                    blockedSet = newSet
                                    prefs.blockedPackages = newSet
                                },
                                trailingContent = {
                                    if (isBlocked) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_block_red),
                                            contentDescription = "Blocked",
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer(scaleX = scale, scaleY = scale),
                                            tint = Color.Unspecified
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_radio_unselected),
                                            contentDescription = "Not blocked",
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

private suspend fun batchLoadApps(
    context: Context,
    onBatchLoaded: (List<AppEntry>) -> Unit
) = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val prefs = Prefs(context)

    val clockPackages = try {
        pm.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            .mapNotNull { it.activityInfo?.packageName }.toSet()
    } catch (_: Throwable) {
        emptySet<String>()
    }

    val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = try {
        pm.queryIntentActivities(mainIntent, 0)
    } catch (_: Throwable) {
        emptyList()
    }

    val rawList = activities.mapNotNull { ri ->
        val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
        if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
        val label = ri.loadLabel(pm).toString()
        AppEntry(pkg, label, clockPackages.contains(pkg))
    }
    .distinctBy { it.packageName }
    .sortedWith(
        compareByDescending<AppEntry> { it.isClockApp }
            .thenBy { it.label.lowercase() }
    )

    if (prefs.targetPackage == null && rawList.isNotEmpty()) {
        val defaultClock = rawList.firstOrNull { it.isClockApp } ?: rawList.first()
        prefs.targetPackage = defaultClock.packageName
        prefs.targetLabel = defaultClock.label
    }

    val initialBatchSize = DeviceCapability.initialBatchSize.coerceAtMost(rawList.size)
    val accumulatedApps = ArrayList<AppEntry>(rawList.size)
    accumulatedApps.addAll(rawList.take(initialBatchSize))

    accumulatedApps.forEach { app ->
        if (iconCache.get(app.packageName) == null) {
            runCatching {
                pm.getApplicationIcon(app.packageName)
                    .toBitmap(72, 72)
                    .asImageBitmap()
            }.getOrNull()?.let { iconCache.put(app.packageName, it) }
        }
    }

    withContext(Dispatchers.Main) {
        onBatchLoaded(ArrayList(accumulatedApps))
    }

    var currentIndex = initialBatchSize
    val chunkSize = DeviceCapability.chunkBatchSize

    while (currentIndex < rawList.size) {
        val nextIndex = (currentIndex + chunkSize).coerceAtMost(rawList.size)
        val chunk = rawList.subList(currentIndex, nextIndex)

        chunk.forEach { app ->
            if (iconCache.get(app.packageName) == null) {
                runCatching {
                    pm.getApplicationIcon(app.packageName)
                        .toBitmap(72, 72)
                        .asImageBitmap()
                }.getOrNull()?.let { iconCache.put(app.packageName, it) }
            }
        }

        accumulatedApps.addAll(chunk)
        currentIndex = nextIndex

        yield()

        val currentSnapshot = ArrayList(accumulatedApps)
        withContext(Dispatchers.Main) {
            onBatchLoaded(currentSnapshot)
        }
    }

    cachedApps = accumulatedApps
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}

@Composable
fun DpSlider(
    label: String,
    value: Int,
    min: Float,
    max: Float,
    onChange: (Int) -> Unit,
    onChangeFinished: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$value dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
