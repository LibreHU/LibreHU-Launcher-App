package org.librehu.launcher.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Export / import of the launcher's settings as one JSON file: every preference file (theme, rail shortcuts, standby
 * clock, SOS, lock, TPMS…) plus the small pictures (TPMS car). Widget slots are not exported: their ids only exist
 * on this unit. The wallpaper is kept when it is at most [MAX_FILE_BYTES].
 */
object SettingsBackup {
    private const val FORMAT = "librehu-launcher-settings"
    private const val VERSION = 1
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024

    private val PREFS = listOf("launcher", "theme", "standby", "sos", "lock", "tpms")

    /** Keys that only make sense on this unit. */
    private val SKIP = setOf("slot_", "widget_")

    fun export(
        context: Context,
        uri: Uri,
    ): Boolean =
        runCatching {
            val root = JSONObject().put("format", FORMAT).put("version", VERSION)
            val prefs = JSONObject()
            for (name in PREFS) {
                val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
                val o = JSONObject()
                for ((k, v) in all) {
                    if (SKIP.any { k.startsWith(it) } || v == null) continue
                    o.put(k, typed(v) ?: continue)
                }
                prefs.put(name, o)
            }
            root.put("prefs", prefs)
            val files = JSONObject()
            context.filesDir.listFiles()?.filter { it.isFile && it.length() in 1..MAX_FILE_BYTES }?.forEach {
                files.put(it.name, Base64.encodeToString(it.readBytes(), Base64.NO_WRAP))
            }
            root.put("files", files)
            context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(1).toByteArray()) } ?: error("no output")
        }.isSuccess

    /** Replaces the settings with the file's; the launcher restarts to load them. Null when done, else the reason. */
    fun import(
        context: Context,
        uri: Uri,
    ): String? =
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return "no input"
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT) return "not a LibreHU Launcher settings file"
            val prefs = root.getJSONObject("prefs")
            for (name in prefs.keys()) {
                if (name !in PREFS) continue
                val o = prefs.getJSONObject(name)
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                // Unit-specific keys (widget slots) stay as they are.
                val keep =
                    context
                        .getSharedPreferences(name, Context.MODE_PRIVATE)
                        .all.keys
                        .filter { k -> SKIP.any { k.startsWith(it) } }
                context
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .all.keys
                    .filter { it !in keep }
                    .forEach { edit.remove(it) }
                for (k in o.keys()) untype(edit, k, o.getJSONObject(k))
                edit.commit()
            }
            val files = root.optJSONObject("files")
            files?.keys()?.forEach { name ->
                if (name.contains('/') || name.startsWith(".")) return@forEach
                File(context.filesDir, name).writeBytes(Base64.decode(files.getString(name), Base64.NO_WRAP))
            }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }

    private fun typed(v: Any): JSONObject? =
        when (v) {
            is Boolean -> JSONObject().put("t", "b").put("v", v)
            is Int -> JSONObject().put("t", "i").put("v", v)
            is Long -> JSONObject().put("t", "l").put("v", v)
            is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
            is String -> JSONObject().put("t", "s").put("v", v)
            is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.filterIsInstance<String>()))
            else -> null
        }

    private fun untype(
        edit: android.content.SharedPreferences.Editor,
        k: String,
        o: JSONObject,
    ) {
        when (o.optString("t")) {
            "b" -> {
                edit.putBoolean(k, o.getBoolean("v"))
            }

            "i" -> {
                edit.putInt(k, o.getInt("v"))
            }

            "l" -> {
                edit.putLong(k, o.getLong("v"))
            }

            "f" -> {
                edit.putFloat(k, o.getDouble("v").toFloat())
            }

            "s" -> {
                edit.putString(k, o.getString("v"))
            }

            "ss" -> {
                val a = o.getJSONArray("v")
                edit.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
            }
        }
    }
}
