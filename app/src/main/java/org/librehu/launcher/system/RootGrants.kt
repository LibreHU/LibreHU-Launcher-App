package org.librehu.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Root for every LibreHU app at once (first start assistant). SuperSU grants root app by app, and only the app itself
 * can ask: each LibreHU app that uses root has a `RootRequestReceiver` that runs `su -c id` on
 * `org.librehu.action.REQUEST_ROOT` and answers `org.librehu.action.ROOT_RESULT`. [requestAll] asks them one after
 * the other (the launcher itself first), so the SuperSU prompts come one by one; each is granted with "Remember" once
 * for good.
 */
object RootGrants {
    const val ACTION_REQUEST = "org.librehu.action.REQUEST_ROOT"
    const val ACTION_RESULT = "org.librehu.action.ROOT_RESULT"

    /** LibreHU apps that use root: package → name shown. */
    val APPS =
        listOf(
            "org.librehu.launcher" to "LibreHU Launcher",
            "org.librehu.service" to "LibreHU-service",
            "org.librehu.ota" to "LibreHU Apps",
            "org.librehu.fm" to "Radio FM",
            "com.llsl.viper4android" to "ViPER4Android",
            "com.dokt.jacmcu" to "MCU Toolkit",
            "org.librehu.headunit" to "Android Auto",
        )

    enum class Result { WAITING, ASKING, GRANTED, DENIED, NO_ANSWER, NOT_INSTALLED }

    /** Asks each app in turn; [onUpdate] gets each app's result as it comes. */
    suspend fun requestAll(
        context: Context,
        onUpdate: (String, Result) -> Unit,
    ) {
        val app = context.applicationContext
        for ((pkg, _) in APPS) {
            if (!installed(app, pkg)) {
                onUpdate(pkg, Result.NOT_INSTALLED)
                continue
            }
            onUpdate(pkg, Result.ASKING)
            val result = if (pkg == app.packageName) if (suSelf()) Result.GRANTED else Result.DENIED else ask(app, pkg)
            onUpdate(pkg, result)
        }
    }

    private fun installed(
        context: Context,
        pkg: String,
    ): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private suspend fun suSelf(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                p.outputStream.close()
                val done = p.waitFor(WAIT_S, TimeUnit.SECONDS)
                if (!done) p.destroy()
                done && p.inputStream.bufferedReader().readText().contains("uid=0")
            }.getOrDefault(false)
        }

    /** One app: its answer, or NO_ANSWER (an older version without the receiver, or SuperSU never answered). */
    private suspend fun ask(
        context: Context,
        pkg: String,
    ): Result {
        val answer = CompletableDeferred<Boolean>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    if (intent.getStringExtra("package") == pkg) answer.complete(intent.getBooleanExtra("granted", false))
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_RESULT), ContextCompat.RECEIVER_EXPORTED)
        return try {
            context.sendBroadcast(Intent(ACTION_REQUEST).setPackage(pkg).putExtra("reply_to", context.packageName))
            when (withTimeoutOrNull(TimeUnit.SECONDS.toMillis(WAIT_S + 10)) { answer.await() }) {
                true -> Result.GRANTED
                false -> Result.DENIED
                null -> Result.NO_ANSWER
            }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private const val WAIT_S = 50L
}
