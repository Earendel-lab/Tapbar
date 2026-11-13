package com.earendel.tapbar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

private const val ANDROID_XML_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val MAX_ASSET_COOKIE = 16

private val intentAppIconCache = LruCache<String, ImageBitmap>(100)

private val IntentSpecSaver = Saver<IntentSpec, String>(
    save = { it.toJson() },
    restore = { IntentSpec.fromJson(it) }
)

private fun readableIntentName(raw: String): String {
    val last = raw.substringAfterLast('.')
    if (last.isEmpty() || !last.all { it.isUpperCase() || it.isDigit() || it == '_' }) return last
    val words = last.removePrefix("ACTION_").removePrefix("CATEGORY_").split('_').filter { it.isNotEmpty() }
    if (words.isEmpty()) return last
    return words.joinToString(" ") { it.lowercase() }.replaceFirstChar { it.uppercase() }
}

private data class ChoiceItem(
    val value: String,
    val title: String = value,
    val subtitle: String? = null,
    val iconPackage: String? = null,
    val lockReason: String? = null,
    val lockPermission: String? = null
)

private enum class IntentSheet { ACTION, CATEGORY, APP, CLASS, FLAGS }

private fun loadApps(context: Context): List<ChoiceItem> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .map { info ->
            ChoiceItem(
                value = info.packageName,
                title = pm.getApplicationLabel(info).toString(),
                subtitle = info.packageName,
                iconPackage = info.packageName
            )
        }
        .sortedBy { it.title.lowercase() }
}

// Only the system may send these, and each device defines its own set. Android has no public API for it, so this
// reads the framework manifest. Overlays share the same asset paths, hence every cookie is tried, not just the first.
private fun systemProtectedBroadcasts(context: Context): Set<String> {
    val assets = runCatching { context.createPackageContext("android", 0).assets }.getOrNull() ?: return emptySet()
    val protected = HashSet<String>()
    for (cookie in 1..MAX_ASSET_COOKIE) {
        runCatching {
            assets.openXmlResourceParser(cookie, "AndroidManifest.xml").use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "protected-broadcast") {
                        parser.getAttributeValue(ANDROID_XML_NAMESPACE, "name")?.let(protected::add)
                    }
                }
            }
        }
    }
    return protected
}

private fun loadActions(context: Context, target: IntentTarget): List<ChoiceItem> {
    val listed = runCatching {
        context.assets.open("intent_actions/${target.name.lowercase()}.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toList()
        }
    }.getOrDefault(emptyList())
    val actions = when (target) {
        IntentTarget.ACTIVITY -> (commonIntentActions + listed).distinct()
        IntentTarget.BROADCAST -> listed - systemProtectedBroadcasts(context)
        else -> listed
    }
    return actions.map { ChoiceItem(it, readableIntentName(it), it) }
}

// These two are app ops: checkSelfPermission reports them denied even after the user has allowed them.
private fun holdsPermission(context: Context, permission: String): Boolean = when (permission) {
    Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(context)
    Manifest.permission.WRITE_SETTINGS -> Settings.System.canWrite(context)
    else -> context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

private fun permissionSettingsAction(permission: String): String? = when (permission) {
    Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
    Manifest.permission.WRITE_SETTINGS -> Settings.ACTION_MANAGE_WRITE_SETTINGS
    else -> null
}

private fun loadClasses(context: Context, pkg: String, target: IntentTarget): List<ChoiceItem> {
    val pm = context.packageManager
    val components: Array<out ComponentInfo>? = runCatching {
        when (target) {
            IntentTarget.ACTIVITY -> pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities
            IntentTarget.BROADCAST -> pm.getPackageInfo(pkg, PackageManager.GET_RECEIVERS).receivers
            else -> pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services
        }
    }.getOrNull()
    val requested = runCatching {
        pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
    }.getOrNull().orEmpty().toSet()
    return components.orEmpty()
        .filter { it.exported }
        .map { component ->
            val permission = when (component) {
                is ActivityInfo -> component.permission
                is ServiceInfo -> component.permission
                else -> null
            }
            // An app can always start its own components, whatever permission they ask of others.
            val blocked = permission != null && pkg != context.packageName && !holdsPermission(context, permission)
            ChoiceItem(
                value = component.name,
                title = component.name.substringAfterLast('.'),
                subtitle = component.name,
                lockReason = if (blocked) context.getString(R.string.intent_lock_missing, permission) else null,
                lockPermission = permission?.takeIf { blocked && it in requested }
            )
        }
        .sortedBy { it.title.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentEditorScreen(
    prefs: Prefs,
    gestureMode: Int = 0,
    zone: Int = 0,
    onBack: () -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val context = LocalContext.current

    val initial = remember { IntentSpec.fromJson(prefs.getIntentSpec(zone, gestureMode)) ?: IntentSpec() }
    var draft by rememberSaveable(stateSaver = IntentSpecSaver) { mutableStateOf(initial) }
    var sheet by remember { mutableStateOf<IntentSheet?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun leave() {
        haptics.performLightTap(view)
        if (draft != initial) confirmDiscard = true else onBack()
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun test() {
        haptics.performSelection(view)
        val problem = draft.problem(context)
        if (problem != null) {
            toast(problem)
            return
        }
        toast(context.getString(IntentSpec.resultMessage(draft.send(context))))
    }

    fun save() {
        haptics.performSelection(view)
        val problem = draft.problem(context)
        val json = draft.toJson()
        when {
            problem != null -> toast(problem)
            json.length > BackupManager.MAX_STRING -> toast(context.getString(R.string.intent_error_too_long))
            else -> {
                prefs.setIntentSpec(zone, gestureMode, json)
                when (gestureMode) {
                    1 -> prefs.setDoubleTapType(zone, GESTURE_TYPE_INTENT)
                    2 -> prefs.setTripleTapType(zone, GESTURE_TYPE_INTENT)
                    3 -> prefs.setLongPressType(zone, GESTURE_TYPE_INTENT)
                    in 5..8 -> prefs.setSwipeDirType(zone, gestureMode - 5, SWIPE_TYPE_INTENT)
                    else -> prefs.setSingleTapType(zone, GESTURE_TYPE_INTENT)
                }
                onBack()
            }
        }
    }

    BackHandler(enabled = sheet == null, onBack = ::leave)

    val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (sheet == null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.run_intent)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    navigationIcon = {
                        IconButton(onClick = ::leave) {
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
                    .padding(top = pad.calculateTopPadding(), bottom = maxOf(imePadding, navBarPadding))
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val targetCount = 1 + if (targetExpanded) IntentTarget.entries.size else 0

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntentFieldCard(
                            value = draft.name,
                            onChange = { draft = draft.copy(name = it) },
                            label = stringResource(R.string.intent_name),
                            capitalize = true
                        )
                        IntentDropdownBlock(
                            index = 0,
                            count = targetCount,
                            label = stringResource(R.string.intent_target),
                            selected = targetLabel(draft.target),
                            selectedValue = draft.target,
                            options = IntentTarget.entries.map { targetLabel(it) to it },
                            expanded = targetExpanded,
                            onExpandedChange = { targetExpanded = it },
                            onSelect = { picked ->
                                if (picked != draft.target) draft = draft.copy(target = picked, flags = 0, foreground = false)
                            }
                        )
                    }

                    if (draft.target == IntentTarget.SERVICE) {
                        SegmentedCard(index = 0, count = 1) {
                            SwitchRow(
                                label = stringResource(R.string.intent_run_foreground),
                                checked = draft.foreground,
                                onChange = { draft = draft.copy(foreground = it) }
                            )
                        }
                    }

                    targetHint(draft)?.let { hint ->
                        Text(
                            text = stringResource(hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    SectionLabel(stringResource(R.string.intent_section_intent))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntentFieldCard(
                            value = draft.action,
                            onChange = { draft = draft.copy(action = it) },
                            label = stringResource(R.string.intent_action),
                            onBrowse = { sheet = IntentSheet.ACTION }
                        )
                        IntentFieldCard(
                            value = draft.data,
                            onChange = { draft = draft.copy(data = it) },
                            label = stringResource(R.string.intent_data)
                        )
                        IntentFieldCard(
                            value = draft.mime,
                            onChange = { draft = draft.copy(mime = it) },
                            label = stringResource(R.string.intent_mime)
                        )
                        IntentFieldCard(
                            value = draft.categories,
                            onChange = { draft = draft.copy(categories = it) },
                            label = stringResource(R.string.intent_categories),
                            singleLine = false,
                            onBrowse = { sheet = IntentSheet.CATEGORY }
                        )
                    }

                    SectionLabel(stringResource(R.string.intent_section_receiver))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntentFieldCard(
                            value = draft.pkg,
                            onChange = { draft = draft.copy(pkg = it) },
                            label = stringResource(R.string.intent_package),
                            onBrowse = { sheet = IntentSheet.APP }
                        )
                        IntentFieldCard(
                            value = draft.cls,
                            onChange = { draft = draft.copy(cls = it) },
                            label = stringResource(R.string.intent_class),
                            onBrowse = {
                                if (draft.pkg.isBlank()) {
                                    toast(context.getString(R.string.intent_error_package))
                                } else {
                                    sheet = IntentSheet.CLASS
                                }
                            }
                        )
                    }

                    val availableFlags = intentFlagsFor(draft.target)
                    if (availableFlags.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.intent_flags))
                        val selectedFlags = availableFlags.filter { draft.flags and it.value != 0 }
                        SegmentedCard(
                            index = 0,
                            count = 1,
                            onClick = {
                                haptics.performLightTap(view)
                                sheet = IntentSheet.FLAGS
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
                                    text = if (selectedFlags.isEmpty()) {
                                        stringResource(R.string.not_set)
                                    } else {
                                        selectedFlags.joinToString(", ") { it.name }
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SectionLabel(stringResource(R.string.intent_extras))

                    draft.extras.forEachIndexed { index, extra ->
                        ExtraCard(
                            extra = extra,
                            onChange = { updated ->
                                draft = draft.copy(
                                    extras = draft.extras.toMutableList().also { it[index] = updated }
                                )
                            },
                            onRemove = {
                                haptics.performLightTap(view)
                                draft = draft.copy(
                                    extras = draft.extras.toMutableList().also { it.removeAt(index) }
                                )
                            }
                        )
                    }

                    SegmentedCard(
                        index = 0,
                        count = 1,
                        onClick = {
                            haptics.performLightTap(view)
                            draft = draft.copy(extras = draft.extras + IntentExtra())
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
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.intent_add_extra),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = ::test, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.intent_test))
                    }
                    Button(
                        onClick = ::save,
                        colors = tapbarPrimaryButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.intent_save))
                    }
                }
            }
        }
    }

    when (sheet) {
        IntentSheet.ACTION -> {
            val target = draft.target
            val actions by produceState<List<ChoiceItem>?>(null, target) {
                value = withContext(Dispatchers.IO) { loadActions(context, target) }
            }
            IntentPickerScreen(
                title = stringResource(R.string.intent_action),
                items = actions,
                selected = setOf(draft.action),
                onBack = { sheet = null },
                onPick = {
                    draft = draft.copy(action = it)
                    sheet = null
                }
            )
        }
        IntentSheet.CATEGORY -> IntentPickerScreen(
            title = stringResource(R.string.intent_categories),
            items = remember { commonIntentCategories.map { ChoiceItem(it, readableIntentName(it), it) } },
            selected = draft.categories.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            onBack = { sheet = null },
            onPick = { picked ->
                val existing = draft.categories.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val updated = if (picked in existing) existing - picked else existing + picked
                draft = draft.copy(categories = updated.joinToString("\n"))
            }
        )
        IntentSheet.APP -> {
            val apps by produceState<List<ChoiceItem>?>(null) {
                value = withContext(Dispatchers.IO) { loadApps(context) }
            }
            IntentPickerScreen(
                title = stringResource(R.string.choose_app),
                items = apps,
                selected = setOf(draft.pkg),
                onBack = { sheet = null },
                onPick = { picked ->
                    if (picked != draft.pkg) draft = draft.copy(pkg = picked, cls = "")
                    sheet = null
                }
            )
        }
        IntentSheet.CLASS -> {
            val pkg = draft.pkg.trim()
            val target = draft.target
            var classesVersion by remember { mutableStateOf(0) }
            val classes by produceState<List<ChoiceItem>?>(null, pkg, target, classesVersion) {
                value = withContext(Dispatchers.IO) { loadClasses(context, pkg, target) }
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                classesVersion++
                if (!granted) Toast.makeText(context, R.string.intent_lock_denied, Toast.LENGTH_LONG).show()
            }
            val settingsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { classesVersion++ }
            IntentPickerScreen(
                title = stringResource(R.string.intent_class),
                items = classes,
                selected = setOf(draft.cls),
                onRequestPermission = { permission ->
                    val action = permissionSettingsAction(permission)
                    if (action != null) {
                        settingsLauncher.launch(Intent(action, Uri.parse("package:${context.packageName}")))
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                onBack = { sheet = null },
                onPick = {
                    draft = draft.copy(cls = it)
                    sheet = null
                }
            )
        }
        IntentSheet.FLAGS -> {
            val flags = intentFlagsFor(draft.target)
            IntentPickerScreen(
                title = stringResource(R.string.intent_flags),
                items = remember(flags) { flags.map { ChoiceItem(it.name) } },
                selected = flags.filter { draft.flags and it.value != 0 }.map { it.name }.toSet(),
                onBack = { sheet = null },
                onPick = { picked ->
                    flags.firstOrNull { it.name == picked }?.let { flag ->
                        draft = draft.copy(flags = draft.flags xor flag.value)
                    }
                }
            )
        }
        null -> Unit
    }

    if (confirmDiscard) {
        Dialog(
            onDismissRequest = { confirmDiscard = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = if (LocalImmersive.current != null) Color.Transparent else overlayContainerColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .immersiveSheetBackdrop()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.intent_discard_message),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.intent_discard_desc),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        RestoreDialogButton(
                            text = stringResource(R.string.intent_discard),
                            color = Color(0xFFD71921),
                            index = 0,
                            onClick = {
                                haptics.performLightTap(view)
                                confirmDiscard = false
                                onBack()
                            }
                        )
                        RestoreDialogButton(
                            text = stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.onSurface,
                            index = 1,
                            onClick = {
                                haptics.performLightTap(view)
                                confirmDiscard = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun targetLabel(target: IntentTarget): String = stringResource(
    when (target) {
        IntentTarget.ACTIVITY -> R.string.intent_target_activity
        IntentTarget.BROADCAST -> R.string.intent_target_broadcast
        IntentTarget.SERVICE -> R.string.intent_target_service
    }
)

private fun targetHint(spec: IntentSpec): Int? = when {
    spec.target == IntentTarget.BROADCAST -> R.string.intent_hint_broadcast
    spec.target != IntentTarget.SERVICE -> null
    spec.foreground -> R.string.intent_hint_foreground_service
    else -> R.string.intent_hint_service
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = sectionHeaderStyle(),
        color = sectionHeaderColor(),
        modifier = Modifier
            .sectionHeaderInset()
            .padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun intentFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent
)

@Composable
private fun intentSearchColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

@Composable
private fun IntentField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    capitalize: Boolean = false,
    onBrowse: (() -> Unit)? = null
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) fieldValue = TextFieldValue(value, TextRange(value.length))
    TextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onChange(it.text)
        },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 5,
        colors = intentFieldColors(),
        // Some keyboards ignore autoCorrectEnabled and rewrite "a.b.c" as "A. b. c", so identifiers use the URI keyboard.
        keyboardOptions = if (capitalize) {
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        } else {
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri
            )
        },
        trailingIcon = onBrowse?.let { browse ->
            {
                IconButton(onClick = {
                    haptics.performLightTap(view)
                    browse()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(R.string.intent_browse, label),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun IntentFieldCard(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    capitalize: Boolean = false,
    onBrowse: (() -> Unit)? = null
) {
    SegmentedCard(index = 0, count = 1) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            IntentField(
                value = value,
                onChange = onChange,
                label = label,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                capitalize = capitalize,
                onBrowse = onBrowse
            )
        }
    }
}

@Composable
private fun IntentRadioRow(text: String, isSelected: Boolean, scale: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                role = Role.RadioButton
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(
                if (isSelected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
            ),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun <T> IntentDropdownBlock(
    index: Int,
    count: Int,
    label: String,
    selected: String,
    selectedValue: T,
    options: List<Pair<String, T>>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (T) -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "IntentDropdownArrow"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        SegmentedCard(
            index = index,
            count = count,
            onClick = {
                haptics.performExpandCollapse(view)
                onExpandedChange(!expanded)
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
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { optionIndex, (text, value) ->
                    val isSelected = value == selectedValue
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "IntentDropdownRadioScale"
                    )
                    SegmentedCard(
                        index = index + 1 + optionIndex,
                        count = count,
                        modifier = Modifier.padding(top = 2.dp),
                        onClick = {
                            haptics.performSelection(view)
                            onExpandedChange(false)
                            onSelect(value)
                        }
                    ) {
                        IntentRadioRow(text = text, isSelected = isSelected, scale = scale)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraCard(
    extra: IntentExtra,
    onChange: (IntentExtra) -> Unit,
    onRemove: () -> Unit
) {
    var typeExpanded by remember { mutableStateOf(false) }
    val types = IntentExtraType.entries
    val count = 1 + if (typeExpanded) types.size else 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedCard(index = 0, count = 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IntentField(
                    value = extra.key,
                    onChange = { onChange(extra.copy(key = it)) },
                    label = stringResource(R.string.intent_extra_key),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.intent_remove_extra),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        IntentDropdownBlock(
            index = 0,
            count = count,
            label = stringResource(R.string.intent_extra_type),
            selected = extra.type.label,
            selectedValue = extra.type,
            options = types.map { it.label to it },
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
            onSelect = { onChange(extra.copy(type = it)) }
        )
        IntentFieldCard(
            value = extra.value,
            onChange = { onChange(extra.copy(value = it)) },
            label = stringResource(R.string.intent_extra_value)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassLockButton(reason: String, onRequest: (() -> Unit)?) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(title = { Text(stringResource(R.string.intent_lock_title)) }) {
                Text(reason)
            }
        },
        state = tooltipState,
        enableUserInput = false
    ) {
        IconButton(onClick = {
            haptics.performSelection(view)
            if (onRequest != null) onRequest() else scope.launch { tooltipState.show() }
        }) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = stringResource(
                    if (onRequest != null) R.string.intent_lock_request_cd else R.string.intent_lock_cd
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentPickerScreen(
    title: String,
    items: List<ChoiceItem>?,
    selected: Set<String>,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: (String) -> Unit = {}
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    BackHandler(onBack = {
        haptics.performLightTap(view)
        onBack()
    })
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        items?.filter { it.title.lowercase().contains(q) || it.value.lowercase().contains(q) }
    }

    LaunchedEffect(query) {
        if (query.isNotEmpty() && !filtered.isNullOrEmpty()) {
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
                title = { Text(title) },
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
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(stringResource(R.string.cd_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            haptics.performLightTap(view)
                            query = ""
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
                colors = intentSearchColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            when {
                filtered == null -> SheetMessage(stringResource(R.string.intent_loading), bottomPadding)
                filtered.isEmpty() -> SheetMessage(stringResource(R.string.intent_nothing_found), bottomPadding)
                else -> LazyColumn(
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
                        items = filtered,
                        key = { _, item -> item.value }
                    ) { index, item ->
                        Box(modifier = Modifier.animateItem()) {
                            IntentChoiceRow(
                                item = item,
                                index = index,
                                count = filtered.size,
                                isSelected = item.value in selected,
                                onRequestPermission = onRequestPermission,
                                onClick = {
                                    haptics.performSelection(view)
                                    onPick(item.value)
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
private fun IntentChoiceRow(
    item: ChoiceItem,
    index: Int,
    count: Int,
    isSelected: Boolean,
    onRequestPermission: (String) -> Unit,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "IntentChoiceRadioScale"
    )

    SegmentedCard(index = index, count = count, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.iconPackage?.let { pkg ->
                AppIconImage(pkg)
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item.lockReason?.let { reason ->
                ClassLockButton(reason, item.lockPermission?.let { permission ->
                    { onRequestPermission(permission) }
                })
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
    }
}

@Composable
private fun SheetMessage(text: String, bottomPadding: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val intentIconDispatcher = Dispatchers.IO.limitedParallelism(2)

@Composable
private fun AppIconImage(pkg: String) {
    val context = LocalContext.current
    var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(intentAppIconCache.get(pkg)) }
    if (icon == null) {
        LaunchedEffect(pkg) {
            icon = withContext(intentIconDispatcher) {
                runCatching {
                    context.packageManager.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap()
                }.getOrNull()?.also { intentAppIconCache.put(pkg, it) }
            }
        }
    }
    val loaded = icon
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Spacer(Modifier.size(40.dp))
    }
}