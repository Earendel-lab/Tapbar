package com.earendel.tapbar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private var cachedApps: List<AppEntry>? = null

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    private val themeMode = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        themeMode.intValue = prefs.themeMode
        setContent { AppRoot(prefs, themeMode) }
    }

    override fun onResume() {
        super.onResume()

        themeMode.intValue = prefs.themeMode
        TapZone.previewRequested = true
        if (!TapAccessibilityService.isEnabled(this) && Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
        TapZone.active?.setPreview(true)
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
        when (screen) {
            "picker" -> AppPickerScreen(prefs) { screen = "home" }
            else -> HomeScreen(
                prefs = prefs,
                onOpenPicker = { screen = "picker" }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: Prefs,
    onOpenPicker: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var a11yOn by remember { mutableStateOf(TapAccessibilityService.isEnabled(context)) }
    var enabled by remember { mutableStateOf(prefs.serviceEnabled) }
    var autoStart by remember { mutableStateOf(prefs.autoStart) }

    var x by remember { mutableIntStateOf(prefs.posX) }
    var y by remember { mutableIntStateOf(prefs.posY) }
    var w by remember { mutableIntStateOf(prefs.zoneWidth) }
    var h by remember { mutableIntStateOf(prefs.zoneHeight) }

    val targetLabel = prefs.targetLabel ?: "Not selected"
    val targetPackage = prefs.targetPackage

    var targetIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(targetPackage) {
        targetIcon = withContext(Dispatchers.IO) {
            targetPackage?.let { pkg ->
                runCatching {
                    context.packageManager
                        .getApplicationIcon(pkg)
                        .toBitmap(96, 96)
                        .asImageBitmap()
                }.getOrNull()
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

    LaunchedEffect(Unit) {
        while(true) {
            hasOverlay = Settings.canDrawOverlays(context)
            a11yOn = TapAccessibilityService.isEnabled(context)
            delay(1000)
        }
    }

    fun pushGeometry() {
        val live = TapZone.active
        if (live != null) {
            live.updateGeometry(x, y, w, h)
        } else {
            prefs.posX = x
            prefs.posY = y
            prefs.zoneWidth = w
            prefs.zoneHeight = h
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tapbar") },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (!a11yOn) {
                SectionCard("Accessibility Service") {
                    Text(
                        "Without the accessibility service, Android forces the zone underneath "
                            + "the status bar and the system takes those taps. Turn the service "
                            + "on to put the zone on the clock itself.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Text("Turn on accessibility service")
                    }
                }
            }

            if (!hasOverlay && !a11yOn) {
                SectionCard("Permission needed") {
                    Text(
                        "Tapbar needs the \"Display over other apps\" permission to place " +
                            "its tap zone on screen.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            )
                        )
                    }) {
                        Text("Grant permission")
                    }
                }
            }

            SectionCard("Tap zone") {
                SwitchRow("Enable tap zone", enabled) {
                    enabled = it
                    prefs.serviceEnabled = it
                    if (it && !a11yOn && Settings.canDrawOverlays(context)) OverlayService.start(context)
                    if (!it) OverlayService.stop(context)
                }
                SwitchRow("Start automatically on boot", autoStart) {
                    autoStart = it
                    prefs.autoStart = it
                }
            }

            SectionCard("Position and size") {
                DpSlider("Horizontal position", x, 0f, 360f) { x = it; pushGeometry() }
                DpSlider("Vertical position", y, 0f, 200f) { y = it; pushGeometry() }
                DpSlider("Width", w, 24f, 360f) { w = it; pushGeometry() }
                DpSlider("Height", h, 16f, 120f) { h = it; pushGeometry() }
            }

            SectionCard("App to open on tap") {
                ListItem(
                    headlineContent = { Text(targetLabel) },
                    supportingContent = { Text("Tap to change") },
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpenPicker() }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private val iconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

data class AppEntry(
    val packageName: String,
    val label: String,
    val isClockApp: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var apps by remember { mutableStateOf<List<AppEntry>>(cachedApps ?: emptyList()) }
    var selected by remember { mutableStateOf(prefs.targetPackage) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (cachedApps == null) {
            apps = loadApps(context)
            cachedApps = apps
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Choose app") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { pad ->
        if (apps.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { Text("Loading apps...") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
                items(apps, key = { it.packageName }) { app ->
                    var icon by remember { mutableStateOf<ImageBitmap?>(iconCache[app.packageName]) }
                    
                    if (icon == null) {
                        LaunchedEffect(app.packageName) {
                            icon = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.packageManager
                                        .getApplicationIcon(app.packageName)
                                        .toBitmap(96, 96)
                                        .asImageBitmap()
                                }.getOrNull()?.also { iconCache[app.packageName] = it }
                            }
                        }
                    }

                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = if (app.isClockApp) {
                            { Text("Clock app") }
                        } else null,
                        leadingContent = {
                            if (icon != null) {
                                Image(bitmap = icon!!, contentDescription = null,
                                    modifier = Modifier.size(40.dp))
                            } else {
                                Spacer(modifier = Modifier.size(40.dp))
                            }
                        },
                        trailingContent = {
                            RadioButton(
                                selected = selected == app.packageName,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        },
                        modifier = Modifier.clickable {
                            selected = app.packageName
                            prefs.targetPackage = app.packageName
                            prefs.targetLabel = app.label
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

private suspend fun loadApps(context: Context): List<AppEntry> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val clockPackages = try {
            pm.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (t: Throwable) {
            emptySet<String>()
        }

        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = try {
            pm.queryIntentActivities(main, 0)
        } catch (t: Throwable) {
            emptyList()
        }

        activities.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            val label = ri.loadLabel(pm).toString()
            AppEntry(pkg, label, clockPackages.contains(pkg))
        }
        .distinctBy { it.packageName }
        .sortedWith(
            compareByDescending<AppEntry> { it.isClockApp }
                .thenBy { it.label.lowercase() }
        )
    }

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
fun DpSlider(label: String, value: Int, min: Float, max: Float, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            Text("$value dp", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

