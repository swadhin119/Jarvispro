package com.swadhin.jarvis

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** "Mere notifications padho" ke liye. Notification access on karna padta hai. */
class NotifListener : NotificationListenerService() {

    companion object {
        @Volatile
        var connected = false
        private val recent = ArrayList<String>()

        fun latest(max: Int): List<String> = synchronized(recent) { recent.take(max) }
    }

    override fun onListenerConnected() {
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName || n.isOngoing) return
        val ex = n.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.packageName, 0)).toString()
        } catch (e: Exception) {
            n.packageName
        }
        synchronized(recent) {
            recent.add(0, "$app | $title: ${text.take(160)}")
            while (recent.size > 30) recent.removeAt(recent.size - 1)
        }
    }
}
