package com.sorcerer.obelisk.notif

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sorcerer.obelisk.net.Protocol
import com.sorcerer.obelisk.net.ObeliskSocket
import org.json.JSONObject

class ObeliskNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!ObeliskSocket.isConnected) return
        // Skip our own foreground-service notification and other silent groups.
        if (sbn.packageName == packageName) return
        if (!NotificationFilterStore.isAllowed(this, sbn.packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

        val json = JSONObject()
            .put("type", Protocol.NOTIFICATION)
            .put("key", sbn.key)
            .put("app", appName)
            .put("package", sbn.packageName)
            .put("title", title)
            .put("text", text)
            .put("postTime", sbn.postTime)

        ObeliskSocket.send(json.toString())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!ObeliskSocket.isConnected) return
        val json = JSONObject()
            .put("type", Protocol.NOTIFICATION_REMOVED)
            .put("key", sbn.key)
        ObeliskSocket.send(json.toString())
    }
}