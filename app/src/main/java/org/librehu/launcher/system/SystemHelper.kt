package org.librehu.launcher.system

import android.os.IBinder
import android.os.LocaleList

/**
 * Run as root by [RegionSettings] through `app_process` (CLASSPATH = the launcher's APK): Android's language, time
 * zone and clock set with the framework's own calls (AOSP Android 9: `com.android.internal.app.LocalePicker.updateLocales`,
 * `IAlarmManager.setTimeZone` / `setTime`). Root passes their permission checks, so the launcher itself needs no
 * privileged permission (none to add to the privapp-permissions whitelist).
 *
 * Arguments: `locale <tags>` (e.g. `fr-FR`), `timezone <Olson id>` (e.g. `Europe/Paris`), `time <epoch ms>`.
 * Prints `ok` and exits 0 when done.
 */
object SystemHelper {
    @JvmStatic
    fun main(args: Array<String>) {
        val ok =
            try {
                when (args.getOrNull(0)) {
                    "locale" -> {
                        locale(args[1])
                        true
                    }

                    "timezone" -> {
                        alarm("setTimeZone", String::class.java, args[1])
                        true
                    }

                    "time" -> alarm("setTime", java.lang.Long.TYPE, args[1].toLong()) as? Boolean ?: true
                    else -> false
                }
            } catch (e: Throwable) {
                System.err.println("LibreHU SystemHelper: $e")
                false
            }
        println(if (ok) "ok" else "failed")
        System.exit(if (ok) 0 else 1)
    }

    private fun locale(tags: String) {
        val list = LocaleList.forLanguageTags(tags)
        Class
            .forName("com.android.internal.app.LocalePicker")
            .getMethod("updateLocales", LocaleList::class.java)
            .invoke(null, list)
    }

    private fun alarm(
        name: String,
        type: Class<*>,
        value: Any,
    ): Any? {
        val binder =
            Class
                .forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "alarm") as IBinder
        val service =
            Class
                .forName("android.app.IAlarmManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        return Class.forName("android.app.IAlarmManager").getMethod(name, type).invoke(service, value)
    }
}
