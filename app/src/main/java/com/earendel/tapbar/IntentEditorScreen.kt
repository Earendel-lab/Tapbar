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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    return actions.map { ChoiceItem(it, it.substringAfterLast('.'), it) }
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
                    else -> prefs.setSingleTapType(zone, GESTURE_TYPE_INTENT)
                }
                onBack()
            }
        }
    }

    BackHandler(onBack = ::leave)

    val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IntentField(
                    value = draft.name,
                    onChange = { draft = draft.copy(name = it) },
                    label = stringResource(R.string.intent_name),
                    capitalize = true
                )

                IntentDropdown(
                    label = stringResource(R.string.intent_target),
                    selected = targetLabel(draft.target),
                    options = IntentTarget.entries.map { targetLabel(it) to it },
                    onSelect = { picked ->
                        if (picked != draft.target) draft = draft.copy(target = picked, flags = 0, foreground = false)
                    }
                )

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

                IntentField(
                    value = draft.action,
                    onChange = { draft = draft.copy(action = it) },
                    label = stringResource(R.string.intent_action),
                    onBrowse = { sheet = IntentSheet.ACTION }
                )
                IntentField(
                    value = draft.data,
                    onChange = { draft = draft.copy(data = it) },
                    label = stringResource(R.string.intent_data)
                )
                IntentField(
                    value = draft.mime,
                    onChange = { draft = draft.copy(mime = it) },
                    label = stringResource(R.string.intent_mime)
                )
                IntentField(
                    value = draft.categories,
                    onChange = { draft = draft.copy(categories = it) },
                    label = stringResource(R.string.intent_categories),
                    singleLine = false,
                    onBrowse = { sheet = IntentSheet.CATEGORY }
                )

                SectionLabel(stringResource(R.string.intent_section_receiver))

                IntentField(
                    value = draft.pkg,
                    onChange = { draft = draft.copy(pkg = it) },
                    label = stringResource(R.string.intent_package),
                    onBrowse = { sheet = IntentSheet.APP }
                )
                IntentField(
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
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedFlags.isEmpty()) {
                                    stringResource(R.string.not_set)
                                } else {
                                    selectedFlags.joinToString(", ") { it.name }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
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

                OutlinedButton(
                    onClick = {
                        haptics.performLightTap(view)
                        draft = draft.copy(extras = draft.extras + IntentExtra())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.intent_add_extra))
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
                Button(onClick = ::save, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.intent_save))
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
            ChoiceSheet(
                title = stringResource(R.string.intent_action),
                items = actions,
                onDismiss = { sheet = null },
                onPick = {
                    draft = draft.copy(action = it)
                    sheet = null
                }
            )
        }
        IntentSheet.CATEGORY -> ChoiceSheet(
            title = stringResource(R.string.intent_categories),
            items = remember { commonIntentCategories.map { ChoiceItem(it, it.substringAfterLast('.'), it) } },
            onDismiss = { sheet = null },
            onPick = { picked ->
                val existing = draft.categories.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (picked !in existing) {
                    draft = draft.copy(categories = (existing + picked).joinToString("\n"))
                }
                sheet = null
            }
        )
        IntentSheet.APP -> {
            val apps by produceState<List<ChoiceItem>?>(null) {
                value = withContext(Dispatchers.IO) { loadApps(context) }
            }
            ChoiceSheet(
                title = stringResource(R.string.choose_app),
                items = apps,
                onDismiss = { sheet = null },
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
            ChoiceSheet(
                title = stringResource(R.string.intent_class),
                items = classes,
                onRequestPermission = { permission ->
                    val action = permissionSettingsAction(permission)
                    if (action != null) {
                        settingsLauncher.launch(Intent(action, Uri.parse("package:${context.packageName}")))
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                onDismiss = { sheet = null },
                onPick = {
                    draft = draft.copy(cls = it)
                    sheet = null
                }
            )
        }
        IntentSheet.FLAGS -> FlagsSheet(
            flags = intentFlagsFor(draft.target),
            selected = draft.flags,
            onChange = { draft = draft.copy(flags = it) },
            onDismiss = { sheet = null }
        )
        null -> Unit
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.intent_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) {
                    Text(stringResource(R.string.intent_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun intentFieldColors() = OutlinedTextFieldDefaults.colors(
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
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 4,
        shape = RoundedCornerShape(GroupLargeCorner),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> IntentDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(GroupLargeCorner),
            colors = intentFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        haptics.performSelection(view)
                        expanded = false
                        onSelect(value)
                    }
                )
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IntentField(
                value = extra.key,
                onChange = { onChange(extra.copy(key = it)) },
                label = stringResource(R.string.intent_extra_key),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.intent_remove_extra),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        IntentDropdown(
            label = stringResource(R.string.intent_extra_type),
            selected = extra.type.label,
            options = IntentExtraType.entries.map { it.label to it },
            onSelect = { onChange(extra.copy(type = it)) }
        )
        IntentField(
            value = extra.value,
            onChange = { onChange(extra.copy(value = it)) },
            label = stringResource(R.string.intent_extra_value)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentSheetFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (LocalImmersive.current != null) Color.Transparent else overlayContainerColor(),
        dragHandle = { if (LocalImmersive.current == null) BottomSheetDefaults.DragHandle() },
        contentWindowInsets = {
            if (LocalImmersive.current != null) WindowInsets(0, 0, 0, 0) else BottomSheetDefaults.windowInsets
        }
    ) {
        Column(
            modifier = Modifier
                .immersiveSheetBackdrop()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (LocalImmersive.current != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
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
private fun ChoiceSheet(
    title: String,
    items: List<ChoiceItem>?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onRequestPermission: (String) -> Unit = {}
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        items?.filter { it.title.lowercase().contains(q) || it.value.lowercase().contains(q) }
    }

    IntentSheetFrame(title = title, onDismiss = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.cd_search)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
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
            colors = intentFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            when {
                filtered == null -> SheetMessage(stringResource(R.string.intent_loading))
                filtered.isEmpty() -> SheetMessage(stringResource(R.string.intent_nothing_found))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filtered, key = { it.value }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.performSelection(view)
                                    onPick(item.value)
                                }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.iconPackage?.let { pkg ->
                                AppIconImage(pkg)
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                item.subtitle?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            item.lockReason?.let { reason ->
                                ClassLockButton(reason, item.lockPermission?.let { permission ->
                                    { onRequestPermission(permission) }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetMessage(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AppIconImage(pkg: String) {
    val context = LocalContext.current
    var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(intentAppIconCache.get(pkg)) }
    if (icon == null) {
        LaunchedEffect(pkg) {
            icon = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap()
                }.getOrNull()?.also { intentAppIconCache.put(pkg, it) }
            }
        }
    }
    val loaded = icon
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, modifier = Modifier.size(36.dp))
    } else {
        Spacer(Modifier.size(36.dp))
    }
}

@Composable
private fun FlagsSheet(
    flags: List<IntentFlag>,
    selected: Int,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticManager.current
    val view = LocalView.current

    IntentSheetFrame(title = stringResource(R.string.intent_flags), onDismiss = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(flags, key = { it.name }) { flag ->
                val checked = selected and flag.value != 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performSelection(view)
                            onChange(selected xor flag.value)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(
                        text = flag.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
