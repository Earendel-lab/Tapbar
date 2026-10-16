package com.earendel.tapbar

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ParsedBackup(
    val createdAt: Long,
    val values: Map<String, Any>
)

sealed interface BackupParseResult {
    class Valid(val backup: ParsedBackup) : BackupParseResult
    object Invalid : BackupParseResult
    object TooNew : BackupParseResult
    object Unreadable : BackupParseResult
}

object BackupManager {

    const val FORMAT_VERSION = 1

    private const val APP_ID = "com.earendel.tapbar"
    private const val PREFS_NAME = "tapbar_prefs"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_INITIAL_POSITION = "has_initial_pos"
    private const val MAX_BYTES = 1024 * 1024
    private const val MAX_STRING = 8192
    private const val INT_LIMIT = 1_000_000

    private val excluded = setOf(
        "has_seen_privacy_notice",
        "has_completed_language_onboarding",
        "has_completed_perm_onboarding"
    )

    private val refreshMarkers = setOf(
        "labels_locale_basic",
        "labels_locale_shortcuts"
    )

    private val gestures = listOf("single_tap", "double_tap", "triple_tap", "long_press")

    private val gestureFields = mapOf(
        "enabled" to 'b',
        "type" to 'i',
        "action_id" to 's',
        "action_label" to 's',
        "shortcut_uri" to 's',
        "shortcut_label" to 's',
        "target_pkg" to 's',
        "target_label" to 's'
    )

    private val globalTypes: Map<String, Char> = run {
        val map = mutableMapOf<String, Char>()
        for (gesture in gestures) {
            for ((field, type) in gestureFields) map["${gesture}_$field"] = type
        }
        for (zone in 1..ZONE_COUNT) map["zone${zone}_enabled"] = 'b'
        map["double_tap_speed_ms"] = 'i'
        map["pos_x"] = 'i'
        map["pos_y"] = 'i'
        map["zone_w"] = 'i'
        map["zone_h"] = 'i'
        map["tap_haptic_mode"] = 'i'
        map["theme_mode"] = 'i'
        map["auto_start"] = 'b'
        map["colorful_ui"] = 'b'
        map["disable_in_landscape"] = 'b'
        map["haptic_feedback_enabled"] = 'b'
        map["service_enabled"] = 'b'
        map["split_shade"] = 'b'
        map[KEY_INITIAL_POSITION] = 'b'
        map[KEY_LANGUAGE] = 's'
        map["blocked_pkgs_str"] = 's'
        map["target_pkg"] = 's'
        map["target_label"] = 's'
        map
    }

    private val zoneTypes: Map<String, Char> = run {
        val map = mutableMapOf<String, Char>()
        for (gesture in gestures) {
            for ((field, type) in gestureFields) map["${gesture}_$field"] = type
        }
        map["swipe_enabled"] = 'b'
        map["swipe_target"] = 'i'
        for (base in listOf("pos_x", "pos_y", "zone_w", "zone_h")) {
            map[base] = 'i'
            map["land_$base"] = 'i'
        }
        map
    }

    private val zoneKeyRegex = Regex("^z[1-4]_(.+)$")
    private val volumeKeyRegex = Regex("^volume_level_[0-9]{1,2}$")

    private val generationCounter = AtomicInteger(0)

    val generation: Int
        get() = generationCounter.get()

    private fun expectedType(key: String): Char? {
        globalTypes[key]?.let { return it }
        val match = zoneKeyRegex.matchEntire(key) ?: return null
        val base = match.groupValues[1]
        zoneTypes[base]?.let { return it }
        if (volumeKeyRegex.matches(base)) return 'i'
        return null
    }

    private fun intInRange(key: String, value: Int): Boolean {
        if (value < -INT_LIMIT || value > INT_LIMIT) return false
        val base = zoneKeyRegex.matchEntire(key)?.groupValues?.get(1) ?: key
        return when {
            base == "theme_mode" -> value in 0..2
            base == "tap_haptic_mode" -> value in 0..1
            base == "swipe_target" -> value in 0..1
            base == "double_tap_speed_ms" -> value in 100..1000
            base.startsWith("volume_level_") -> value in 0..100
            base.endsWith("_type") -> value in 0..2
            else -> true
        }
    }

    private fun validLanguage(tag: String): Boolean =
        tag == LocaleHelper.SYSTEM || SupportedLanguages.any { it.tag == tag }

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "Tapbar-backup-$stamp.json"
    }

    fun formatTimestamp(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    private fun buildJson(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val values = JSONObject()
        for (entry in sp.all.entries.sortedBy { it.key }) {
            val key = entry.key
            if (key in excluded) continue
            val type = expectedType(key) ?: continue
            val value = entry.value
            when (type) {
                'b' -> if (value is Boolean) values.put(key, value)
                'i' -> if (value is Int) values.put(key, value)
                's' -> if (value is String) values.put(key, value)
            }
        }
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Throwable) {
            ""
        }
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("format", FORMAT_VERSION)
        root.put("appVersion", versionName)
        root.put("createdAt", System.currentTimeMillis())
        root.put("prefs", values)
        return root.toString(2)
    }

    suspend fun writeBackup(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO + NonCancellable) {
            val app = context.applicationContext
            try {
                val bytes = buildJson(app).toByteArray(Charsets.UTF_8)
                val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException()
                stream.use {
                    it.write(bytes)
                    it.flush()
                }
                true
            } catch (_: Throwable) {
                try {
                    DocumentsContract.deleteDocument(app.contentResolver, uri)
                } catch (_: Throwable) {
                }
                false
            }
        }

    suspend fun readBackup(context: Context, uri: Uri): BackupParseResult =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val raw = try {
                readBounded(app, uri)
            } catch (_: Throwable) {
                return@withContext BackupParseResult.Unreadable
            }
            if (raw == null) BackupParseResult.Invalid else parse(raw)
        }

    private fun readBounded(app: Context, uri: Uri): String? {
        val stream = app.contentResolver.openInputStream(uri) ?: throw IOException()
        stream.use {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val read = it.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > MAX_BYTES) return null
            }
            return buffer.toString("UTF-8")
        }
    }

    private fun parse(text: String): BackupParseResult {
        val root = try {
            JSONObject(text.removePrefix("\uFEFF"))
        } catch (_: JSONException) {
            return BackupParseResult.Invalid
        }
        if (root.optString("app") != APP_ID) return BackupParseResult.Invalid
        val format = root.optInt("format", -1)
        if (format < 1) return BackupParseResult.Invalid
        if (format > FORMAT_VERSION) return BackupParseResult.TooNew
        val createdAt = root.optLong("createdAt", 0L)
        if (createdAt <= 0L) return BackupParseResult.Invalid
        val source = root.optJSONObject("prefs") ?: return BackupParseResult.Invalid

        val values = mutableMapOf<String, Any>()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in excluded) continue
            val type = expectedType(key) ?: continue
            val raw = source.opt(key)
            when (type) {
                'b' -> {
                    if (raw !is Boolean) return BackupParseResult.Invalid
                    values[key] = raw
                }
                'i' -> {
                    if (raw !is Int) return BackupParseResult.Invalid
                    if (intInRange(key, raw)) values[key] = raw
                }
                's' -> {
                    if (raw !is String) return BackupParseResult.Invalid
                    if (raw.length > MAX_STRING) return BackupParseResult.Invalid
                    if (key == KEY_LANGUAGE && !validLanguage(raw)) continue
                    values[key] = raw
                }
            }
        }
        if (values.isEmpty()) return BackupParseResult.Invalid
        return BackupParseResult.Valid(ParsedBackup(createdAt, values))
    }

    suspend fun applyBackup(context: Context, backup: ParsedBackup): Boolean =
        withContext(Dispatchers.IO + NonCancellable) {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = sp.edit()
            for (key in sp.all.keys) {
                if (key in excluded) continue
                if (key == KEY_LANGUAGE && !backup.values.containsKey(KEY_LANGUAGE)) continue
                if (expectedType(key) != null || key in refreshMarkers) editor.remove(key)
            }
            for ((key, value) in backup.values) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.putBoolean(KEY_INITIAL_POSITION, true)
            val committed = editor.commit()
            if (committed) generationCounter.incrementAndGet()
            committed
        }
}
