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
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Adb
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
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.ContentPasteOff
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.sp
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

private fun swipeSummaryLines(context: Context, types: List<Int>, labels: List<String?>): List<String> {
    val order = listOf(SWIPE_DIR_UP, SWIPE_DIR_DOWN, SWIPE_DIR_LEFT, SWIPE_DIR_RIGHT)
    val lines = ArrayList<String>()
    for (dir in order) {
        val summary = when (types.getOrNull(dir)) {
            SWIPE_TYPE_VOLUME -> context.getString(R.string.volume)
            SWIPE_TYPE_BRIGHTNESS -> context.getString(R.string.brightness)
            SWIPE_TYPE_ACTION -> labels.getOrNull(dir)?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.perform_action)
            SWIPE_TYPE_APP -> labels.getOrNull(dir)?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.swipe_open_app)
            else -> null
        } ?: continue
        val title = when (dir) {
            SWIPE_DIR_LEFT -> context.getString(R.string.action_swipe_left)
            SWIPE_DIR_RIGHT -> context.getString(R.string.action_swipe_right)
            SWIPE_DIR_UP -> context.getString(R.string.swipe_dir_up)
            else -> context.getString(R.string.swipe_dir_down)
        }
        lines.add(context.getString(R.string.swipe_summary_line, title, summary))
    }
    return lines
}

private fun swipeDirDisplayLabel(context: Context, prefs: Prefs, zone: Int, dir: Int): String? =
    if (prefs.getSwipeDirType(zone, dir) == SWIPE_TYPE_APP) {
        prefs.getSwipeDirTargetLabel(zone, dir) ?: prefs.getSwipeDirTargetPkg(zone, dir)
    } else {
        SystemToggles.displayLabel(
            context,
            prefs.getSwipeDirActionId(zone, dir),
            prefs.getSwipeDirActionLabel(zone, dir),
            prefs.shellMethod
        )
    }

var cachedApps: List<AppEntry>? = null
private val iconCache = LruCache<String, ImageBitmap>(250)

fun invalidateAppCache(packageName: String? = null) {
    cachedApps = null
    if (packageName != null) iconCache.remove(packageName)
    AppShortcuts.invalidate()
}

suspend fun validCachedApps(context: Context): List<AppEntry>? = withContext(Dispatchers.IO) {
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
    @StringRes val descRes: Int = 0,
    val icon: ImageVector
)

val quickSettingActions = listOf(
    ActionEntry("wifi", R.string.action_wifi, R.string.action_wifi_desc, Icons.Rounded.Wifi),
    ActionEntry("bluetooth", R.string.action_bluetooth, R.string.action_bluetooth_desc, Icons.Rounded.Bluetooth),
    ActionEntry("data", R.string.action_data, R.string.action_data_desc, Icons.Rounded.SignalCellularAlt),
    ActionEntry("flashlight", R.string.action_flashlight, R.string.action_flashlight_desc, Icons.Rounded.FlashOn),
    ActionEntry("auto_rotate", R.string.action_auto_rotate, R.string.action_auto_rotate_desc, Icons.Rounded.ScreenRotation),
    ActionEntry("force_orientation", R.string.action_force_orientation, R.string.action_force_orientation_desc, Icons.Rounded.ScreenLockRotation),
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
    ActionEntry("media_previous", R.string.action_media_previous, R.string.action_media_previous_desc, Icons.Rounded.SkipPrevious),
    ActionEntry("volume_set", R.string.action_volume_set, R.string.action_volume_set_desc, Icons.Rounded.Tune)
)

val displayActions = listOf(
    ActionEntry("auto_brightness", R.string.action_auto_brightness, R.string.action_auto_brightness_desc, Icons.Rounded.BrightnessAuto),
    ActionEntry("auto_brightness_on", R.string.action_auto_brightness_on, R.string.action_auto_brightness_on_desc, Icons.Rounded.BrightnessAuto),
    ActionEntry("brightness_up", R.string.action_brightness_up, R.string.action_brightness_up_desc, Icons.Rounded.Brightness7),
    ActionEntry("brightness_down", R.string.action_brightness_down, R.string.action_brightness_down_desc, Icons.Rounded.Brightness4),
    ActionEntry("font_size", R.string.action_font_size, R.string.action_font_size_desc, Icons.Rounded.TextFields),
    ActionEntry("wallpaper", R.string.action_wallpaper, R.string.action_wallpaper_desc, Icons.Rounded.Wallpaper)
)

val navigationActions = listOf(
    ActionEntry("lock_screen", R.string.action_lock_screen, R.string.action_lock_screen_desc, Icons.Rounded.Lock),
    ActionEntry("screenshot", R.string.action_screenshot, R.string.action_screenshot_desc, Icons.Rounded.Camera),
    ActionEntry("screen_record", R.string.action_screen_record, R.string.action_screen_record_desc, Icons.Rounded.Videocam),
    ActionEntry("power_menu", R.string.action_power_menu, R.string.action_power_menu_desc, Icons.Rounded.PowerSettingsNew),
    ActionEntry("back", R.string.action_back, R.string.action_back_desc, Icons.AutoMirrored.Rounded.ArrowBack),
    ActionEntry("home", R.string.action_home, R.string.action_home_desc, Icons.Rounded.Home),
    ActionEntry("recents", R.string.action_recents, R.string.action_recents_desc, Icons.Rounded.Menu),
    ActionEntry("notifications", R.string.action_notifications, R.string.action_notifications_desc, Icons.Rounded.Notifications),
    ActionEntry("quick_settings", R.string.action_quick_settings, R.string.action_quick_settings_desc, Icons.Rounded.Notifications),
    ActionEntry("split_screen", R.string.action_split_screen, R.string.action_split_screen_desc, Icons.Rounded.VerticalSplit),
    ActionEntry("dismiss_shade", R.string.action_dismiss_shade, R.string.action_dismiss_shade_desc, Icons.Rounded.ExpandLess),
    ActionEntry("scroll_to_top", R.string.action_scroll_to_top, R.string.action_scroll_to_top_desc, Icons.Rounded.VerticalAlignTop),
    ActionEntry("previous_app", R.string.action_previous_app, R.string.action_previous_app_desc, Icons.Rounded.SwapHoriz),
    ActionEntry("scroll_to_bottom", R.string.action_scroll_to_bottom, R.string.action_scroll_to_bottom_desc, Icons.Rounded.VerticalAlignBottom),
    ActionEntry("page_up", R.string.action_page_up, R.string.action_page_up_desc, Icons.Rounded.KeyboardArrowUp),
    ActionEntry("page_down", R.string.action_page_down, R.string.action_page_down_desc, Icons.Rounded.KeyboardArrowDown),
    ActionEntry("copy", R.string.action_copy, R.string.action_copy_desc, Icons.Rounded.ContentCopy),
    ActionEntry("cut", R.string.action_cut, R.string.action_cut_desc, Icons.Rounded.ContentCut),
    ActionEntry("paste", R.string.action_paste, R.string.action_paste_desc, Icons.Rounded.ContentPaste),
    ActionEntry("select_all", R.string.action_select_all, R.string.action_select_all_desc, Icons.Rounded.SelectAll),
    ActionEntry("caffeine", R.string.action_caffeine, R.string.action_caffeine_desc, Icons.Rounded.Coffee),
    ActionEntry("refresh", R.string.action_refresh, R.string.action_refresh_desc, Icons.Rounded.Refresh),
    ActionEntry("swipe_left", R.string.action_swipe_left, R.string.action_swipe_left_desc, Icons.Rounded.SwipeLeft),
    ActionEntry("swipe_right", R.string.action_swipe_right, R.string.action_swipe_right_desc, Icons.Rounded.SwipeRight),
    ActionEntry("clear_clipboard", R.string.action_clear_clipboard, R.string.action_clear_clipboard_desc, Icons.Rounded.ContentPasteOff),
    ActionEntry("switch_keyboard", R.string.action_switch_keyboard, R.string.action_switch_keyboard_desc, Icons.Rounded.KeyboardAlt),
    ActionEntry("web_search", R.string.action_web_search, R.string.action_web_search_desc, Icons.Rounded.Search),
    ActionEntry("open_dialer", R.string.action_open_dialer, R.string.action_open_dialer_desc, Icons.Rounded.Dialpad),
    ActionEntry("current_app_info", R.string.action_current_app_info, R.string.action_current_app_info_desc, Icons.Rounded.Info),
    ActionEntry("open_browser", R.string.action_open_browser, R.string.action_open_browser_desc, Icons.Rounded.Public),
    ActionEntry("open_files", R.string.action_open_files, R.string.action_open_files_desc, Icons.Rounded.Folder),
    ActionEntry("all_apps", R.string.action_all_apps, R.string.action_all_apps_desc, Icons.Rounded.Apps),
    ActionEntry("accessibility_button", R.string.action_accessibility_button, R.string.action_accessibility_button_desc, Icons.Rounded.Accessibility),
    ActionEntry("accessibility_shortcut", R.string.action_accessibility_shortcut, R.string.action_accessibility_shortcut_desc, Icons.Rounded.AccessibilityNew),
    ActionEntry("headset_hook", R.string.action_headset_hook, R.string.action_headset_hook_desc, Icons.Rounded.Headset),
    ActionEntry("assistant", R.string.action_assistant, R.string.action_assistant_desc, Icons.Rounded.Mic),
    ActionEntry("camera", R.string.action_camera, R.string.action_camera_desc, Icons.Rounded.PhotoCamera),
    ActionEntry("camera_video", R.string.action_camera_video, R.string.action_camera_video_desc, Icons.Rounded.Videocam)
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
    ActionEntry("usb_debugging", R.string.action_usb_debugging, R.string.action_usb_debugging_desc, Icons.Rounded.Adb),
    ActionEntry("wireless_debugging", R.string.action_wireless_debugging, R.string.action_wireless_debugging_desc, Icons.Rounded.Adb),
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

val automationActions = listOf(
    ActionEntry("send_notification", R.string.action_send_notification, R.string.action_send_notification_desc, Icons.Rounded.NotificationsActive),
    ActionEntry("custom_command", R.string.action_custom_command, R.string.action_custom_command_desc, Icons.Rounded.Terminal)
)

val shellActions = listOf(
    ActionEntry("force_stop_app", R.string.action_force_stop_app, icon = Icons.Rounded.Close),
    ActionEntry("restart_systemui", R.string.action_restart_systemui, icon = Icons.Rounded.Refresh),
    ActionEntry("reboot", R.string.action_reboot, icon = Icons.Rounded.RestartAlt),
    ActionEntry("reboot_recovery", R.string.action_reboot_recovery, icon = Icons.Rounded.SettingsBackupRestore),
    ActionEntry("reboot_bootloader", R.string.action_reboot_bootloader, icon = Icons.Rounded.Memory),
    ActionEntry("dark_mode", R.string.action_dark_mode, icon = Icons.Rounded.DarkMode),
    ActionEntry("grayscale", R.string.action_grayscale, icon = Icons.Rounded.Contrast),
    ActionEntry("invert_colors", R.string.action_invert_colors, icon = Icons.Rounded.InvertColors),
    ActionEntry("extra_dim", R.string.action_extra_dim, icon = Icons.Rounded.BrightnessLow),
    ActionEntry("show_taps", R.string.action_show_taps, icon = Icons.Rounded.TouchApp),
    ActionEntry("pointer_location", R.string.action_pointer_location, icon = Icons.Rounded.NearMe),
    ActionEntry("animations_off", R.string.action_animations_off, icon = Icons.Rounded.Animation),
    ActionEntry("stay_awake", R.string.action_stay_awake, icon = Icons.Rounded.Visibility),
    ActionEntry("location_toggle", R.string.action_location_toggle, icon = Icons.Rounded.LocationOn),
    ActionEntry("private_dns", R.string.action_private_dns, icon = Icons.Rounded.Dns)
)

val systemActions = quickSettingActions + mediaActions + displayActions + navigationActions + automationActions + settingsActions + advancedSettingsActions + shellActions

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private val themeMode = mutableIntStateOf(0)
    private val ui = UiAppearance()
    private val restoreGeneration = BackupManager.generation

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
        ui.load(prefs)
        setContent {
            ProvideHapticManager(prefs) {
                AppRoot(prefs, themeMode, ui, onboardingDone)
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
        if (restoreGeneration != BackupManager.generation) {
            recreate()
            return
        }
        themeMode.intValue = prefs.themeMode
        ui.load(prefs)
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
fun AppRoot(prefs: Prefs, themeMode: MutableIntState, ui: UiAppearance, initialOnboardingDone: Boolean = true) {
    var onboardingDone by rememberSaveable { mutableStateOf(initialOnboardingDone) }
    var screen by remember { mutableStateOf("home") }
    var activeGestureMode by remember { mutableIntStateOf(0) }
    var activeZone by rememberSaveable { mutableIntStateOf(0) }

    TapbarTheme(
        themeMode = themeMode.intValue,
        uiStyle = ui.style
    ) {
        if (!onboardingDone) {
            LanguageOnboardingScreen(prefs) { onboardingDone = true }
            return@TapbarTheme
        }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == "picker" || targetState == "filter_picker" || targetState == "hide_picker" || targetState == "hide_setup" || targetState == "action_picker" || targetState == "shortcut_picker") {
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
                "hide_picker" -> HideFromAppsPickerScreen(prefs) { screen = "home" }
                "hide_setup" -> HideFromAppsSetupScreen(prefs) { screen = "home" }
                "action_picker" -> ActionPickerScreen(prefs, gestureMode = activeGestureMode, zone = activeZone) { screen = "home" }
                "shortcut_picker" -> ShortcutPickerScreen(prefs, gestureMode = activeGestureMode, zone = activeZone) { screen = "home" }
                else -> HomeScreen(
                    prefs = prefs,
                    initialZone = activeZone,
                    onZoneChange = { activeZone = it },
                    onSettingsReset = {
                        themeMode.intValue = prefs.themeMode
                        ui.load(prefs)
                    },
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
                    onOpenFilterPicker = { screen = "filter_picker" },
                    onOpenHidePicker = { screen = "hide_picker" },
                    onOpenHideSetup = { screen = "hide_setup" }
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
    onOpenFilterPicker: () -> Unit,
    onOpenHidePicker: () -> Unit,
    onOpenHideSetup: () -> Unit
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
    var disableOnLockscreen by remember { mutableStateOf(prefs.disableOnLockscreen) }
    var disableOnHome by remember { mutableStateOf(prefs.disableOnHome) }
    var disableMenuExpanded by remember { mutableStateOf(false) }
    var autoStart by remember { mutableStateOf(prefs.autoStart) }

    var singleTapEnabled by remember { mutableStateOf(prefs.getSingleTapEnabled(selectedZone)) }
    var singleTapType by remember { mutableIntStateOf(prefs.getSingleTapType(selectedZone)) }
    var singleTapPkg by remember { mutableStateOf(prefs.getSingleTapTargetPkg(selectedZone, context)) }
    var singleTapLabel by remember {
        mutableStateOf(
            when (singleTapType) {
                1 -> SystemToggles.displayLabel(context, prefs.getSingleTapActionId(selectedZone), prefs.getSingleTapActionLabel(selectedZone), prefs.shellMethod)
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
                1 -> SystemToggles.displayLabel(context, prefs.getDoubleTapActionId(selectedZone), prefs.getDoubleTapActionLabel(selectedZone), prefs.shellMethod)
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
                1 -> SystemToggles.displayLabel(context, prefs.getTripleTapActionId(selectedZone), prefs.getTripleTapActionLabel(selectedZone), prefs.shellMethod)
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
                1 -> SystemToggles.displayLabel(context, prefs.getLongPressActionId(selectedZone), prefs.getLongPressActionLabel(selectedZone), prefs.shellMethod)
                2 -> prefs.getLongPressShortcutDisplay(selectedZone)
                else -> prefs.getLongPressTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
            }
        )
    }
    var longPressActionId by remember { mutableStateOf(prefs.getLongPressActionId(selectedZone)) }

    var swipeEnabled by remember { mutableStateOf(prefs.getSwipeEnabled(selectedZone)) }
    var swipeTarget by remember { mutableIntStateOf(prefs.getSwipeTarget(selectedZone)) }
    var swipeBrightnessHud by remember { mutableStateOf(prefs.getSwipeBrightnessHud(selectedZone)) }
    var swipeAutoNotice by remember { mutableStateOf(prefs.getSwipeAutoNotice(selectedZone)) }
    var swipeMode by remember { mutableIntStateOf(prefs.getSwipeMode(selectedZone)) }
    var swipeDirTypes by remember { mutableStateOf(List(4) { prefs.getSwipeDirType(selectedZone, it) }) }
    var swipeDirLabels by remember { mutableStateOf(List(4) { swipeDirDisplayLabel(context, prefs, selectedZone, it) }) }
    var swipeEditDir by remember { mutableStateOf<Int?>(null) }

    var tapSpeedMs by remember { mutableIntStateOf(prefs.tapSpeedMs) }
    var longPressMs by remember { mutableIntStateOf(prefs.longPressMs) }
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

        singleTapEnabled = prefs.getSingleTapEnabled(selectedZone)
        singleTapType = prefs.getSingleTapType(selectedZone)
        singleTapPkg = prefs.getSingleTapTargetPkg(selectedZone, context)
        singleTapLabel = when (singleTapType) {
            1 -> SystemToggles.displayLabel(context, prefs.getSingleTapActionId(selectedZone), prefs.getSingleTapActionLabel(selectedZone), prefs.shellMethod)
            2 -> prefs.getSingleTapShortcutDisplay(selectedZone, context)
            else -> prefs.getSingleTapTargetLabel(selectedZone, context)
        }
        singleTapActionId = prefs.getSingleTapActionId(selectedZone)

        doubleTapEnabled = prefs.getDoubleTapEnabled(selectedZone)
        doubleTapType = prefs.getDoubleTapType(selectedZone)
        doubleTapPkg = prefs.getDoubleTapTargetPkg(selectedZone)
        doubleTapLabel = when (doubleTapType) {
            1 -> SystemToggles.displayLabel(context, prefs.getDoubleTapActionId(selectedZone), prefs.getDoubleTapActionLabel(selectedZone), prefs.shellMethod)
            2 -> prefs.getDoubleTapShortcutDisplay(selectedZone)
            else -> prefs.getDoubleTapTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
        }
        doubleTapActionId = prefs.getDoubleTapActionId(selectedZone)

        tripleTapEnabled = prefs.getTripleTapEnabled(selectedZone)
        tripleTapType = prefs.getTripleTapType(selectedZone)
        tripleTapPkg = prefs.getTripleTapTargetPkg(selectedZone)
        tripleTapLabel = when (tripleTapType) {
            1 -> SystemToggles.displayLabel(context, prefs.getTripleTapActionId(selectedZone), prefs.getTripleTapActionLabel(selectedZone), prefs.shellMethod)
            2 -> prefs.getTripleTapShortcutDisplay(selectedZone)
            else -> prefs.getTripleTapTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
        }
        tripleTapActionId = prefs.getTripleTapActionId(selectedZone)

        longPressEnabled = prefs.getLongPressEnabled(selectedZone)
        longPressType = prefs.getLongPressType(selectedZone)
        longPressPkg = prefs.getLongPressTargetPkg(selectedZone)
        longPressLabel = when (longPressType) {
            1 -> SystemToggles.displayLabel(context, prefs.getLongPressActionId(selectedZone), prefs.getLongPressActionLabel(selectedZone), prefs.shellMethod)
            2 -> prefs.getLongPressShortcutDisplay(selectedZone)
            else -> prefs.getLongPressTargetLabel(selectedZone) ?: context.getString(R.string.not_set)
        }
        longPressActionId = prefs.getLongPressActionId(selectedZone)

        swipeEnabled = prefs.getSwipeEnabled(selectedZone)
        swipeTarget = prefs.getSwipeTarget(selectedZone)
        swipeBrightnessHud = prefs.getSwipeBrightnessHud(selectedZone)
        swipeAutoNotice = prefs.getSwipeAutoNotice(selectedZone)
        swipeMode = prefs.getSwipeMode(selectedZone)
        swipeDirTypes = List(4) { prefs.getSwipeDirType(selectedZone, it) }
        swipeDirLabels = List(4) { swipeDirDisplayLabel(context, prefs, selectedZone, it) }
    }

    LaunchedEffect(showGestureSheetFor, selectedZone) {
        if (showGestureSheetFor == null) swipeEditDir = null
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

    DisposableEffect(Unit) {
        TapZone.onZoneSelected = { zone ->
            if (zone != selectedZone) {
                haptics.performSelection(view)
                selectedZone = zone
            }
        }
        onDispose { TapZone.onZoneSelected = null }
    }

    val orientationKey = LocalConfiguration.current.orientation
    LaunchedEffect(orientationKey) {
        refreshZoneState()
    }

    DisposableEffect(Unit) {
        val stopObserving = ShellAccess.observeShizuku { refreshZoneState() }
        onDispose { stopObserving() }
    }

    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                a11yOn = TapAccessibilityService.isEnabled(context)
            }
        }
        val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = TapAccessibilityService.isEnabled(context)
                hasOverlay = Settings.canDrawOverlays(context)
                enabled = prefs.serviceEnabled
                zoneEnabledStates = List(ZONE_COUNT) { prefs.isZoneEnabled(it) }
                tapSpeedMs = prefs.tapSpeedMs
                longPressMs = prefs.longPressMs
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
        prefs.setGeometry(selectedZone, ZoneGeometry(x, y, w, h))
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isZoneControlsEnabled = zoneEnabledStates.getOrElse(selectedZone) { true }
    val homeScroll = rememberScrollState()

    val menuButton: @Composable () -> Unit = {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { CollapsingTitle(stringResource(R.string.app_name), homeScroll, inset = 12.dp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                actions = {
                    Box(Modifier.collapsedToolbarFade(homeScroll)) { menuButton() }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding())
                .verticalScroll(homeScroll)
                .padding(
                    bottom = navBarPadding + 16.dp,
                    start = if (isOneUiStyle()) 20.dp else 16.dp,
                    end = if (isOneUiStyle()) 20.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpandedAppBarTitle(stringResource(R.string.app_name), homeScroll, actions = menuButton)

            if (!a11yOn) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.accessibility_service_title),
                        style = sectionHeaderStyle(),
                        color = sectionHeaderColor(),
                        modifier = Modifier.sectionHeaderInset()
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
                                    TapAccessibilityService.openSettings(context)
                                },
                                colors = tapbarPrimaryButtonColors()
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
                        style = sectionHeaderStyle(),
                        color = sectionHeaderColor(),
                        modifier = Modifier.sectionHeaderInset()
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
                                colors = tapbarPrimaryButtonColors()
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
                    style = sectionHeaderStyle(),
                    color = sectionHeaderColor(),
                    modifier = Modifier.sectionHeaderInset()
                )
                val disableArrowRotation by animateFloatAsState(
                    targetValue = if (disableMenuExpanded) 180f else 0f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "DisableArrowRotation"
                )
                val disableLandscapeLabel = stringResource(R.string.disable_option_landscape)
                val disableLockscreenLabel = stringResource(R.string.disable_option_lockscreen)
                val disableHomeLabel = stringResource(R.string.disable_option_home)
                val disableSummary = listOfNotNull(
                    disableLandscapeLabel.takeIf { disableInLandscape },
                    disableLockscreenLabel.takeIf { disableOnLockscreen },
                    disableHomeLabel.takeIf { disableOnHome }
                ).joinToString(", ").ifEmpty { stringResource(R.string.disable_summary_none) }
                val disableGroupCount = if (disableMenuExpanded) 6 else 3
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegmentedCard(index = 0, count = disableGroupCount) {
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
                    SegmentedCard(index = 1, count = disableGroupCount) {
                        SwitchRow(
                            label = stringResource(R.string.start_on_boot),
                            checked = autoStart,
                            onChange = {
                                autoStart = it
                                prefs.autoStart = it
                            }
                        )
                    }
                    SegmentedCard(
                        index = 2,
                        count = disableGroupCount,
                        onClick = {
                            haptics.performExpandCollapse(view)
                            disableMenuExpanded = !disableMenuExpanded
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.disable_menu_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = disableSummary,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = disableArrowRotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AnimatedVisibility(visible = disableMenuExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SegmentedCard(index = 3, count = disableGroupCount) {
                                SwitchRow(
                                    label = disableLandscapeLabel,
                                    checked = disableInLandscape,
                                    onChange = {
                                        disableInLandscape = it
                                        prefs.disableInLandscape = it
                                        TapZone.active?.attach()
                                    }
                                )
                            }
                            SegmentedCard(index = 4, count = disableGroupCount) {
                                SwitchRow(
                                    label = disableLockscreenLabel,
                                    checked = disableOnLockscreen,
                                    onChange = {
                                        disableOnLockscreen = it
                                        prefs.disableOnLockscreen = it
                                        if (it && disableOnHome) {
                                            disableOnHome = false
                                            prefs.disableOnHome = false
                                        }
                                        TapZone.active?.attach()
                                    }
                                )
                            }
                            SegmentedCard(index = 5, count = disableGroupCount) {
                                SwitchRow(
                                    label = disableHomeLabel,
                                    checked = disableOnHome,
                                    onChange = {
                                        disableOnHome = it
                                        prefs.disableOnHome = it
                                        if (it && disableOnLockscreen) {
                                            disableOnLockscreen = false
                                            prefs.disableOnLockscreen = false
                                        }
                                        TapZone.active?.attach()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.tap_zones),
                    style = sectionHeaderStyle(),
                    color = sectionHeaderColor(),
                    modifier = Modifier.sectionHeaderInset()
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
                        style = sectionHeaderStyle(),
                        color = sectionHeaderColor(),
                        modifier = Modifier.sectionHeaderInset()
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
                            advanced = swipeMode == SWIPE_MODE_ADVANCED,
                            dirLines = swipeSummaryLines(context, swipeDirTypes, swipeDirLabels),
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

                    val showTapSpeed = doubleTapEnabled || tripleTapEnabled
                    val timingCount = (if (showTapSpeed) 1 else 0) + (if (longPressEnabled) 1 else 0)
                    if (timingCount > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.speed_and_timing),
                            style = sectionHeaderStyle(),
                            color = sectionHeaderColor(),
                            modifier = Modifier.sectionHeaderInset()
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (showTapSpeed) {
                                SegmentedCard(index = 0, count = timingCount) {
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
                            if (longPressEnabled) {
                                SegmentedCard(index = timingCount - 1, count = timingCount) {
                                    DpSlider(
                                        label = stringResource(R.string.long_press_duration),
                                        value = longPressMs,
                                        min = 200f,
                                        max = 1000f,
                                        stepInterval = 50f,
                                        enabled = isZoneControlsEnabled,
                                        unit = "ms",
                                        onChange = {
                                            if (isZoneControlsEnabled) {
                                                longPressMs = it
                                                prefs.longPressMs = it
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.position_and_size),
                        style = sectionHeaderStyle(),
                        color = sectionHeaderColor(),
                        modifier = Modifier.sectionHeaderInset()
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
                    style = sectionHeaderStyle(),
                    color = sectionHeaderColor(),
                    modifier = Modifier.sectionHeaderInset()
                )

                SegmentedCard(
                    index = 0,
                    count = 1,
                    onClick = {
                        haptics.performLightTap(view)
                        onOpenFilterPicker()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_block_red),
                            contentDescription = stringResource(R.string.cd_block_in_apps),
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFFD71921)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (blockedCount == 0) stringResource(R.string.no_apps_blocked)
                                else context.resources.getQuantityString(R.plurals.apps_blocked, blockedCount, blockedCount),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.block_in_apps_desc),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HideFromAppsSection(prefs, onOpenHidePicker, onOpenHideSetup)

            val resetDoneText = stringResource(R.string.settings_reset_done)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.reset_title),
                    style = sectionHeaderStyle(),
                    color = sectionHeaderColor(),
                    modifier = Modifier.sectionHeaderInset()
                )
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
                        disableOnLockscreen = prefs.disableOnLockscreen
                        disableOnHome = prefs.disableOnHome
                        autoStart = prefs.autoStart
                        tapSpeedMs = prefs.tapSpeedMs
                        longPressMs = prefs.longPressMs
                        blockedCount = prefs.blockedPackages.size
                        refreshZoneState()

                        TapZone.active?.attach()
                        TapZone.active?.setSelectedZoneForPreview(0)

                        onSettingsReset()
                        Toast.makeText(context, resetDoneText, Toast.LENGTH_SHORT).show()
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reset_hint_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.reset_hint_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showGestureSheetFor != null) {
        val gestureMode = showGestureSheetFor!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetScroll = rememberScrollState()

        LaunchedEffect(sheetScroll) {
            var previousMax = sheetScroll.maxValue
            snapshotFlow { sheetScroll.maxValue }.collect { max ->
                if (sheetScroll.value >= previousMax) sheetScroll.scrollTo(max)
                previousMax = max
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showGestureSheetFor = null },
            sheetState = sheetState,
            containerColor = if (LocalImmersive.current != null) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                overlayContainerColor()
            },
            dragHandle = { if (LocalImmersive.current == null) BottomSheetDefaults.DragHandle() },
            contentWindowInsets = {
                if (LocalImmersive.current != null) WindowInsets(0, 0, 0, 0) else BottomSheetDefaults.windowInsets
            }
        ) {
            Column(
                modifier = Modifier
                    .immersiveSheetBackdrop()
                    .fillMaxWidth()
                    .verticalScroll(sheetScroll)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (LocalImmersive.current != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }
                }

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
                }

                if (gestureMode == 4) {
                    BackHandler(enabled = swipeEditDir != null) {
                        swipeEditDir = null
                    }
                    SwipeSettings(
                        header = {
                            SwitchRow(
                                label = stringResource(R.string.enable_swipe),
                                checked = swipeEnabled,
                                onChange = {
                                    swipeEnabled = it
                                    prefs.setSwipeEnabled(selectedZone, it)
                                }
                            )
                        },
                        mode = swipeMode,
                        onModeChange = {
                            swipeMode = it
                            swipeEditDir = null
                            prefs.setSwipeMode(selectedZone, it)
                        },
                        target = swipeTarget,
                        onTargetChange = { picked ->
                            swipeTarget = picked
                            prefs.setSwipeTarget(selectedZone, picked)
                        },
                        dirTypes = swipeDirTypes,
                        dirLabels = swipeDirLabels,
                        editDir = swipeEditDir,
                        onEditDir = { swipeEditDir = it },
                        onDirType = { dir, type ->
                            prefs.setSwipeDirType(selectedZone, dir, type)
                            swipeDirTypes = swipeDirTypes.toMutableList().also { list -> list[dir] = type }
                        },
                        onPickAction = { dir ->
                            showGestureSheetFor = null
                            onOpenActionPicker(5 + dir)
                        },
                        onPickApp = { dir ->
                            showGestureSheetFor = null
                            onOpenPicker(5 + dir)
                        },
                        hudEnabled = swipeBrightnessHud,
                        noticeEnabled = swipeAutoNotice,
                        onHudChange = {
                            swipeBrightnessHud = it
                            prefs.setSwipeBrightnessHud(selectedZone, it)
                        },
                        onNoticeChange = {
                            swipeAutoNotice = it
                            prefs.setSwipeAutoNotice(selectedZone, it)
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

                if (LocalImmersive.current != null) {
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
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
    TapbarCard(
        shape = RoundedCornerShape(20.dp),
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
                            painter = painterResource(R.drawable.ic_block_red),
                            contentDescription = stringResource(R.string.disabled),
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFFD71921)
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
    advanced: Boolean = false,
    dirLines: List<String> = emptyList(),
    isEnabledControls: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val showLines = enabled && advanced && dirLines.isNotEmpty()
    val subtitleText = when {
        !enabled -> stringResource(R.string.disabled)
        advanced -> stringResource(R.string.swipe_mode_advanced)
        target == SWIPE_TARGET_BRIGHTNESS -> stringResource(R.string.swipe_control_brightness)
        else -> stringResource(R.string.swipe_control_volume)
    }

    val iconVector = when {
        advanced -> Icons.Rounded.SwipeRight
        target == SWIPE_TARGET_BRIGHTNESS -> Icons.Rounded.Brightness7
        else -> Icons.Rounded.VolumeUp
    }

    TapbarCard(
        shape = RoundedCornerShape(20.dp),
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
                            painter = painterResource(R.drawable.ic_block_red),
                            contentDescription = stringResource(R.string.disabled),
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFFD71921)
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

                if (showLines) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        dirLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
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
        in 5..8 -> prefs.getSwipeDirActionId(zone, gestureMode - 5)
        else -> prefs.getSingleTapActionId(zone)
    }
    var selectedId by remember { mutableStateOf(selectedActionId) }
    var volumeLevel by remember { mutableIntStateOf(prefs.getVolumeLevel(zone, gestureMode)) }

    var commandText by remember { mutableStateOf(prefs.getCustomCommand(zone, gestureMode)) }

    fun saveActionLabel(label: String) {
        when (gestureMode) {
            1 -> prefs.setDoubleTapActionLabel(zone, label)
            2 -> prefs.setTripleTapActionLabel(zone, label)
            3 -> prefs.setLongPressActionLabel(zone, label)
            in 5..8 -> prefs.setSwipeDirActionLabel(zone, gestureMode - 5, label)
            else -> prefs.setSingleTapActionLabel(zone, label)
        }
    }

    fun saveVolumeLabel(level: Int) {
        saveActionLabel(context.getString(R.string.action_volume_set_label, level))
    }

    fun saveCommandLabel(text: String) {
        val summary = text.trim().replace('\n', ' ').take(32)
        saveActionLabel(
            if (summary.isEmpty()) context.getString(R.string.action_custom_command)
            else context.getString(R.string.action_custom_command_label, summary)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val actionGroups = remember {
        val method = prefs.shellMethod
        val collator = java.text.Collator.getInstance(context.resources.configuration.locales[0])
        listOf(
            R.string.group_automation to automationActions,
            R.string.group_quick_settings to quickSettingActions,
            R.string.group_media to mediaActions,
            R.string.group_display to displayActions,
            R.string.group_navigation to navigationActions,
            R.string.group_settings to settingsActions,
            R.string.group_advanced to advancedSettingsActions,
            R.string.group_shell to shellActions
        ).map { (groupTitle, actions) ->
            groupTitle to actions
                .map { SystemToggles.resolve(it, method) }
                .sortedWith { a, b -> collator.compare(context.getString(a.titleRes), context.getString(b.titleRes)) }
        }
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
                    val desc = if (action.descRes != 0) context.getString(action.descRes).lowercase() else ""
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
        5 -> stringResource(R.string.action_swipe_left)
        6 -> stringResource(R.string.action_swipe_right)
        7 -> stringResource(R.string.swipe_dir_up)
        8 -> stringResource(R.string.swipe_dir_down)
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
                            if (groupTitle == R.string.group_automation) {
                                ShimmerText(
                                    text = stringResource(groupTitle),
                                    style = MaterialTheme.typography.labelLarge,
                                    baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    highlightColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                                )
                            } else {
                                Text(
                                    text = stringResource(groupTitle),
                                    style = sectionHeaderStyle(),
                                    color = sectionHeaderColor(),
                                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(
                            items = actions,
                            key = { _, action -> action.id }
                        ) { index, action ->
                            ActionRow(
                                action = action,
                                index = index,
                                totalCount = actions.size,
                                isSelected = (selectedId == action.id),
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
                                        in 5..8 -> {
                                            val dir = gestureMode - 5
                                            prefs.setSwipeDirActionId(zone, dir, action.id)
                                            prefs.setSwipeDirActionLabel(zone, dir, context.getString(action.titleRes))
                                            prefs.setSwipeDirType(zone, dir, SWIPE_TYPE_ACTION)
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
                                    if (action.id == "send_notification" && !AutomationNotifier.isReady(context)) {
                                        AutomationNotifier.openSettings(context)
                                    }
                                    selectedId = action.id
                                    if (action.id == "volume_set") {
                                        saveVolumeLabel(volumeLevel)
                                    } else if (action.id == "custom_command") {
                                        saveCommandLabel(commandText)
                                    } else if (action.id != "screen_record") {
                                        onBack()
                                    }
                                }
                            )
                        }
                        if (selectedId == "custom_command" && actions.any { it.id == "custom_command" }) {
                            item(key = "custom_command_card") {
                                SegmentedCard(
                                    index = 0,
                                    count = 1,
                                    modifier = Modifier.padding(top = 10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = commandText,
                                            onValueChange = {
                                                commandText = it
                                                prefs.setCustomCommand(zone, gestureMode, it)
                                                saveCommandLabel(it)
                                            },
                                            label = { Text(stringResource(R.string.custom_command_hint)) },
                                            minLines = 2,
                                            maxLines = 5,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = stringResource(R.string.custom_command_note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (selectedId == "volume_set" && actions.any { it.id == "volume_set" }) {
                            item(key = "volume_level_card") {
                                SegmentedCard(
                                    index = 0,
                                    count = 1,
                                    modifier = Modifier.padding(top = 10.dp)
                                ) {
                                    DpSlider(
                                        label = stringResource(R.string.volume_level),
                                        value = volumeLevel,
                                        min = 0f,
                                        max = 100f,
                                        stepInterval = 5f,
                                        unit = "%",
                                        onChange = {
                                            volumeLevel = it
                                            prefs.setVolumeLevel(zone, gestureMode, it)
                                        },
                                        onChangeFinished = { saveVolumeLabel(volumeLevel) }
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

@Composable
fun ActionRow(
    action: ActionEntry,
    index: Int,
    totalCount: Int,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    PlainActionRow(action = action, index = index, totalCount = totalCount, isSelected = isSelected, onSelect = onSelect)
}

@Composable
fun PlainActionRow(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(action.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (action.descRes != 0) {
                    Text(
                        stringResource(action.descRes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
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
        if (action.id == "screen_record") {
            val context = LocalContext.current
            val prefs = remember { Prefs(context) }
            var recordAudio by remember { mutableStateOf(prefs.screenRecordAudio) }
            val audioSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            AnimatedVisibility(visible = isSelected) {
                Column(modifier = Modifier.padding(start = 40.dp)) {
                    SwitchRow(
                        label = stringResource(R.string.record_option_audio),
                        checked = recordAudio && audioSupported,
                        enabled = audioSupported,
                        onChange = {
                            recordAudio = it
                            prefs.screenRecordAudio = it
                        }
                    )
                }
            }
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
        in 5..8 -> prefs.getSwipeDirTargetPkg(zone, gestureMode - 5)
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
                                        in 5..8 -> {
                                            val dir = gestureMode - 5
                                            prefs.setSwipeDirTargetPkg(zone, dir, app.packageName)
                                            prefs.setSwipeDirTargetLabel(zone, dir, app.label)
                                            prefs.setSwipeDirType(zone, dir, SWIPE_TYPE_APP)
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
                                            tint = Color(0xFFD71921)
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

suspend fun batchLoadApps(
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
        TapbarSwitch(
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
    onChangeFinished: (() -> Unit)? = null,
    unit: String? = null
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
    val unitSuffix = unit ?: if (label.contains("speed", ignoreCase = true)) "ms" else "dp"

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
                    text = if (unit == "%") "$value%" else "$value $unitSuffix",
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

        TapbarSlider(
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
            modifier = Modifier.thumbOnly(((value - min) / (max - min)).coerceIn(0f, 1f))
        )
    }
}