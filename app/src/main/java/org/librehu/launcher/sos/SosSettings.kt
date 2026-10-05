package org.librehu.launcher.sos

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SosContact(
    val name: String,
    val number: String,
)

/** Emergency button: number, contacts, where the button shows, and what the rescue needs to know. */
data class SosSettings(
    /** 112: European emergency number, also accepted by GSM phones in most countries. */
    val number: String = "112",
    val contacts: List<SosContact> = emptyList(),
    /** Seconds before the emergency number is called (0: show the screen only, no automatic call). */
    val countdown: Int = 10,
    val onRail: Boolean = true,
    val onDashboard: Boolean = false,
    /** The rail / dashboard button needs a long press (no call by a stray touch). */
    val longPress: Boolean = true,
    val owner: String = "",
    val medical: String = "",
    val vehicle: String = "",
) {
    companion object {
        val COUNTDOWN_CHOICES = listOf(0, 5, 10, 20)
        const val MAX_CONTACTS = 5
    }
}

class SosStore private constructor(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("sos", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SosSettings> = _settings.asStateFlow()

    fun update(transform: (SosSettings) -> SosSettings) {
        val s = transform(_settings.value)
        val contacts = JSONArray()
        s.contacts.forEach { contacts.put(JSONObject().put("name", it.name).put("number", it.number)) }
        prefs
            .edit()
            .putString("number", s.number)
            .putString("contacts", contacts.toString())
            .putInt("countdown", s.countdown)
            .putBoolean("rail", s.onRail)
            .putBoolean("dashboard", s.onDashboard)
            .putBoolean("long_press", s.longPress)
            .putString("owner", s.owner)
            .putString("medical", s.medical)
            .putString("vehicle", s.vehicle)
            .apply()
        _settings.value = s
    }

    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = SosSettings()
    }

    private fun load(): SosSettings {
        val d = SosSettings()
        val contacts =
            runCatching {
                val a = JSONArray(prefs.getString("contacts", "[]"))
                (0 until a.length()).map { a.getJSONObject(it) }.map { SosContact(it.optString("name"), it.optString("number")) }
            }.getOrDefault(emptyList())
        return SosSettings(
            number = prefs.getString("number", null) ?: d.number,
            contacts = contacts,
            countdown = prefs.getInt("countdown", d.countdown),
            onRail = prefs.getBoolean("rail", d.onRail),
            onDashboard = prefs.getBoolean("dashboard", d.onDashboard),
            longPress = prefs.getBoolean("long_press", d.longPress),
            owner = prefs.getString("owner", null) ?: "",
            medical = prefs.getString("medical", null) ?: "",
            vehicle = prefs.getString("vehicle", null) ?: "",
        )
    }

    companion object {
        /** Opens the SOS screen (exported: key mapping apps, LibreHU-service touch keys…). */
        const val ACTION_SOS = "org.librehu.action.SOS"

        @Volatile
        private var instance: SosStore? = null

        fun get(context: Context): SosStore = instance ?: synchronized(this) { instance ?: SosStore(context).also { instance = it } }
    }
}
