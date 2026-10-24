package org.librehu.launcher.system

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Language, time zone, date and time of Android from the launcher (first start assistant, Settings → Language and
 * time). Set as root ([SystemHelper] through `app_process`, `settings put global …`); without root the caller opens
 * Android's own screens instead.
 */
object RegionSettings {
    private const val TAG = "LibreHU-Region"
    private val SAFE = Regex("[A-Za-z0-9_+./,-]{1,64}")

    /** Languages offered first (the rest through Android's language screen). */
    val LANGUAGES =
        listOf(
            "fr-FR", "fr-BE", "fr-CH", "fr-CA", "en-GB", "en-US", "de-DE", "es-ES", "it-IT", "pt-PT", "pt-BR",
            "nl-NL", "pl-PL", "ro-RO", "tr-TR", "ru-RU", "ar-MA",
        )

    /** Time zones offered first: France and its neighbours, overseas France, the usual others. */
    val TIME_ZONES =
        listOf(
            "Europe/Paris", "Europe/Brussels", "Europe/Luxembourg", "Europe/Zurich", "Europe/London", "Europe/Lisbon",
            "Europe/Madrid", "Europe/Berlin", "Europe/Rome", "Europe/Amsterdam", "Europe/Warsaw", "Europe/Bucharest",
            "Europe/Istanbul", "Africa/Casablanca", "Africa/Algiers", "Africa/Tunis", "America/Toronto",
            "America/New_York", "America/Guadeloupe", "America/Martinique", "America/Cayenne", "Indian/Reunion",
            "Indian/Mayotte", "Pacific/Noumea", "Pacific/Tahiti",
        )

    /** "Français (France)": the language named in itself. */
    fun languageLabel(tag: String): String {
        val l = Locale.forLanguageTag(tag)
        return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }

    /** "Europe/Paris · UTC+01:00". */
    fun zoneLabel(id: String): String {
        val offset = TimeZone.getTimeZone(id).getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (offset < 0) "-" else "+"
        val a = kotlin.math.abs(offset)
        return "${id.substringAfter('/').replace('_', ' ')} · UTC$sign%02d:%02d".format(a / 60, a % 60)
    }

    fun currentLanguage(): String = Locale.getDefault().toLanguageTag()

    fun currentZone(): String = TimeZone.getDefault().id

    fun autoTime(context: Context): Boolean = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 1) == 1

    fun autoZone(context: Context): Boolean = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1

    fun is24h(context: Context): Boolean = Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24) != "12"

    /** Each call returns false without root (or when Android refused): open Android's screen then. */
    fun setLanguage(
        context: Context,
        tag: String,
    ): Boolean = SAFE.matches(tag) && helper(context, "locale $tag")

    fun setZone(
        context: Context,
        id: String,
    ): Boolean = SAFE.matches(id) && root("settings put global auto_time_zone 0") && helper(context, "timezone $id")

    /** Date and time chosen by hand: automatic time off, else the network would put it back. */
    fun setTime(
        context: Context,
        epochMs: Long,
    ): Boolean = root("settings put global auto_time 0") && helper(context, "time $epochMs")

    fun setAutoTime(on: Boolean): Boolean = root("settings put global auto_time ${if (on) 1 else 0}")

    fun setAutoZone(on: Boolean): Boolean = root("settings put global auto_time_zone ${if (on) 1 else 0}")

    fun set24h(
        context: Context,
        on: Boolean,
    ): Boolean {
        val value = if (on) "24" else "12"
        // WRITE_SETTINGS is enough for this one; root when Android refuses it.
        return try {
            Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, value)
        } catch (e: Exception) {
            root("settings put system time_12_24 $value")
        }
    }

    private fun helper(
        context: Context,
        args: String,
    ): Boolean {
        val apk = context.applicationInfo.sourceDir
        return root("CLASSPATH=$apk app_process /system/bin ${SystemHelper::class.java.name} $args")
    }

    /** [command] as root; its output read on its own thread, so that the 20 s timeout holds even if it hangs. */
    private fun root(command: String): Boolean =
        try {
            val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            p.outputStream.close()
            val out = StringBuilder()
            val reader =
                Thread {
                    try {
                        val text = p.inputStream.bufferedReader().readText()
                        synchronized(out) { out.append(text) }
                    } catch (_: Exception) {
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            val done = p.waitFor(20, TimeUnit.SECONDS)
            if (!done) p.destroy()
            reader.join(if (done) 2_000 else 500)
            val ok = done && p.exitValue() == 0
            if (!ok) Log.w(TAG, "su $command: ${if (done) synchronized(out) { out.toString() } else "timeout"}")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "su $command: ${e.message}")
            false
        }
}
