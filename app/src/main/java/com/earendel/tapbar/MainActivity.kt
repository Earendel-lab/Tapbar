@file:OptIn(ExperimentalCoroutinesApi::class)

package com.earendel.tapbar

import android.Manifest
import androidx.annotation.StringRes
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.DoNotDisturb
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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

fun invalidateAppCache(packageName: String? = null) {
    cachedApps = null
    if (packageName != null) iconCache.remove(packageName)
    AppShortcuts.invalidate()
}

private suspend fun validCachedApps(context: Context): List<AppEntry>? = withContext(Dispatchers.IO) {
    val cached = cachedApps ?: return@withContext null
    val pm = context.packageManager
    val valid = cached.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
    if (valid.size != cached.size) cachedApps = valid
    valid
}
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

data class ActionEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector
)

val quickSettingActions = listOf(
    ActionEntry("wifi", R.string.action_wifi, R.string.action_wifi_desc, Icons.Rounded.Wifi),
    ActionEntry("bluetooth", R.string.action_bluetooth, R.string.action_bluetooth_desc, Icons.Rounded.Bluetooth),
    ActionEntry("data", R.string.action_data, R.string.action_data_desc, Icons.Rounded.SignalCellularAlt),
    ActionEntry("flashlight", R.string.action_flashlight, R.string.action_flashlight_desc, Icons.Rounded.FlashOn),
    ActionEntry("auto_rotate", R.string.action_auto_rotate, R.string.action_auto_rotate_desc, Icons.Rounded.ScreenRotation),
    ActionEntry("dnd", R.string.action_dnd, R.string.action_dnd_desc, Icons.Rounded.DoNotDisturb),
    ActionEntry("volume", R.string.action_volume, R.string.action_volume_desc, Icons.Rounded.VolumeUp),
    ActionEntry("hotspot", R.string.action_hotspot, R.string.action_hotspot_desc, Icons.Rounded.WifiTethering),
    ActionEntry("battery_saver", R.string.action_battery_saver, R.string.action_battery_saver_desc, Icons.Rounded.BatterySaver),
    ActionEntry("battery_usage", R.string.action_battery_usage, R.string.action_battery_usage_desc, Icons.Rounded.BatteryChargingFull),
    ActionEntry("nfc", R.string.action_nfc, R.string.action_nfc_desc, Icons.Rounded.Nfc),
    ActionEntry("night_light", R.string.action_night_light, R.string.action_night_light_desc, Icons.Rounded.NightsStay),
    ActionEntry("airplane", R.string.action_airplane, R.string.action_airplane_desc, Icons.Rounded.AirplanemodeActive),
    ActionEntry("ringer_mode", R.string.action_ringer_mode, R.string.action_ringer_mode_desc, Icons.Rounded.NotificationsActive)
)

val mediaActions = listOf(
    ActionEntry("volume_up", R.string.action_volume_up, R.string.action_volume_up_desc, Icons.Rounded.VolumeUp),
    ActionEntry("volume_down", R.string.action_volume_down, R.string.action_volume_down_desc, Icons.Rounded.VolumeDown),
    ActionEntry("mute", R.string.action_mute, R.string.action_mute_desc, Icons.Rounded.VolumeOff),
    ActionEntry("media_play_pause", R.string.action_media_play_pause, R.string.action_media_play_pause_desc, Icons.Rounded.PlayArrow),
    ActionEntry("media_next", R.string.action_media_next, R.string.action_media_next_desc, Icons.Rounded.SkipNext),
    ActionEntry("media_previous", R.string.action_media_previous, R.string.action_media_previous_desc, Icons.Rounded.SkipPrevious)
)

val displayActions = listOf(
    ActionEntry("auto_brightness", R.string.action_auto_brightness, R.string.action_auto_brightness_desc, Icons.Rounded.BrightnessAuto),
    ActionEntry("brightness_up", R.string.action_brightness_up, R.string.action_brightness_up_desc, Icons.Rounded.Brightness7),
    ActionEntry("brightness_down", R.string.action_brightness_down, R.string.action_brightness_down_desc, Icons.Rounded.Brightness4),
    ActionEntry("wallpaper", R.string.action_wallpaper, R.string.action_wallpaper_desc, Icons.Rounded.Wallpaper)
)

val navigationActions = listOf(
    ActionEntry("lock_screen", R.string.action_lock_screen, R.string.action_lock_screen_desc, Icons.Rounded.Lock),
    ActionEntry("screenshot", R.string.action_screenshot, R.string.action_screenshot_desc, Icons.Rounded.Camera),
    ActionEntry("power_menu", R.string.action_power_menu, R.string.action_power_menu_desc, Icons.Rounded.PowerSettingsNew),
    ActionEntry("back", R.string.action_back, R.string.action_back_desc, Icons.AutoMirrored.Rounded.ArrowBack),
    ActionEntry("home", R.string.action_home, R.string.action_home_desc, Icons.Rounded.Home),
    ActionEntry("recents", R.string.action_recents, R.string.action_recents_desc, Icons.Rounded.Menu),
    ActionEntry("notifications", R.string.action_notifications, R.string.action_notifications_desc, Icons.Rounded.Notifications),
    ActionEntry("quick_settings", R.string.action_quick_settings, R.string.action_quick_settings_desc, Icons.Rounded.Notifications),
    ActionEntry("split_screen", R.string.action_split_screen, R.string.action_split_screen_desc, Icons.Rounded.VerticalSplit),
    ActionEntry("dismiss_shade", R.string.action_dismiss_shade, R.string.action_dismiss_shade_desc, Icons.Rounded.ExpandLess),
    ActionEntry("all_apps", R.string.action_all_apps, R.string.action_all_apps_desc, Icons.Rounded.Apps),
    ActionEntry("accessibility_button", R.string.action_accessibility_button, R.string.action_accessibility_button_desc, Icons.Rounded.Accessibility),
    ActionEntry("accessibility_shortcut", R.string.action_accessibility_shortcut, R.string.action_accessibility_shortcut_desc, Icons.Rounded.AccessibilityNew),
    ActionEntry("headset_hook", R.string.action_headset_hook, R.string.action_headset_hook_desc, Icons.Rounded.Headset),
    ActionEntry("assistant", R.string.action_assistant, R.string.action_assistant_desc, Icons.Rounded.Mic),
    ActionEntry("camera", R.string.action_camera, R.string.action_camera_desc, Icons.Rounded.PhotoCamera)
)

val settingsActions = listOf(
    ActionEntry("display_settings", R.string.action_display_settings, R.string.action_display_settings_desc, Icons.Rounded.DisplaySettings),
    ActionEntry("accessibility_settings", R.string.action_accessibility_settings, R.string.action_accessibility_settings_desc, Icons.Rounded.SettingsAccessibility),
    ActionEntry("default_apps", R.string.action_default_apps, R.string.action_default_apps_desc, Icons.Rounded.AppShortcut),
    ActionEntry("input_method", R.string.action_input_method, R.string.action_input_method_desc, Icons.Rounded.Keyboard),
    ActionEntry("cast", R.string.action_cast, R.string.action_cast_desc, Icons.Rounded.Cast),
    ActionEntry("vpn_settings", R.string.action_vpn_settings, R.string.action_vpn_settings_desc, Icons.Rounded.VpnKey),
    ActionEntry("add_account", R.string.action_add_account, R.string.action_add_account_desc, Icons.Rounded.PersonAdd),
    ActionEntry("battery_optimization", R.string.action_battery_optimization, R.string.action_battery_optimization_desc, Icons.Rounded.BatteryAlert),
    ActionEntry("sound_settings", R.string.action_sound_settings, R.string.action_sound_settings_desc, Icons.Rounded.Vibration),
    ActionEntry("location_settings", R.string.action_location_settings, R.string.action_location_settings_desc, Icons.Rounded.LocationOn),
    ActionEntry("data_usage", R.string.action_data_usage, R.string.action_data_usage_desc, Icons.Rounded.DataUsage),
    ActionEntry("wifi_settings", R.string.action_wifi_settings, R.string.action_wifi_settings_desc, Icons.Rounded.Router),
    ActionEntry("sim_settings", R.string.action_sim_settings, R.string.action_sim_settings_desc, Icons.Rounded.SimCard),
    ActionEntry("mobile_network", R.string.action_mobile_network, R.string.action_mobile_network_desc, Icons.Rounded.SignalCellularAlt),
    ActionEntry("network_settings", R.string.action_network_settings, R.string.action_network_settings_desc, Icons.Rounded.Public),
    ActionEntry("nfc_payment", R.string.action_nfc_payment, R.string.action_nfc_payment_desc, Icons.Rounded.Payment),
    ActionEntry("screen_saver", R.string.action_screen_saver, R.string.action_screen_saver_desc, Icons.Rounded.Tv),
    ActionEntry("captioning_settings", R.string.action_captioning_settings, R.string.action_captioning_settings_desc, Icons.Rounded.ClosedCaption),
    ActionEntry("print_settings", R.string.action_print_settings, R.string.action_print_settings_desc, Icons.Rounded.Print),
    ActionEntry("user_dictionary", R.string.action_user_dictionary, R.string.action_user_dictionary_desc, Icons.Rounded.Book),
    ActionEntry("open_settings", R.string.action_open_settings, R.string.action_open_settings_desc, Icons.Rounded.SettingsApplications)
)

val advancedSettingsActions = listOf(
    ActionEntry("date_time_settings", R.string.action_date_time_settings, R.string.action_date_time_settings_desc, Icons.Rounded.Schedule),
    ActionEntry("locale_settings", R.string.action_locale_settings, R.string.action_locale_settings_desc, Icons.Rounded.Language),
    ActionEntry("app_info_list", R.string.action_app_info_list, R.string.action_app_info_list_desc, Icons.Rounded.AppSettingsAlt),
    ActionEntry("developer_options", R.string.action_developer_options, R.string.action_developer_options_desc, Icons.Rounded.Code),
    ActionEntry("security_settings", R.string.action_security_settings, R.string.action_security_settings_desc, Icons.Rounded.Security),
    ActionEntry("sync_settings", R.string.action_sync_settings, R.string.action_sync_settings_desc, Icons.Rounded.Sync),
    ActionEntry("voice_input_settings", R.string.action_voice_input_settings, R.string.action_voice_input_settings_desc, Icons.Rounded.RecordVoiceOver),
    ActionEntry("about_phone", R.string.action_about_phone, R.string.action_about_phone_desc, Icons.Rounded.Info),
    ActionEntry("default_home", R.string.action_default_home, R.string.action_default_home_desc, Icons.Rounded.HomeWork),
    ActionEntry("storage_settings", R.string.action_storage_settings, R.string.action_storage_settings_desc, Icons.Rounded.Storage),
    ActionEntry("privacy_dashboard", R.string.action_privacy_dashboard, R.string.action_privacy_dashboard_desc, Icons.Rounded.PrivacyTip),
    ActionEntry("usage_access", R.string.action_usage_access, R.string.action_usage_access_desc, Icons.Rounded.QueryStats),
    ActionEntry("notification_access", R.string.action_notification_access, R.string.action_notification_access_desc, Icons.Rounded.Notifications),
    ActionEntry("dnd_access", R.string.action_dnd_access, R.string.action_dnd_access_desc, Icons.Rounded.DoNotDisturbOn),
    ActionEntry("overlay_permission", R.string.action_overlay_permission, R.string.action_overlay_permission_desc, Icons.Rounded.Layers),
    ActionEntry("write_settings", R.string.action_write_settings, R.string.action_write_settings_desc, Icons.Rounded.Tune)
)

val systemActions = quickSettingActions + mediaActions + displayActions + navigationActions + settingsActions + advancedSettingsActions

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private val themeMode = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        LocaleHelper.syncFromSystem(this, prefs)
        LabelRefresher.refreshBasic(this, prefs)
        val onboardingDone = prefs.hasCompletedLanguageOnboarding
        themeMode.intValue = prefs.themeMode
        setContent {
            ProvideHapticManager(prefs) {
                AppRoot(prefs, themeMode, onboardingDone)
            }
        }

        val appCtx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            if (cachedApps == null) {
                batchLoadApps(appCtx) { _ -> }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (LocaleHelper.needsRecreate(this)) {
            recreate()
            return
        }
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
fun AppRoot(prefs: Prefs, themeMode: MutableIntState, initialOnboardingDone: Boolean = true) {
    var onboardingDone by rememberSaveable { mutableStateOf(initialOnboardingDone) }
    var screen by remember { mutableStateOf("home") }
    var activeGestureMode by remember { mutableIntStateOf(0) }
    var activeZone by rememberSaveable { mutableIntStateOf(0) }

    TapbarTheme(themeMode = themeMode.intValue) {
        if (!onboardingDone) {
            LanguageOnboardingScreen(prefs) { onboardingDone = true }
            return@TapbarTheme
        }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == "picker" || targetState == "filter_picker" || targetState == "action_picker" || targetState == "shortcut_picker") {
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
                "picker" -> AppPickerScreen(prefs, gestureMode = activeGestureMode, zone = activeZone) { screen = "home" }
                "filter_picker" -> FilterPickerScreen(prefs) { screen = "home" }
                "action_picker" -> ActionPickerScreen(prefs, gestureMode = activeGestureMode, zone = activeZone) { screen = "home" }
                "shortcut_picker" -> ShortcutPickerScreen(prefs, gestureMode = activeGestureMode, zone = activeZone) { screen = "home" }
                else -> HomeScreen(
                    prefs = prefs,
                    initialZone = activeZone,
                    onZoneChange = { activeZone = it },
                    onSettingsReset = { themeMode.intValue = prefs.themeMode },
                    onOpenPicker = { mode ->
                        activeGestureMode = mode
                        screen = "picker"
                    },
                    onOpenActionPicker = { mode ->
                        activeGestureMode = mode
                        screen = "action_picker"
                    },
                    onOpenShortcutPicker = { mode ->
                        activeGestureMode = mode
                        screen = "shortcut_picker"
                    },
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
    initialZone: Int = 0,
    onZoneChange: (Int) -> Unit = {},
    onSettingsReset: () -> Unit = {},
    onOpenPicker: (gestureMode: Int) -> Unit,
    onOpenActionPicker: (gestureMode: Int) -> Unit,
    onOpenShortcutPicker: (gestureMode: Int) -> Unit,
    onOpenFilterPicker: () -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedZone by remember { mutableIntStateOf(initialZone) }
    var zoneEnabledStates by remember { mutableStateOf(List(ZONE_COUNT) { prefs.isZoneEnabled(it) }) }

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var a11yOn by remember { mutableStateOf(TapAccessibilityService.isEnabled(context)) }
    var enabled by remember { mutableStateOf(prefs.serviceEnabled) }
    var disableInLandscape by remember { mutableStateOf(prefs.disableInLandscape) }
    var autoStart by remember { mutableStateOf(prefs.autoStart) }

    var singleTapEnabled by remember { mutableStateOf(prefs.getSingleTapEnabled(selectedZone)) }
    var singleTapType by remember { mutableIntStateOf(prefs.getSingleTapType(selectedZone)) }
    var singleTapPkg by remember { mutableStateOf(prefs.getSingleTapTargetPkg(selectedZone, context)) }
    var singleTapLabel by remember {
        mutableStateOf(
            when (singleTapType) {
                1 -> prefs.getSingleTapActionLabel(selectedZone)
                2 -> prefs.getSingleTapShortcutDisplay(selectedZone, context)
                else -> prefs.getSingleTapTargetLabel(selectedZone, context)
            }
        )
    }
    var singleTapActionId by remember { mutableStateOf(prefs.getSingleTapActionId(selectedZone)) }

    var doubleTapEnabled by remember { mutableStateOf(prefs.getDoubleTapEnabled(selectedZone)) }
    var doubleTapType by remember { mutableIntStateOf(prefs.getDoubleTapType(selectedZone)) }
    var doubleTapPkg by remember { mutableStateOf(prefs.getDoubleTapTargetPkg(selectedZone)) }
    var doubleTapLabel by remember {
        mutableStateOf(
            when (doubleTapType) {
                1 -> prefs.getDoubleTapActionLabel(selectedZone)
                2 -> prefs.getDoubleTapShortcutDisplay(selectedZone)
                else -> prefs.getDoubleTapTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
            }
        )
    }
    var doubleTapActionId by remember { mutableStateOf(prefs.getDoubleTapActionId(selectedZone)) }

    var tripleTapEnabled by remember { mutableStateOf(prefs.getTripleTapEnabled(selectedZone)) }
    var tripleTapType by remember { mutableIntStateOf(prefs.getTripleTapType(selectedZone)) }
    var tripleTapPkg by remember { mutableStateOf(prefs.getTripleTapTargetPkg(selectedZone)) }
    var tripleTapLabel by remember {
        mutableStateOf(
            when (tripleTapType) {
                1 -> prefs.getTripleTapActionLabel(selectedZone)
                2 -> prefs.getTripleTapShortcutDisplay(selectedZone)
                else -> prefs.getTripleTapTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
            }
        )
    }
    var tripleTapActionId by remember { mutableStateOf(prefs.getTripleTapActionId(selectedZone)) }

    var longPressEnabled by remember { mutableStateOf(prefs.getLongPressEnabled(selectedZone)) }
    var longPressType by remember { mutableIntStateOf(prefs.getLongPressType(selectedZone)) }
    var longPressPkg by remember { mutableStateOf(prefs.getLongPressTargetPkg(selectedZone)) }
    var longPressLabel by remember {
        mutableStateOf(
            when (longPressType) {
                1 -> prefs.getLongPressActionLabel(selectedZone)
                2 -> prefs.getLongPressShortcutDisplay(selectedZone)
                else -> prefs.getLongPressTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
            }
        )
    }
    var longPressActionId by remember { mutableStateOf(prefs.getLongPressActionId(selectedZone)) }

    var swipeEnabled by remember { mutableStateOf(prefs.getSwipeEnabled(selectedZone)) }
    var swipeTarget by remember { mutableIntStateOf(prefs.getSwipeTarget(selectedZone)) }

    var tapSpeedMs by remember { mutableIntStateOf(prefs.tapSpeedMs) }
    var blockedCount by remember { mutableIntStateOf(prefs.blockedPackages.size) }

    var x by remember { mutableIntStateOf(prefs.getPosX(selectedZone)) }
    var y by remember { mutableIntStateOf(prefs.getPosY(selectedZone)) }
    var w by remember { mutableIntStateOf(prefs.getZoneWidth(selectedZone)) }
    var h by remember { mutableIntStateOf(prefs.getZoneHeight(selectedZone)) }

    var singleTapIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var doubleTapIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var tripleTapIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var longPressIcon by remember { mutableStateOf<ImageBitmap?>(null) }

    var showGestureSheetFor by remember { mutableStateOf<Int?>(null) }

    fun refreshZoneState() {
        x = prefs.getPosX(selectedZone)
        y = prefs.getPosY(selectedZone)
        w = prefs.getZoneWidth(selectedZone)
        h = prefs.getZoneHeight(selectedZone)

        singleTapType = prefs.getSingleTapType(selectedZone)
        singleTapPkg = prefs.getSingleTapTargetPkg(selectedZone, context)
        singleTapLabel = when (singleTapType) {
            1 -> prefs.getSingleTapActionLabel(selectedZone)
            2 -> prefs.getSingleTapShortcutDisplay(selectedZone, context)
            else -> prefs.getSingleTapTargetLabel(selectedZone, context)
        }
        singleTapActionId = prefs.getSingleTapActionId(selectedZone)

        doubleTapEnabled = prefs.getDoubleTapEnabled(selectedZone)
        doubleTapType = prefs.getDoubleTapType(selectedZone)
        doubleTapPkg = prefs.getDoubleTapTargetPkg(selectedZone)
        doubleTapLabel = when (doubleTapType) {
            1 -> prefs.getDoubleTapActionLabel(selectedZone)
            2 -> prefs.getDoubleTapShortcutDisplay(selectedZone)
            else -> prefs.getDoubleTapTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
        }
        doubleTapActionId = prefs.getDoubleTapActionId(selectedZone)

        tripleTapEnabled = prefs.getTripleTapEnabled(selectedZone)
        tripleTapType = prefs.getTripleTapType(selectedZone)
        tripleTapPkg = prefs.getTripleTapTargetPkg(selectedZone)
        tripleTapLabel = when (tripleTapType) {
            1 -> prefs.getTripleTapActionLabel(selectedZone)
            2 -> prefs.getTripleTapShortcutDisplay(selectedZone)
            else -> prefs.getTripleTapTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
        }
        tripleTapActionId = prefs.getTripleTapActionId(selectedZone)

        longPressEnabled = prefs.getLongPressEnabled(selectedZone)
        longPressType = prefs.getLongPressType(selectedZone)
        longPressPkg = prefs.getLongPressTargetPkg(selectedZone)
        longPressLabel = when (longPressType) {
            1 -> prefs.getLongPressActionLabel(selectedZone)
            2 -> prefs.getLongPressShortcutDisplay(selectedZone)
            else -> prefs.getLongPressTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
        }
        longPressActionId = prefs.getLongPressActionId(selectedZone)

        swipeEnabled = prefs.getSwipeEnabled(selectedZone)
        swipeTarget = prefs.getSwipeTarget(selectedZone)
    }

    LaunchedEffect(Unit) {
        val changed = withContext(Dispatchers.IO) { LabelRefresher.refreshShortcuts(context, prefs) }
        if (changed) refreshZoneState()
    }

    LaunchedEffect(selectedZone) {
        onZoneChange(selectedZone)
        refreshZoneState()
        TapZone.active?.setSelectedZoneForPreview(selectedZone)
    }

    val orientationKey = LocalConfiguration.current.orientation
    LaunchedEffect(orientationKey) {
        refreshZoneState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = TapAccessibilityService.isEnabled(context)
                hasOverlay = Settings.canDrawOverlays(context)
                enabled = prefs.serviceEnabled
                zoneEnabledStates = List(ZONE_COUNT) { prefs.isZoneEnabled(it) }
                tapSpeedMs = prefs.tapSpeedMs
                blockedCount = prefs.blockedPackages.size
                refreshZoneState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(singleTapPkg) {
        val pkg = singleTapPkg
        if (pkg != null) {
            singleTapIcon = withContext(iconDispatcher) {
                iconCache.get(pkg) ?: runCatching {
                    context.packageManager.getApplicationIcon(pkg).toBitmap(80, 80).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(pkg, it) }
            }
        } else {
            singleTapIcon = null
        }
    }

    LaunchedEffect(doubleTapPkg) {
        val pkg = doubleTapPkg
        if (pkg != null) {
            doubleTapIcon = withContext(iconDispatcher) {
                iconCache.get(pkg) ?: runCatching {
                    context.packageManager.getApplicationIcon(pkg).toBitmap(80, 80).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(pkg, it) }
            }
        } else {
            doubleTapIcon = null
        }
    }

    LaunchedEffect(tripleTapPkg) {
        val pkg = tripleTapPkg
        if (pkg != null) {
            tripleTapIcon = withContext(iconDispatcher) {
                iconCache.get(pkg) ?: runCatching {
                    context.packageManager.getApplicationIcon(pkg).toBitmap(80, 80).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(pkg, it) }
            }
        } else {
            tripleTapIcon = null
        }
    }

    LaunchedEffect(longPressPkg) {
        val pkg = longPressPkg
        if (pkg != null) {
            longPressIcon = withContext(iconDispatcher) {
                iconCache.get(pkg) ?: runCatching {
                    context.packageManager.getApplicationIcon(pkg).toBitmap(80, 80).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(pkg, it) }
            }
        } else {
            longPressIcon = null
        }
    }

    val singleTapAction = systemActions.firstOrNull { it.id == singleTapActionId } ?: systemActions[0]
    val doubleTapAction = systemActions.firstOrNull { it.id == doubleTapActionId } ?: systemActions[0]
    val tripleTapAction = systemActions.firstOrNull { it.id == tripleTapActionId } ?: systemActions[0]
    val longPressAction = systemActions.firstOrNull { it.id == longPressActionId } ?: systemActions[0]

    val cutoutLeft  = WindowInsets.displayCutout.getLeft(density, layoutDir)
    val cutoutRight = WindowInsets.displayCutout.getRight(density, layoutDir)

    LaunchedEffect(Unit) {
        ZoneOverlapResolver.enforceNonOverlapOnLoad(prefs)
        if (!prefs.hasSetInitialPosition) {
            val leftDp  = (cutoutLeft  / density.density).toInt()
            val rightDp = (cutoutRight / density.density).toInt()

            val detectedX = when {
                leftDp > 16 -> leftDp + 8
                rightDp > 16 -> 12
                else -> 12
            }
            x = detectedX
            prefs.setPosX(0, detectedX)
            prefs.hasSetInitialPosition = true

            TapZone.active?.updateGeometry(0, x, y, w, h)
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
        TapZone.active?.updateGeometryLive(selectedZone, x, y, w, h)
    }

    fun saveGeometry() {
        prefs.setPosX(selectedZone, x)
        prefs.setPosY(selectedZone, y)
        prefs.setZoneWidth(selectedZone, w)
        prefs.setZoneHeight(selectedZone, h)
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isZoneControlsEnabled = zoneEnabledStates.getOrElse(selectedZone) { true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = {
                        haptics.performLightTap(view)
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.settings),
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
                .verticalScroll(rememberScrollState())
                .padding(
                    bottom = navBarPadding + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (!a11yOn) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.accessibility_service_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SegmentedCard(index = 0, count = 1) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.accessibility_service_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    haptics.performConfirm(view)
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
                                Text(stringResource(R.string.turn_on_accessibility))
                            }
                        }
                    }
                }
            }

            if (!hasOverlay && !a11yOn) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.permission_needed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SegmentedCard(index = 0, count = 1) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.permission_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    haptics.performConfirm(view)
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
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.service_behavior),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegmentedCard(index = 0, count = 3) {
                        SwitchRow(
                            label = stringResource(R.string.enable_tap_zone),
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
                    SegmentedCard(index = 1, count = 3) {
                        SwitchRow(
                            label = stringResource(R.string.disable_in_landscape),
                            checked = disableInLandscape,
                            onChange = {
                                disableInLandscape = it
                                prefs.disableInLandscape = it
                                TapZone.active?.attach()
                            }
                        )
                    }
                    SegmentedCard(index = 2, count = 3) {
                        SwitchRow(
                            label = stringResource(R.string.start_on_boot),
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
                    stringResource(R.string.tap_zones),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (rowStart in 0 until ZONE_COUNT step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (zoneIndex in rowStart until minOf(rowStart + 2, ZONE_COUNT)) {
                                ZoneSelectCard(
                                    zone = zoneIndex,
                                    selected = selectedZone == zoneIndex,
                                    zoneOn = zoneEnabledStates[zoneIndex],
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        haptics.performSelection(view)
                                        selectedZone = zoneIndex
                                    }
                                )
                            }
                        }
                    }

                    ZoneToggleCard(
                        zone = selectedZone,
                        zoneOn = zoneEnabledStates[selectedZone],
                        onChange = { enabledState ->
                            prefs.setZoneEnabled(selectedZone, enabledState)
                            zoneEnabledStates = List(ZONE_COUNT) { prefs.isZoneEnabled(it) }
                            TapZone.active?.attach()
                        }
                    )
                }
            }

            val gestureAlpha by animateFloatAsState(
                targetValue = if (isZoneControlsEnabled) 1.0f else 0.5f,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "gestureAlpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = gestureAlpha },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.gesture_actions),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GestureSquareCard(
                                title = stringResource(R.string.single_tap),
                                subtitle = if (singleTapEnabled) (singleTapLabel ?: stringResource(R.string.not_set)) else stringResource(R.string.disabled),
                                isAppType = (singleTapType == 0 || singleTapType == 2),
                                appIcon = singleTapIcon,
                                actionIcon = singleTapAction.icon,
                                isEnabled = isZoneControlsEnabled && singleTapEnabled,
                                clickable = isZoneControlsEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isZoneControlsEnabled) {
                                        haptics.performLightTap(view)
                                        showGestureSheetFor = 0
                                    }
                                }
                            )
                            GestureSquareCard(
                                title = stringResource(R.string.double_tap),
                                subtitle = if (doubleTapEnabled) (doubleTapLabel ?: stringResource(R.string.not_set)) else stringResource(R.string.disabled),
                                isAppType = (doubleTapType == 0 || doubleTapType == 2),
                                appIcon = doubleTapIcon,
                                actionIcon = doubleTapAction.icon,
                                isEnabled = isZoneControlsEnabled && doubleTapEnabled,
                                clickable = isZoneControlsEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isZoneControlsEnabled) {
                                        haptics.performLightTap(view)
                                        showGestureSheetFor = 1
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GestureSquareCard(
                                title = stringResource(R.string.triple_tap),
                                subtitle = if (tripleTapEnabled) (tripleTapLabel ?: stringResource(R.string.not_set)) else stringResource(R.string.disabled),
                                isAppType = (tripleTapType == 0 || tripleTapType == 2),
                                appIcon = tripleTapIcon,
                                actionIcon = tripleTapAction.icon,
                                isEnabled = isZoneControlsEnabled && tripleTapEnabled,
                                clickable = isZoneControlsEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isZoneControlsEnabled) {
                                        haptics.performLightTap(view)
                                        showGestureSheetFor = 2
                                    }
                                }
                            )
                            GestureSquareCard(
                                title = stringResource(R.string.long_press),
                                subtitle = if (longPressEnabled) (longPressLabel ?: stringResource(R.string.not_set)) else stringResource(R.string.disabled),
                                isAppType = (longPressType == 0 || longPressType == 2),
                                appIcon = longPressIcon,
                                actionIcon = longPressAction.icon,
                                isEnabled = isZoneControlsEnabled && longPressEnabled,
                                clickable = isZoneControlsEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isZoneControlsEnabled) {
                                        haptics.performLightTap(view)
                                        showGestureSheetFor = 3
                                    }
                                }
                            )
                        }

                        SwipeGestureCard(
                            enabled = swipeEnabled,
                            target = swipeTarget,
                            isEnabledControls = isZoneControlsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (isZoneControlsEnabled) {
                                    haptics.performLightTap(view)
                                    showGestureSheetFor = 4
                                }
                            }
                        )
                    }

                    if (doubleTapEnabled || tripleTapEnabled) {
                        Spacer(Modifier.height(4.dp))
                        SegmentedCard(index = 0, count = 1) {
                            DpSlider(
                                label = stringResource(R.string.tap_speed),
                                value = tapSpeedMs,
                                min = 150f,
                                max = 500f,
                                stepInterval = 25f,
                                enabled = isZoneControlsEnabled,
                                onChange = {
                                    if (isZoneControlsEnabled) {
                                        tapSpeedMs = it
                                        prefs.tapSpeedMs = it
                                    }
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.position_and_size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SegmentedCard(index = 0, count = 4) {
                            DpSlider(
                                label = stringResource(R.string.horizontal_position),
                                value = x,
                                min = 0f,
                                max = prefs.screenWidthDp().toFloat(),
                                stepInterval = 20f,
                                enabled = isZoneControlsEnabled,
                                onChange = {
                                    if (isZoneControlsEnabled) {
                                        val cx = ZoneOverlapResolver.constrainX(selectedZone, it, prefs)
                                        x = cx
                                        pushLiveGeometry()
                                    }
                                },
                                onChangeFinished = { if (isZoneControlsEnabled) saveGeometry() }
                            )
                        }
                        SegmentedCard(index = 1, count = 4) {
                            DpSlider(
                                label = stringResource(R.string.vertical_position),
                                value = y,
                                min = 0f,
                                max = prefs.screenHeightDp().toFloat(),
                                stepInterval = 20f,
                                enabled = isZoneControlsEnabled,
                                onChange = {
                                    if (isZoneControlsEnabled) {
                                        val cy = ZoneOverlapResolver.constrainY(selectedZone, it, prefs)
                                        y = cy
                                        pushLiveGeometry()
                                    }
                                },
                                onChangeFinished = { if (isZoneControlsEnabled) saveGeometry() }
                            )
                        }
                        SegmentedCard(index = 2, count = 4) {
                            DpSlider(
                                label = stringResource(R.string.width),
                                value = w,
                                min = 10f,
                                max = prefs.screenWidthDp().toFloat(),
                                stepInterval = 20f,
                                enabled = isZoneControlsEnabled,
                                onChange = {
                                    if (isZoneControlsEnabled) {
                                        val cw = ZoneOverlapResolver.constrainWidth(selectedZone, it, prefs)
                                        w = cw
                                        pushLiveGeometry()
                                    }
                                },
                                onChangeFinished = { if (isZoneControlsEnabled) saveGeometry() }
                            )
                        }
                        SegmentedCard(index = 3, count = 4) {
                            DpSlider(
                                label = stringResource(R.string.height),
                                value = h,
                                min = 16f,
                                max = prefs.screenHeightDp().toFloat(),
                                stepInterval = 10f,
                                enabled = isZoneControlsEnabled,
                                onChange = {
                                    if (isZoneControlsEnabled) {
                                        val ch = ZoneOverlapResolver.constrainHeight(selectedZone, it, prefs)
                                        h = ch
                                        pushLiveGeometry()
                                    }
                                },
                                onChangeFinished = { if (isZoneControlsEnabled) saveGeometry() }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.app_filter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SegmentedCard(
                    index = 0,
                    count = 1,
                    onClick = {
                        haptics.performLightTap(view)
                        onOpenFilterPicker()
                    }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                if (blockedCount == 0) stringResource(R.string.no_apps_blocked)
                                else context.resources.getQuantityString(R.plurals.apps_blocked, blockedCount, blockedCount),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.block_in_apps_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_block_red),
                                contentDescription = stringResource(R.string.cd_block_in_apps),
                                modifier = Modifier.size(32.dp),
                                tint = Color.Unspecified
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            val resetDoneText = stringResource(R.string.settings_reset_done)
            SlideToReset(
                onReset = {
                    prefs.resetToDefaults()

                    val leftDp = (cutoutLeft / density.density).toInt()
                    val rightDp = (cutoutRight / density.density).toInt()
                    val defaultX = when {
                        leftDp > 16 -> leftDp + 8
                        rightDp > 16 -> 12
                        else -> 12
                    }
                    prefs.setPosX(0, defaultX)
                    prefs.hasSetInitialPosition = true

                    selectedZone = 0
                    zoneEnabledStates = List(ZONE_COUNT) { prefs.isZoneEnabled(it) }
                    disableInLandscape = prefs.disableInLandscape
                    autoStart = prefs.autoStart
                    tapSpeedMs = prefs.tapSpeedMs
                    blockedCount = prefs.blockedPackages.size
                    refreshZoneState()

                    TapZone.active?.attach()
                    TapZone.active?.setSelectedZoneForPreview(0)

                    onSettingsReset()
                    Toast.makeText(context, resetDoneText, Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showGestureSheetFor != null) {
        val gestureMode = showGestureSheetFor!!
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showGestureSheetFor = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val sheetTitle = when (gestureMode) {
                    1 -> stringResource(R.string.double_tap_mode)
                    2 -> stringResource(R.string.triple_tap_mode)
                    3 -> stringResource(R.string.long_press_mode)
                    4 -> stringResource(R.string.swipe_gesture)
                    else -> stringResource(R.string.single_tap_mode)
                }

                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (gestureMode == 0) {
                    SwitchRow(
                        label = stringResource(R.string.enable_single_tap),
                        checked = singleTapEnabled,
                        onChange = {
                            singleTapEnabled = it
                            prefs.setSingleTapEnabled(selectedZone, it)
                        }
                    )
                } else if (gestureMode == 1) {
                    SwitchRow(
                        label = stringResource(R.string.enable_double_tap),
                        checked = doubleTapEnabled,
                        onChange = {
                            doubleTapEnabled = it
                            prefs.setDoubleTapEnabled(selectedZone, it)
                        }
                    )
                } else if (gestureMode == 2) {
                    SwitchRow(
                        label = stringResource(R.string.enable_triple_tap),
                        checked = tripleTapEnabled,
                        onChange = {
                            tripleTapEnabled = it
                            prefs.setTripleTapEnabled(selectedZone, it)
                        }
                    )
                } else if (gestureMode == 3) {
                    SwitchRow(
                        label = stringResource(R.string.enable_long_press),
                        checked = longPressEnabled,
                        onChange = {
                            longPressEnabled = it
                            prefs.setLongPressEnabled(selectedZone, it)
                        }
                    )
                } else if (gestureMode == 4) {
                    SwitchRow(
                        label = stringResource(R.string.enable_swipe),
                        checked = swipeEnabled,
                        onChange = {
                            swipeEnabled = it
                            prefs.setSwipeEnabled(selectedZone, it)
                        }
                    )
                }

                if (gestureMode == 4) {
                    SwipeOptionsList(
                        target = swipeTarget,
                        axis = SwipeAxisResolver.resolve(
                            x, y, w, h,
                            prefs.screenWidthDp(),
                            prefs.screenHeightDp(),
                            ZoneLayout.EDGE_DP
                        ),
                        onSelect = { picked ->
                            swipeTarget = picked
                            prefs.setSwipeTarget(selectedZone, picked)
                        }
                    )
                } else {
                val currentType = when (gestureMode) {
                    1 -> doubleTapType
                    2 -> tripleTapType
                    3 -> longPressType
                    else -> singleTapType
                }

                val radioScale0 by animateFloatAsState(
                    targetValue = if (currentType == 0) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "RadioScale0"
                )
                val radioScale1 by animateFloatAsState(
                    targetValue = if (currentType == 1) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "RadioScale1"
                )
                val radioScale2 by animateFloatAsState(
                    targetValue = if (currentType == 2) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "RadioScale2"
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegmentedCard(
                        index = 0,
                        count = 3,
                        onClick = {
                            haptics.performSelection(view)
                            when (gestureMode) {
                                1 -> { doubleTapType = 0; prefs.setDoubleTapType(selectedZone, 0) }
                                2 -> { tripleTapType = 0; prefs.setTripleTapType(selectedZone, 0) }
                                3 -> { longPressType = 0; prefs.setLongPressType(selectedZone, 0) }
                                else -> { singleTapType = 0; prefs.setSingleTapType(selectedZone, 0) }
                            }
                            showGestureSheetFor = null
                            onOpenPicker(gestureMode)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.open_app),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(
                                    if (currentType == 0) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer(scaleX = radioScale0, scaleY = radioScale0),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    SegmentedCard(
                        index = 1,
                        count = 3,
                        onClick = {
                            haptics.performSelection(view)
                            when (gestureMode) {
                                1 -> { doubleTapType = 1; prefs.setDoubleTapType(selectedZone, 1) }
                                2 -> { tripleTapType = 1; prefs.setTripleTapType(selectedZone, 1) }
                                3 -> { longPressType = 1; prefs.setLongPressType(selectedZone, 1) }
                                else -> { singleTapType = 1; prefs.setSingleTapType(selectedZone, 1) }
                            }
                            showGestureSheetFor = null
                            onOpenActionPicker(gestureMode)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.perform_action),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(
                                    if (currentType == 1) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer(scaleX = radioScale1, scaleY = radioScale1),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    SegmentedCard(
                        index = 2,
                        count = 3,
                        onClick = {
                            haptics.performSelection(view)
                            showGestureSheetFor = null
                            onOpenShortcutPicker(gestureMode)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.app_shortcut),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(
                                    if (currentType == 2) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer(scaleX = radioScale2, scaleY = radioScale2),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun GestureSquareCard(
    title: String,
    subtitle: String,
    isAppType: Boolean,
    appIcon: ImageBitmap?,
    actionIcon: ImageVector?,
    isEnabled: Boolean = true,
    clickable: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .height(152.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = clickable, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isEnabled) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = stringResource(R.string.disabled),
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isAppType && appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    } else if (!isAppType && actionIcon != null) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SwipeGestureCard(
    enabled: Boolean,
    target: Int,
    isEnabledControls: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val subtitleText = when {
        !enabled -> stringResource(R.string.disabled)
        target == SWIPE_TARGET_BRIGHTNESS -> stringResource(R.string.swipe_control_brightness)
        else -> stringResource(R.string.swipe_control_volume)
    }

    val iconVector = if (target == SWIPE_TARGET_BRIGHTNESS) {
        Icons.Rounded.Brightness7
    } else {
        Icons.Rounded.VolumeUp
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .height(152.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = isEnabledControls, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.swipe_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (!enabled || !isEnabledControls) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = stringResource(R.string.disabled),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerScreen(
    prefs: Prefs,
    gestureMode: Int = 0,
    zone: Int = 0,
    onBack: () -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val context = LocalContext.current
    BackHandler(onBack = {
        haptics.performLightTap(view)
        onBack()
    })
    val selectedActionId = when (gestureMode) {
        1 -> prefs.getDoubleTapActionId(zone)
        2 -> prefs.getTripleTapActionId(zone)
        3 -> prefs.getLongPressActionId(zone)
        else -> prefs.getSingleTapActionId(zone)
    }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val actionGroups = remember {
        listOf(
            R.string.group_quick_settings to quickSettingActions,
            R.string.group_media to mediaActions,
            R.string.group_display to displayActions,
            R.string.group_navigation to navigationActions,
            R.string.group_settings to settingsActions,
            R.string.group_advanced to advancedSettingsActions
        )
    }

    val filteredActionGroups = remember(actionGroups, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            actionGroups
        } else {
            val terms = q.split(' ').filter { it.isNotEmpty() }
            actionGroups.mapNotNull { (groupTitle, actions) ->
                val matchingActions = actions.filter { action ->
                    val title = context.getString(action.titleRes).lowercase()
                    val desc = context.getString(action.descRes).lowercase()
                    terms.all { term -> title.contains(term) || desc.contains(term) }
                }
                if (matchingActions.isNotEmpty()) {
                    groupTitle to matchingActions
                } else null
            }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty() && filteredActionGroups.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = maxOf(imePadding, navBarPadding) + 16.dp

    val screenTitle = when (gestureMode) {
        1 -> stringResource(R.string.double_tap_action)
        2 -> stringResource(R.string.triple_tap_action)
        3 -> stringResource(R.string.long_press_action)
        else -> stringResource(R.string.single_tap_action)
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
                placeholder = { Text(stringResource(R.string.search_actions), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
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
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear),
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

            if (filteredActionGroups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.no_actions_found), color = MaterialTheme.colorScheme.onSurface)
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
                    filteredActionGroups.forEach { (groupTitle, actions) ->
                        item(key = "header_$groupTitle") {
                            Text(
                                text = stringResource(groupTitle),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        itemsIndexed(
                            items = actions,
                            key = { _, action -> action.id }
                        ) { index, action ->
                            ActionRow(
                                action = action,
                                index = index,
                                totalCount = actions.size,
                                isSelected = (selectedActionId == action.id),
                                onSelect = {
                                    haptics.performSelection(view)
                                    when (gestureMode) {
                                        1 -> {
                                            prefs.setDoubleTapActionId(zone, action.id)
                                            prefs.setDoubleTapActionLabel(zone, context.getString(action.titleRes))
                                        }
                                        2 -> {
                                            prefs.setTripleTapActionId(zone, action.id)
                                            prefs.setTripleTapActionLabel(zone, context.getString(action.titleRes))
                                        }
                                        3 -> {
                                            prefs.setLongPressActionId(zone, action.id)
                                            prefs.setLongPressActionLabel(zone, context.getString(action.titleRes))
                                        }
                                        else -> {
                                            prefs.setSingleTapActionId(zone, action.id)
                                            prefs.setSingleTapActionLabel(zone, context.getString(action.titleRes))
                                        }
                                    }
                                    if (action.id in listOf("dnd", "ringer_mode", "mute")) {
                                        if (!hasNotificationPolicyAccess(context)) {
                                            openNotificationPolicyAccessSettings(context)
                                        }
                                    }
                                    onBack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionRow(
    action: ActionEntry,
    index: Int,
    totalCount: Int,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ActionScale"
    )

    SegmentedCard(
        index = index,
        count = totalCount,
        onClick = onSelect
    ) {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(action.titleRes),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    stringResource(action.descRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            trailingContent = {
                Icon(
                    painter = painterResource(
                        if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                    ),
                    contentDescription = if (isSelected) stringResource(R.string.cd_selected) else stringResource(R.string.cd_not_selected),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
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
                        text = stringResource(R.string.clock_app),
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
fun AppPickerScreen(
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
    var apps by remember { mutableStateOf<List<AppEntry>>(cachedApps ?: emptyList()) }
    val currentSelectedPkg = when (gestureMode) {
        1 -> prefs.getDoubleTapTargetPkg(zone)
        2 -> prefs.getTripleTapTargetPkg(zone)
        3 -> prefs.getLongPressTargetPkg(zone)
        else -> prefs.getSingleTapTargetPkg(zone) ?: (if (zone == 0) prefs.targetPackage else null)
    }
    var selected by remember { mutableStateOf(currentSelectedPkg) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val valid = validCachedApps(context)
        if (valid.isNullOrEmpty()) {
            batchLoadApps(context) { newBatch ->
                apps = newBatch
            }
        } else if (valid.size != apps.size) {
            apps = valid
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

    val screenTitle = when (gestureMode) {
        1 -> stringResource(R.string.double_tap_app)
        2 -> stringResource(R.string.triple_tap_app)
        3 -> stringResource(R.string.long_press_app)
        else -> stringResource(R.string.choose_app)
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
                        imageVector = Icons.Default.Search,
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
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear),
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
                                    haptics.performSelection(view)
                                    selected = app.packageName
                                    when (gestureMode) {
                                        1 -> {
                                            prefs.setDoubleTapTargetPkg(zone, app.packageName)
                                            prefs.setDoubleTapTargetLabel(zone, app.label)
                                        }
                                        2 -> {
                                            prefs.setTripleTapTargetPkg(zone, app.packageName)
                                            prefs.setTripleTapTargetLabel(zone, app.label)
                                        }
                                        3 -> {
                                            prefs.setLongPressTargetPkg(zone, app.packageName)
                                            prefs.setLongPressTargetLabel(zone, app.label)
                                        }
                                        else -> {
                                            prefs.setSingleTapTargetPkg(zone, app.packageName)
                                            prefs.setSingleTapTargetLabel(zone, app.label)
                                            if (zone == 0) {
                                                prefs.targetPackage = app.packageName
                                                prefs.targetLabel = app.label
                                            }
                                        }
                                    }
                                    onBack()
                                },
                                trailingContent = {
                                    Icon(
                                        painter = painterResource(
                                            if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                                        ),
                                        contentDescription = if (isSelected) stringResource(R.string.cd_selected) else stringResource(R.string.cd_not_selected),
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
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    BackHandler(onBack = {
        haptics.performLightTap(view)
        onBack()
    })
    var apps by remember { mutableStateOf<List<AppEntry>>(cachedApps ?: emptyList()) }
    var blockedSet by remember { mutableStateOf(prefs.blockedPackages) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val valid = validCachedApps(context)
        if (valid.isNullOrEmpty()) {
            batchLoadApps(context) { newBatch ->
                apps = newBatch
            }
        } else if (valid.size != apps.size) {
            apps = valid
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
                title = { Text(stringResource(R.string.filter_list_title)) },
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
                        imageVector = Icons.Default.Search,
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
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear),
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
                                    val newBlockedState = !isBlocked
                                    haptics.performToggle(view, newBlockedState)
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
                                            contentDescription = stringResource(R.string.cd_blocked),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer(scaleX = scale, scaleY = scale),
                                            tint = Color.Unspecified
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_radio_unselected),
                                            contentDescription = stringResource(R.string.cd_not_blocked),
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
    val labelLocale = AppLabels.appLocale(context)

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
        val label = AppLabels.label(context, ri, labelLocale)
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
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
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
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { newState ->
                if (enabled) {
                    haptics.performToggle(view, newState)
                    onChange(newState)
                }
            },
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
    stepInterval: Float = 15f,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
    onChangeFinished: (() -> Unit)? = null
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val helper = remember(min, max, stepInterval) { SliderHapticHelper(min, max, stepInterval) }

    var isEditing by remember { mutableStateOf(false) }
    var textFieldValue by remember(isEditing) {
        val str = value.toString()
        mutableStateOf(
            TextFieldValue(
                text = str,
                selection = TextRange(0, str.length)
            )
        )
    }
    var hadFocus by remember(isEditing) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun commitEdit() {
        val parsed = textFieldValue.text.toIntOrNull()
        val finalValue = (parsed ?: value).coerceIn(min.toInt(), max.toInt())
        onChange(finalValue)
        onChangeFinished?.invoke()
        isEditing = false
    }
    val unitSuffix = if (label.contains("speed", ignoreCase = true)) "ms" else "dp"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isEditing && enabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newTfv ->
                            val cleanDigits = newTfv.text.filter { it.isDigit() }.take(5)
                            textFieldValue = newTfv.copy(text = cleanDigits)
                            if (cleanDigits.isNotEmpty()) {
                                val parsed = cleanDigits.toIntOrNull()
                                if (parsed != null) {
                                    if (parsed in min.toInt()..max.toInt()) {
                                        onChange(parsed)
                                    } else if (parsed > max.toInt()) {
                                        onChange(max.toInt())
                                    }
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { commitEdit() }
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    hadFocus = true
                                } else if (hadFocus && isEditing) {
                                    commitEdit()
                                }
                            }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unitSuffix,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                Text(
                    text = "$value $unitSuffix",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled) {
                            haptics.performLightTap(view)
                            val str = value.toString()
                            textFieldValue = TextFieldValue(text = str, selection = TextRange(0, str.length))
                            isEditing = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Slider(
            value = value.toFloat(),
            enabled = enabled,
            onValueChange = { newValue ->
                if (enabled) {
                    helper.onValueChange(newValue, view, haptics)
                    onChange(newValue.toInt())
                }
            },
            onValueChangeFinished = {
                if (enabled) {
                    helper.onValueChangeFinished()
                    onChangeFinished?.invoke()
                }
            },
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
