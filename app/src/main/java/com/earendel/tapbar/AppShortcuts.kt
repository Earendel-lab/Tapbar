package com.earendel.tapbar

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class AppShortcut(
    val packageName: String,
    val id: String,
    val label: String,
    val intentUri: String
)

data class AppWithShortcuts(
    val packageName: String,
    val appLabel: String,
    val shortcuts: List<AppShortcut>
)

object AppShortcuts {

    @Volatile
    private var cache: List<AppWithShortcuts>? = null

    fun cached(): List<AppWithShortcuts>? = cache

    fun invalidate() {
        cache = null
    }

    fun launch(context: Context, intentUri: String?): Boolean {
        if (intentUri.isNullOrEmpty()) return false
        return try {
            val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
            intent.selector = null
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun loadAll(context: Context, forceRefresh: Boolean = false): List<AppWithShortcuts> {
        if (!forceRefresh) {
            cache?.let { return it }
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val resultMap = LinkedHashMap<String, MutableList<AppShortcut>>()
        val appLabelMap = HashMap<String, String>()
        val stringResCache = HashMap<String, Resources>()
        val locale = AppLabels.appLocale(context)

        for (ri in resolveInfos) {
            val activityInfo = ri.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            val metaData = activityInfo.metaData ?: continue
            val resId = metaData.getInt("android.app.shortcuts")
            if (resId == 0) continue

            val appInfo = activityInfo.applicationInfo ?: continue
            val appRes = try {
                pm.getResourcesForApplication(appInfo)
            } catch (_: Throwable) {
                null
            } ?: continue

            val xml = try {
                appRes.getXml(resId)
            } catch (_: Throwable) {
                null
            } ?: continue

            val appLabel = appLabelMap.getOrPut(pkg) {
                try {
                    AppLabels.label(context, ri, locale)
                } catch (_: Throwable) {
                    pkg
                }
            }

            val stringRes = stringResCache.getOrPut(pkg) {
                AppLabels.packageResources(context, pkg, locale) ?: appRes
            }
            val parsedShortcuts = parseShortcutsXml(context, pm, appRes, stringRes, xml, pkg)
            if (parsedShortcuts.isNotEmpty()) {
                val list = resultMap.getOrPut(pkg) { mutableListOf() }
                list.addAll(parsedShortcuts)
            }
        }

        val resultList = resultMap.mapNotNull { (pkg, shortcuts) ->
            val deduped = shortcuts.distinctBy { it.id }
            if (deduped.isEmpty()) null
            else {
                val label = appLabelMap[pkg] ?: pkg
                AppWithShortcuts(pkg, label, deduped)
            }
        }.sortedBy { it.appLabel.lowercase() }

        cache = resultList
        return resultList
    }

    private fun parseShortcutsXml(
        context: Context,
        pm: PackageManager,
        appRes: Resources,
        stringRes: Resources,
        xml: XmlResourceParser,
        packageName: String
    ): List<AppShortcut> {
        val shortcuts = mutableListOf<AppShortcut>()
        return try {
            var eventType = xml.eventType
            var currentShortcutId: String? = null
            var currentEnabled = true
            var currentShortLabelRes = 0
            var currentShortLabelStr: String? = null
            var currentLongLabelRes = 0
            var currentLongLabelStr: String? = null
            var currentIntentAction: String? = null
            var currentTargetPkg: String? = null
            var currentTargetClass: String? = null
            var currentData: String? = null
            val currentExtras = mutableListOf<Pair<String, Any>>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (xml.name) {
                        "shortcut" -> {
                            currentShortcutId = getAttrValue(xml, "shortcutId")
                            currentEnabled = getAttrBool(xml, "enabled", true)
                            currentShortLabelRes = getAttrResId(xml, "shortcutShortLabel")
                            currentShortLabelStr = getAttrValue(xml, "shortcutShortLabel")
                            currentLongLabelRes = getAttrResId(xml, "shortcutLongLabel")
                            currentLongLabelStr = getAttrValue(xml, "shortcutLongLabel")
                        }
                        "intent" -> {
                            currentIntentAction = getAttrValue(xml, "action")
                            currentTargetPkg = getAttrValue(xml, "targetPackage")
                            currentTargetClass = getAttrValue(xml, "targetClass")
                            currentData = getAttrValue(xml, "data")
                        }
                        "extra" -> {
                            val extraPair = getExtraNameAndValue(appRes, xml)
                            if (extraPair != null) {
                                currentExtras.add(extraPair)
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG && xml.name == "shortcut") {
                    if (currentEnabled && !currentShortcutId.isNullOrEmpty()) {
                        val label = resolveString(stringRes, currentShortLabelRes, currentShortLabelStr)
                            ?: resolveString(stringRes, currentLongLabelRes, currentLongLabelStr)
                        if (!label.isNullOrEmpty()) {
                            val intent = Intent(currentIntentAction ?: Intent.ACTION_VIEW)
                            if (!currentTargetPkg.isNullOrEmpty() && !currentTargetClass.isNullOrEmpty()) {
                                intent.setClassName(currentTargetPkg!!, currentTargetClass!!)
                            } else if (!currentTargetPkg.isNullOrEmpty()) {
                                intent.setPackage(currentTargetPkg)
                            }
                            if (!currentData.isNullOrEmpty()) {
                                intent.data = Uri.parse(currentData)
                            }
                            for ((eName, eVal) in currentExtras) {
                                when (eVal) {
                                    is String -> intent.putExtra(eName, eVal)
                                    is Boolean -> intent.putExtra(eName, eVal)
                                    is Int -> intent.putExtra(eName, eVal)
                                    is Float -> intent.putExtra(eName, eVal)
                                }
                            }

                            if (isExportedAndAllowed(pm, intent, context)) {
                                val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
                                shortcuts.add(AppShortcut(packageName, currentShortcutId!!, label, uri))
                            }
                        }
                    }
                    currentShortcutId = null
                    currentEnabled = true
                    currentShortLabelRes = 0
                    currentShortLabelStr = null
                    currentLongLabelRes = 0
                    currentLongLabelStr = null
                    currentIntentAction = null
                    currentTargetPkg = null
                    currentTargetClass = null
                    currentData = null
                    currentExtras.clear()
                }
                eventType = xml.next()
            }
            shortcuts
        } catch (_: Throwable) {
            shortcuts
        }
    }

    private fun getAttrValue(xml: XmlResourceParser, attrName: String): String? {
        return xml.getAttributeValue("http://schemas.android.com/apk/res/android", attrName)
            ?: xml.getAttributeValue(null, attrName)
    }

    private fun getAttrResId(xml: XmlResourceParser, attrName: String): Int {
        val res = xml.getAttributeResourceValue("http://schemas.android.com/apk/res/android", attrName, 0)
        if (res != 0) return res
        return xml.getAttributeResourceValue(null, attrName, 0)
    }

    private fun getAttrBool(xml: XmlResourceParser, attrName: String, defaultVal: Boolean): Boolean {
        return xml.getAttributeBooleanValue(
            "http://schemas.android.com/apk/res/android",
            attrName,
            xml.getAttributeBooleanValue(null, attrName, defaultVal)
        )
    }

    private fun resolveString(res: Resources, resId: Int, rawStr: String?): String? {
        if (resId != 0) {
            try {
                val str = res.getString(resId)
                if (str.isNotBlank()) return str
            } catch (_: Throwable) {
            }
        }
        if (!rawStr.isNullOrBlank() && !rawStr.startsWith("@")) {
            return rawStr
        }
        return null
    }

    @SuppressLint("ResourceType")
    private fun getExtraNameAndValue(res: Resources, xml: XmlResourceParser): Pair<String, Any>? {
        val attrs = try {
            res.obtainAttributes(
                Xml.asAttributeSet(xml),
                intArrayOf(R.attr.name, R.attr.value)
            )
        } catch (_: Throwable) {
            return null
        }
        return try {
            val name = attrs.getString(0) ?: return null
            val tv = attrs.peekValue(1)
            val value: Any = if (tv != null) {
                when (tv.type) {
                    TypedValue.TYPE_INT_BOOLEAN -> tv.data != 0
                    TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> tv.data
                    TypedValue.TYPE_FLOAT -> Float.fromBits(tv.data)
                    TypedValue.TYPE_STRING -> tv.string?.toString() ?: ""
                    else -> tv.coerceToString()?.toString() ?: ""
                }
            } else {
                attrs.getString(1) ?: ""
            }
            Pair(name, value)
        } catch (_: Throwable) {
            null
        } finally {
            attrs.recycle()
        }
    }

    private fun isExportedAndAllowed(pm: PackageManager, intent: Intent, context: Context): Boolean {
        val resolveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, 0)
            }
        } catch (_: Throwable) {
            null
        } ?: return false

        val activityInfo = resolveInfo.activityInfo ?: return false
        if (!activityInfo.exported) return false

        val requiredPermission = activityInfo.permission
        if (!requiredPermission.isNullOrEmpty()) {
            val granted = context.checkSelfPermission(requiredPermission) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        return true
    }
}
