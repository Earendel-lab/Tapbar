package com.earendel.tapbar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

const val GESTURE_TYPE_INTENT = 3

enum class IntentTarget { ACTIVITY, BROADCAST, SERVICE }

enum class IntentExtraType(val label: String) {
    STRING("String"),
    BOOLEAN("Boolean"),
    INT("Integer"),
    LONG("Long"),
    FLOAT("Float"),
    DOUBLE("Double"),
    URI("URI"),
    STRING_ARRAY("String array");

    fun accepts(value: String): Boolean {
        val v = value.trim()
        return when (this) {
            STRING, STRING_ARRAY -> true
            BOOLEAN -> v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true)
            INT -> v.toIntOrNull() != null
            LONG -> v.toLongOrNull() != null
            FLOAT -> v.toFloatOrNull() != null
            DOUBLE -> v.toDoubleOrNull() != null
            URI -> v.isNotEmpty()
        }
    }
}

data class IntentExtra(
    val key: String = "",
    val type: IntentExtraType = IntentExtraType.STRING,
    val value: String = ""
) {
    fun putInto(intent: Intent) {
        val k = key.trim()
        val v = value.trim()
        when (type) {
            IntentExtraType.STRING -> intent.putExtra(k, value)
            IntentExtraType.BOOLEAN -> intent.putExtra(k, v.toBoolean())
            IntentExtraType.INT -> intent.putExtra(k, v.toInt())
            IntentExtraType.LONG -> intent.putExtra(k, v.toLong())
            IntentExtraType.FLOAT -> intent.putExtra(k, v.toFloat())
            IntentExtraType.DOUBLE -> intent.putExtra(k, v.toDouble())
            IntentExtraType.URI -> intent.putExtra(k, Uri.parse(v))
            IntentExtraType.STRING_ARRAY -> intent.putExtra(
                k,
                value.split(',').map { it.trim() }.toTypedArray()
            )
        }
    }
}

data class IntentFlag(val name: String, val value: Int)

// NEW_TASK is left out because build() always sets it: Tapbar starts activities from outside any task.
// CLEAR_WHEN_TASK_RESET is left out because it is the deprecated name for NEW_DOCUMENT's bit.
private val activityOnlyIntentFlags = listOf(
    IntentFlag("BROUGHT_TO_FRONT", Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT),
    IntentFlag("CLEAR_TASK", Intent.FLAG_ACTIVITY_CLEAR_TASK),
    IntentFlag("CLEAR_TOP", Intent.FLAG_ACTIVITY_CLEAR_TOP),
    IntentFlag("EXCLUDE_FROM_RECENTS", Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
    IntentFlag("FORWARD_RESULT", Intent.FLAG_ACTIVITY_FORWARD_RESULT),
    IntentFlag("LAUNCHED_FROM_HISTORY", Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
    IntentFlag("LAUNCH_ADJACENT", Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
    IntentFlag("MATCH_EXTERNAL", Intent.FLAG_ACTIVITY_MATCH_EXTERNAL),
    IntentFlag("MULTIPLE_TASK", Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
    IntentFlag("NEW_DOCUMENT", Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
    IntentFlag("NO_ANIMATION", Intent.FLAG_ACTIVITY_NO_ANIMATION),
    IntentFlag("NO_HISTORY", Intent.FLAG_ACTIVITY_NO_HISTORY),
    IntentFlag("NO_USER_ACTION", Intent.FLAG_ACTIVITY_NO_USER_ACTION),
    IntentFlag("PREVIOUS_IS_TOP", Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP),
    IntentFlag("REORDER_TO_FRONT", Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
    IntentFlag("REQUIRE_DEFAULT", Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT),
    IntentFlag("REQUIRE_NON_BROWSER", Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER),
    IntentFlag("RESET_TASK_IF_NEEDED", Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
    IntentFlag("RETAIN_IN_RECENTS", Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS),
    IntentFlag("SINGLE_TOP", Intent.FLAG_ACTIVITY_SINGLE_TOP),
    IntentFlag("TASK_ON_HOME", Intent.FLAG_ACTIVITY_TASK_ON_HOME)
)

// INCLUDE_BACKGROUND and REGISTERED_ONLY_BEFORE_BOOT are hidden from the SDK, so they have no constants.
private val broadcastOnlyIntentFlags = listOf(
    IntentFlag("RECEIVER_FOREGROUND", Intent.FLAG_RECEIVER_FOREGROUND),
    IntentFlag("RECEIVER_INCLUDE_BACKGROUND", 0x01000000),
    IntentFlag("RECEIVER_NO_ABORT", Intent.FLAG_RECEIVER_NO_ABORT),
    IntentFlag("RECEIVER_REGISTERED_ONLY", Intent.FLAG_RECEIVER_REGISTERED_ONLY),
    IntentFlag("RECEIVER_REGISTERED_ONLY_BEFORE_BOOT", 0x04000000),
    IntentFlag("RECEIVER_REPLACE_PENDING", Intent.FLAG_RECEIVER_REPLACE_PENDING),
    IntentFlag("RECEIVER_VISIBLE_TO_INSTANT_APPS", Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS)
)

private val sharedIntentFlags = listOf(
    IntentFlag("DEBUG_LOG_RESOLUTION", Intent.FLAG_DEBUG_LOG_RESOLUTION),
    IntentFlag("DIRECT_BOOT_AUTO", Intent.FLAG_DIRECT_BOOT_AUTO),
    IntentFlag("EXCLUDE_STOPPED_PACKAGES", Intent.FLAG_EXCLUDE_STOPPED_PACKAGES),
    IntentFlag("FROM_BACKGROUND", Intent.FLAG_FROM_BACKGROUND),
    IntentFlag("GRANT_PERSISTABLE_URI_PERMISSION", Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
    IntentFlag("GRANT_PREFIX_URI_PERMISSION", Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),
    IntentFlag("GRANT_READ_URI_PERMISSION", Intent.FLAG_GRANT_READ_URI_PERMISSION),
    IntentFlag("GRANT_WRITE_URI_PERMISSION", Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
    IntentFlag("INCLUDE_STOPPED_PACKAGES", Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
)

private val activityIntentFlags = activityOnlyIntentFlags + sharedIntentFlags
private val broadcastIntentFlags = broadcastOnlyIntentFlags + sharedIntentFlags

// Activity and receiver flags reuse the same bits, so a flag only means something for one target.
fun intentFlagsFor(target: IntentTarget): List<IntentFlag> = when (target) {
    IntentTarget.ACTIVITY -> activityIntentFlags
    IntentTarget.BROADCAST -> broadcastIntentFlags
    else -> sharedIntentFlags
}

// Shown first for activities, ahead of the full SDK list in assets/intent_actions.
val commonIntentActions = listOf(
    "android.intent.action.VIEW",
    "android.intent.action.SEND",
    "android.intent.action.SENDTO",
    "android.intent.action.EDIT",
    "android.intent.action.DIAL",
    "android.intent.action.MAIN",
    "android.intent.action.ASSIST",
    "android.intent.action.VOICE_COMMAND",
    "android.intent.action.WEB_SEARCH",
    "android.intent.action.SET_ALARM",
    "android.intent.action.SET_TIMER",
    "android.intent.action.SHOW_ALARMS",
    "android.media.action.IMAGE_CAPTURE",
    "android.media.action.STILL_IMAGE_CAMERA",
    "android.media.action.VIDEO_CAMERA",
    "android.settings.SETTINGS",
    "android.settings.WIFI_SETTINGS",
    "android.settings.BLUETOOTH_SETTINGS",
    "android.settings.DISPLAY_SETTINGS",
    "android.settings.SOUND_SETTINGS",
    "android.settings.APPLICATION_SETTINGS",
    "android.settings.LOCATION_SOURCE_SETTINGS"
)

val commonIntentCategories = listOf(
    Intent.CATEGORY_DEFAULT,
    Intent.CATEGORY_BROWSABLE,
    Intent.CATEGORY_LAUNCHER,
    Intent.CATEGORY_HOME,
    Intent.CATEGORY_INFO,
    Intent.CATEGORY_ALTERNATIVE,
    Intent.CATEGORY_APP_BROWSER,
    Intent.CATEGORY_APP_CALCULATOR,
    Intent.CATEGORY_APP_CALENDAR,
    Intent.CATEGORY_APP_CONTACTS,
    Intent.CATEGORY_APP_EMAIL,
    Intent.CATEGORY_APP_GALLERY,
    Intent.CATEGORY_APP_MAPS,
    Intent.CATEGORY_APP_MESSAGING,
    Intent.CATEGORY_APP_MUSIC
)

enum class IntentResult { SENT, NO_HANDLER, DENIED, FAILED }

data class IntentSpec(
    val name: String = "",
    val target: IntentTarget = IntentTarget.ACTIVITY,
    val foreground: Boolean = false,
    val action: String = "",
    val data: String = "",
    val mime: String = "",
    val pkg: String = "",
    val cls: String = "",
    val categories: String = "",
    val flags: Int = 0,
    val extras: List<IntentExtra> = emptyList()
) {

    val displayLabel: String
        get() = name.trim().ifEmpty {
            action.trim().substringAfterLast('.').ifEmpty {
                cls.trim().substringAfterLast('.').ifEmpty { pkg.trim().ifEmpty { data.trim() } }
            }
        }

    private val categoryList: List<String>
        get() = categories.lines().map { it.trim() }.filter { it.isNotEmpty() }

    private val needsPackage: Boolean
        get() = cls.isNotBlank() || target == IntentTarget.SERVICE

    fun problem(context: Context): String? {
        if (action.isBlank() && data.isBlank() && mime.isBlank() && pkg.isBlank() && cls.isBlank()) {
            return context.getString(R.string.intent_error_empty)
        }
        if (needsPackage && pkg.isBlank()) return context.getString(R.string.intent_error_package)
        for (extra in extras) {
            if (extra.key.isBlank()) return context.getString(R.string.intent_error_extra_key)
            if (!extra.type.accepts(extra.value)) {
                return context.getString(R.string.intent_error_extra_value, extra.key.trim())
            }
        }
        return null
    }

    fun build(context: Context): Intent {
        val p = pkg.trim()
        val c = cls.trim()
        val launcherOnly = target == IntentTarget.ACTIVITY && p.isNotEmpty() && c.isEmpty() &&
            action.isBlank() && data.isBlank() && mime.isBlank() && categoryList.isEmpty()
        val launchIntent = if (launcherOnly) context.packageManager.getLaunchIntentForPackage(p) else null
        val intent = launchIntent ?: Intent()

        if (action.isNotBlank()) intent.action = action.trim()
        val uri = data.trim().takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val type = mime.trim().takeIf { it.isNotEmpty() }
        when {
            uri != null && type != null -> intent.setDataAndType(uri, type)
            uri != null -> intent.data = uri
            type != null -> intent.type = type
        }
        categoryList.forEach(intent::addCategory)

        if (launchIntent == null) {
            when {
                c.isNotEmpty() -> intent.setClassName(p, if (c.startsWith(".")) p + c else c)
                p.isNotEmpty() -> intent.setPackage(p)
            }
        }

        val allowedFlags = intentFlagsFor(target).fold(0) { mask, flag -> mask or flag.value }
        intent.addFlags(flags and allowedFlags)
        if (target == IntentTarget.ACTIVITY) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        extras.forEach { it.putInto(intent) }
        return intent
    }

    fun send(context: Context): IntentResult = try {
        val intent = build(context)
        val delivered = when (target) {
            IntentTarget.ACTIVITY -> {
                context.startActivity(intent)
                true
            }
            IntentTarget.BROADCAST -> {
                context.sendBroadcast(intent)
                true
            }
            IntentTarget.SERVICE -> {
                val started = if (foreground) context.startForegroundService(intent) else context.startService(intent)
                started != null
            }
        }
        if (delivered) IntentResult.SENT else IntentResult.NO_HANDLER
    } catch (_: ActivityNotFoundException) {
        IntentResult.NO_HANDLER
    } catch (e: SecurityException) {
        Log.w("Tapbar", "Intent was denied", e)
        IntentResult.DENIED
    } catch (t: Throwable) {
        Log.w("Tapbar", "Could not send intent", t)
        IntentResult.FAILED
    }

    fun toJson(): String = JSONObject()
        .put("name", name)
        .put("target", target.name)
        .put("foreground", foreground)
        .put("action", action)
        .put("data", data)
        .put("mime", mime)
        .put("pkg", pkg)
        .put("cls", cls)
        .put("categories", categories)
        .put("flags", flags)
        .put("extras", JSONArray().also { array ->
            extras.forEach { extra ->
                array.put(
                    JSONObject()
                        .put("key", extra.key)
                        .put("type", extra.type.name)
                        .put("value", extra.value)
                )
            }
        })
        .toString()

    companion object {
        fun fromJson(raw: String?): IntentSpec? {
            if (raw.isNullOrEmpty()) return null
            return runCatching {
                val root = JSONObject(raw)
                val extras = root.optJSONArray("extras")
                IntentSpec(
                    name = root.optString("name"),
                    target = IntentTarget.valueOf(root.getString("target")),
                    foreground = root.optBoolean("foreground"),
                    action = root.optString("action"),
                    data = root.optString("data"),
                    mime = root.optString("mime"),
                    pkg = root.optString("pkg"),
                    cls = root.optString("cls"),
                    categories = root.optString("categories"),
                    flags = root.optInt("flags"),
                    extras = List(extras?.length() ?: 0) { i ->
                        val extra = extras!!.getJSONObject(i)
                        IntentExtra(
                            key = extra.optString("key"),
                            type = IntentExtraType.valueOf(extra.getString("type")),
                            value = extra.optString("value")
                        )
                    }
                )
            }.getOrNull()
        }

        fun resultMessage(result: IntentResult): Int = when (result) {
            IntentResult.SENT -> R.string.intent_sent
            IntentResult.NO_HANDLER -> R.string.intent_failed_no_handler
            IntentResult.DENIED -> R.string.intent_failed_denied
            IntentResult.FAILED -> R.string.intent_failed
        }

        fun run(context: Context, raw: String?) {
            val spec = fromJson(raw)
            val result = when {
                spec == null || spec.problem(context) != null -> IntentResult.FAILED
                else -> spec.send(context)
            }
            if (result != IntentResult.SENT) {
                Toast.makeText(context, resultMessage(result), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
