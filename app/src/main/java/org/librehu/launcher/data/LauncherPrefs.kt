package org.librehu.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rail shortcuts and widget slots. */
class LauncherPrefs(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _pins = MutableStateFlow(loadPins())
    val pins: StateFlow<List<String>> = _pins.asStateFlow()

    private val _slots = MutableStateFlow(Slot.entries.associateWith { prefs.getInt(it.key, NO_WIDGET) })
    val slots: StateFlow<Map<Slot, Int>> = _slots.asStateFlow()

    /** First start assistant done (or skipped). */
    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(value) = prefs.edit().putBoolean("setup_done", value).apply()

    fun togglePin(key: String) {
        val list = _pins.value.toMutableList()
        if (!list.remove(key)) list += key
        savePins(list)
    }

    fun movePin(
        key: String,
        offset: Int,
    ) {
        val list = _pins.value.toMutableList()
        val i = list.indexOf(key)
        val j = i + offset
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        savePins(list)
    }

    fun setSlot(
        slot: Slot,
        appWidgetId: Int,
    ) {
        prefs.edit().putInt(slot.key, appWidgetId).apply()
        _slots.value = _slots.value + (slot to appWidgetId)
    }

    /** Back to the first-start state: default rail shortcuts, empty widget slots (ids returned to delete). */
    fun reset(): List<Int> {
        val widgetIds = _slots.value.values.filter { it >= 0 }
        prefs.edit().clear().apply()
        _pins.value = loadPins()
        _slots.value = Slot.entries.associateWith { NO_WIDGET }
        return widgetIds
    }

    private fun savePins(list: List<String>) {
        prefs.edit().putString("pins", list.joinToString("\n")).apply()
        _pins.value = list
    }

    private fun loadPins(): List<String> {
        val saved = prefs.getString("pins", null)
        if (saved != null) return saved.split("\n").filter { it.isNotBlank() }
        // First start: navigation, radio, music, phone, like a car dashboard.
        val defaults =
            listOfNotNull(
                AppsRepository.resolve(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))),
                context.packageManager.getLaunchIntentForPackage(FM_PACKAGE)?.component,
                AppsRepository.resolve(context, Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)),
                AppsRepository.resolve(context, Intent(Intent.ACTION_DIAL)),
            ).map(ComponentName::flattenToString).distinct()
        prefs.edit().putString("pins", defaults.joinToString("\n")).apply()
        return defaults
    }

    enum class Slot(
        val key: String,
    ) {
        MAIN("slot_main"),
        SIDE("slot_side"),
    }

    companion object {
        const val NO_WIDGET = -1
        const val FM_PACKAGE = "org.librehu.fm"
    }
}
