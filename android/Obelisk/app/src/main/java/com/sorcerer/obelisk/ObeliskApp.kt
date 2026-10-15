package com.sorcerer.obelisk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LinkStatus { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Global, process-wide state so MainActivity, the foreground service,
 * the notification listener, and the call receiver can all report
 * status without a database. Kept intentionally simple.
 */
object ObeliskState {
    private val _status = MutableStateFlow(LinkStatus.DISCONNECTED)
    val status: StateFlow<LinkStatus> = _status

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    fun setStatus(s: LinkStatus) {
        _status.value = s
    }

    fun setError(message: String?) {
        _lastError.value = message
    }
}

class ObeliskApp : Application() {

    companion object {
        const val CHANNEL_ID = "obelisk_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Obelisk Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the link to your PC alive."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
