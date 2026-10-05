package org.librehu.launcher.sos

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.util.Log

/**
 * Places a call. On a head unit the call goes out through the phone connected in Bluetooth (hands-free profile).
 *
 * - [Intent.ACTION_CALL] cannot call emergency numbers (Android only dials them), and needs CALL_PHONE;
 * - `CALL_PRIVILEGED` (privileged install) calls any number, emergency ones included;
 * - otherwise the dialer opens with the number, one more tap to call.
 */
object SosCaller {
    private const val ACTION_CALL_PRIVILEGED = "android.intent.action.CALL_PRIVILEGED"
    private const val PERMISSION_CALL_PRIVILEGED = "android.permission.CALL_PRIVILEGED"

    enum class Result { CALLING, DIALER, FAILED }

    fun call(
        context: Context,
        number: String,
    ): Result {
        val uri = Uri.fromParts("tel", number.trim(), null)
        val emergency =
            runCatching {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.isEmergencyNumber(number.trim())
            }.getOrDefault(false)
        val attempts =
            buildList {
                if (granted(context, PERMISSION_CALL_PRIVILEGED)) add(Intent(ACTION_CALL_PRIVILEGED, uri) to Result.CALLING)
                if (!emergency && granted(context, Manifest.permission.CALL_PHONE)) add(Intent(Intent.ACTION_CALL, uri) to Result.CALLING)
                add(Intent(Intent.ACTION_DIAL, uri) to Result.DIALER)
            }
        for ((intent, result) in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return result
            } catch (e: ActivityNotFoundException) {
                Log.w("LibreHU-SOS", "${intent.action}: ${e.message}")
            } catch (e: SecurityException) {
                Log.w("LibreHU-SOS", "${intent.action}: ${e.message}")
            }
        }
        return Result.FAILED
    }

    private fun granted(
        context: Context,
        permission: String,
    ) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
