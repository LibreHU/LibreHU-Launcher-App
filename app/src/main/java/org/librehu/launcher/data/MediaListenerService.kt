package org.librehu.launcher.data

import android.service.notification.NotificationListenerService

/**
 * Empty notification listener: enabling it (Settings > Notification access) is what lets the launcher read the
 * active media sessions for its "now playing" card.
 */
class MediaListenerService : NotificationListenerService()
