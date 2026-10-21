package org.librehu.launcher.system

import android.content.Context
import java.io.File

/**
 * The root solution of the unit, for the first start assistant: SuperSU and Magisk ask with a prompt for each app,
 * KernelSU (Next) and APatch never ask (allowlist: each app is ticked in the manager's Superuser list), a bare `su`
 * (built into the kernel or the ROM) has nothing to grant.
 */
object RootManager {
    enum class Kind { PROMPT, ALLOWLIST, PLAIN, NONE }

    data class Detected(
        val name: String,
        val pkg: String?,
        val kind: Kind,
    )

    private val MANAGERS =
        listOf(
            Triple("com.rifsxd.ksunext", "KernelSU Next", Kind.ALLOWLIST),
            Triple("me.weishu.kernelsu", "KernelSU", Kind.ALLOWLIST),
            Triple("me.bmax.apatch", "APatch", Kind.ALLOWLIST),
            Triple("com.topjohnwu.magisk", "Magisk", Kind.PROMPT),
            Triple("io.github.vvb2060.magisk", "Magisk Alpha", Kind.PROMPT),
            Triple("io.github.huskydg.magisk", "Kitsune Mask", Kind.PROMPT),
            Triple("eu.chainfire.supersu", "SuperSU", Kind.PROMPT),
            Triple("com.koushikdutta.superuser", "Superuser", Kind.PROMPT),
        )

    /** Files left by root solutions whose manager may be hidden or renamed (readable only on some ROMs). */
    private val MARKERS =
        listOf(
            "/data/adb/ksud" to Triple("KernelSU", null, Kind.ALLOWLIST),
            "/data/adb/ap" to Triple("APatch", null, Kind.ALLOWLIST),
            "/data/adb/magisk" to Triple("Magisk", null, Kind.PROMPT),
            "/sbin/.magisk" to Triple("Magisk", null, Kind.PROMPT),
            "/system/xbin/daemonsu" to Triple("SuperSU", null, Kind.PROMPT),
        )

    private val SU = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/vendor/bin/su")

    fun detect(context: Context): Detected {
        val pm = context.packageManager
        for ((pkg, name, kind) in MANAGERS) {
            if (runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) return Detected(name, pkg, kind)
        }
        for ((path, m) in MARKERS) if (runCatching { File(path).exists() }.getOrDefault(false)) return Detected(m.first, m.second, m.third)
        return if (SU.any { File(it).exists() }) Detected("su", null, Kind.PLAIN) else Detected("", null, Kind.NONE)
    }
}
