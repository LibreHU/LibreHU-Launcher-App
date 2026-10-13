package org.librehu.launcher.data

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The car as LibreHU-service describes it (Véhicule tab of the service). */
data class CarInfo(
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val color: String = "",
    val plate: String = "",
    val fuel: String = "",
    /** left or right. */
    val driverSide: String = "left",
) {
    /** "Renault Clio (2019), grey, AB-123-CD": what the SOS screen reads to the emergency services. */
    fun description(): String =
        listOf(
            listOf(make, model).filter { it.isNotBlank() }.joinToString(" ") + (year.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
            color,
            plate,
        ).map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
}

/**
 * The car's name and description from LibreHU-service (`content://org.librehu.service.vehicle/info`, signature
 * permission `org.librehu.permission.VEHICLE`), read and written by the first setup and shown on the SOS screen.
 * [available] is false without the service (or with a service signed with another key).
 */
object VehicleLink {
    private const val TAG = "LibreHU-Vehicle"
    private val URI: Uri = Uri.parse("content://org.librehu.service.vehicle/info")

    private val _info = MutableStateFlow<CarInfo?>(null)
    val info: StateFlow<CarInfo?> = _info.asStateFlow()

    private var observing = false

    fun available(context: Context): Boolean = context.packageManager.resolveContentProvider(URI.authority!!, 0) != null

    /** Reads it now and again on every change. */
    fun start(context: Context) {
        val app = context.applicationContext
        refresh(app)
        if (observing || !available(app)) return
        observing = true
        runCatching {
            app.contentResolver.registerContentObserver(
                URI,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        Thread({ refresh(app) }, "vehicle").start()
                    }
                },
            )
        }.onFailure { Log.w(TAG, "observer: ${it.message}") }
    }

    fun refresh(context: Context) {
        _info.value =
            runCatching {
                context.contentResolver.query(URI, null, null, null, null)?.use { c ->
                    if (!c.moveToFirst()) return@use null

                    fun col(name: String) =
                        c
                            .getColumnIndex(name)
                            .takeIf { it >= 0 }
                            ?.let { c.getString(it) }
                            .orEmpty()
                    CarInfo(
                        col("name"),
                        col("make"),
                        col("model"),
                        col("year"),
                        col("color"),
                        col("plate"),
                        col("fuel"),
                        col("driver_side").ifEmpty { "left" },
                    )
                }
            }.onFailure { Log.i(TAG, "read: ${it.message}") }
                .getOrNull()
    }

    /** Changes some fields (name renames the car everywhere: Bluetooth, hotspot…); false when refused. */
    fun write(
        context: Context,
        values: Map<String, String>,
    ): Boolean =
        runCatching {
            val cv = ContentValues().apply { values.forEach { (k, v) -> put(k, v) } }
            context.contentResolver.update(URI, cv, null, null) > 0
        }.onFailure { Log.w(TAG, "write: ${it.message}") }
            .getOrDefault(false)
}
