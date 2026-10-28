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

    private val _sizes = MutableStateFlow(loadSizes())

    /** Widget sizes set by hand (keys from [Slot.widthKey] / [Slot.heightKey]), used when the layout may be changed. */
    val sizes: StateFlow<Map<String, Float>> = _sizes.asStateFlow()

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

    /** Swaps the widgets of two slots (moves a widget, to an empty slot too). */
    fun swap(
        a: Slot,
        b: Slot,
    ) {
        val wa = _slots.value[a] ?: NO_WIDGET
        val wb = _slots.value[b] ?: NO_WIDGET
        prefs.edit().putInt(a.key, wb).putInt(b.key, wa).apply()
        _slots.value = _slots.value + (a to wb) + (b to wa)
    }

    fun setSize(
        key: String,
        value: Float,
    ) {
        prefs.edit().putFloat(key, value).apply()
        _sizes.value = _sizes.value + (key to value)
    }

    /** Back to the usual widget sizes (the widgets stay where they are). */
    fun resetSizes() {
        prefs.edit().apply { _sizes.value.keys.forEach(::remove) }.apply()
        _sizes.value = emptyMap()
    }

    private fun loadSizes(): Map<String, Float> =
        prefs.all.entries
            .filter { (k, v) -> v is Float && (k.startsWith("w_slot_") || k.startsWith("h_slot_")) }
            .associate { (k, v) -> k to v as Float }

    /** Back to the first-start state: default rail shortcuts, empty widget slots (ids returned to delete). */
    fun reset(): List<Int> {
        val widgetIds = _slots.value.values.filter { it >= 0 }
        prefs.edit().clear().apply()
        _pins.value = loadPins()
        _slots.value = Slot.entries.associateWith { NO_WIDGET }
        _sizes.value = emptyMap()
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

        // Home pages 2 to 4: four widgets each (see Launcher's HomePages).
        P2_1("slot_p2_1"),
        P2_2("slot_p2_2"),
        P2_3("slot_p2_3"),
        P2_4("slot_p2_4"),
        P3_1("slot_p3_1"),
        P3_2("slot_p3_2"),
        P3_3("slot_p3_3"),
        P3_4("slot_p3_4"),
        P4_1("slot_p4_1"),
        P4_2("slot_p4_2"),
        P4_3("slot_p4_3"),
        P4_4("slot_p4_4"),

        // LHU-Link (ambient style): its own two pages of four widgets after the app pages.
        A1("slot_a1"),
        A2("slot_a2"),
        A3("slot_a3"),
        A4("slot_a4"),
        A5("slot_a5"),
        A6("slot_a6"),
        A7("slot_a7"),
        A8("slot_a8"),
        ;

        companion object {
            /** The four slots of home page [page] (2 to 4). */
            fun ofPage(page: Int): List<Slot> = (1..4).map { valueOf("P${page}_$it") }

            /** The slots of the [pages] home pages shown, in order (a widget moves to the previous / next one). */
            /** The LHU-Link widget slots, four by page. */
            val ambient: List<List<Slot>> = listOf(listOf(A1, A2, A3, A4), listOf(A5, A6, A7, A8))

            fun shown(pages: Int): List<Slot> = listOf(MAIN, SIDE) + (2..pages).flatMap { ofPage(it) }

            /** Width of a slot: the main one's against the column beside it, a page's in its row (none: SIDE). */
            fun widthKey(slot: Slot): String? = if (slot == SIDE) null else "w_${slot.key}"

            /** Height: SIDE's in its column, a page slot's row (none: MAIN, full height). */
            fun heightKey(slot: Slot): String? =
                when (slot) {
                    MAIN -> null
                    SIDE -> "h_${slot.key}"
                    else -> "h_${slot.key.dropLast(2)}_r${(slot.key.last() - '1') / 2}"
                }

            fun size(
                sizes: Map<String, Float>,
                key: String?,
            ): Float = sizes[key] ?: if (key == "w_slot_main") 1.5f else 1f
        }
    }

    companion object {
        const val NO_WIDGET = -1
        const val FM_PACKAGE = "org.librehu.fm"
    }
}
