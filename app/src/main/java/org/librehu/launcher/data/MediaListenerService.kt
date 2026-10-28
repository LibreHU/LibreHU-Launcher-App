package org.librehu.launcher.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification listener: enabling it (Settings > Notification access) is what lets the launcher read the active media
 * sessions for its "now playing" card. It also counts the notifications waiting (the envelope of the factory style's
 * status bar).
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() = count()

    override fun onListenerDisconnected() {
        _waiting.value = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = count()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = count()

    /** Notifications a person can clear (not the ongoing ones: media, services…). */
    private fun count() {
        _waiting.value = runCatching { activeNotifications.count { it.isClearable && !it.isOngoing } }.getOrDefault(0)
    }

    companion object {
        private val _waiting = MutableStateFlow(0)
        val waiting: StateFlow<Int> = _waiting.asStateFlow()
    }
}
