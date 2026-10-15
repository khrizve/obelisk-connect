package com.sorcerer.obelisk.net

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sorcerer.obelisk.R
import com.sorcerer.obelisk.ObeliskApp
import com.sorcerer.obelisk.ObeliskState
import com.sorcerer.obelisk.LinkStatus
import com.sorcerer.obelisk.capture.ScreenCaptureManager
import com.sorcerer.obelisk.files.FileManagerHandler
import com.sorcerer.obelisk.ui.MainActivity
import org.json.JSONObject

class ObeliskService : Service(), ObeliskSocket.Listener {

    companion object {
        const val EXTRA_WS_URL = "ws_url"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_DEVICE = "device"
        const val EXTRA_PROJECTION_RESULT_CODE = "proj_result"
        const val EXTRA_PROJECTION_DATA = "proj_data"
        const val NOTIF_ID = 42

        const val ACTION_STOP = "com.sorcerer.obelisk.action.STOP"
    }

    private lateinit var screenCapture: ScreenCaptureManager

    override fun onCreate() {
        super.onCreate()
        screenCapture = ScreenCaptureManager(this)
        ObeliskSocket.setListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val notification = buildNotification("Weaving the link…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            androidx.core.app.ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val wsUrl = intent?.getStringExtra(EXTRA_WS_URL)
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        val device = intent?.getStringExtra(EXTRA_DEVICE) ?: Build.MODEL
        // NOTE: -1 is Activity.RESULT_OK — the normal *success* value — so it
        // can't double as a "this extra was missing" sentinel. That collision
        // meant a successful screen-capture grant was silently skipped here.
        val resultCode = intent?.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val projData: Intent? = intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)

        if (resultCode != Int.MIN_VALUE && projData != null) {
            try {
                screenCapture.initProjection(resultCode, projData)
            } catch (e: Exception) {
                android.util.Log.e("ObeliskService", "initProjection crashed: ${e.message}")
                ObeliskSocket.send(Protocol.error("Screen capture setup failed: ${e.message}"))
            }
        } else {
            android.util.Log.w("ObeliskService", "No projection data in intent (resultCode=$resultCode, data=${projData != null})")
        }

        if (wsUrl != null && token != null) {
            ObeliskState.setStatus(LinkStatus.CONNECTING)
            ObeliskSocket.connect(wsUrl, token, device)
        }

        // Deliberately NOT START_REDELIVER_INTENT: a MediaProjection consent
        // Intent is single-use on Android 14+. If the system kills and
        // restarts this service, redelivering that stale intent and calling
        // getMediaProjection() again would throw a SecurityException.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun stopEverything() {
        screenCapture.release()
        ObeliskSocket.disconnect()
        ObeliskState.setStatus(LinkStatus.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- ObeliskSocket.Listener ----

    override fun onAuthOk() {
        ObeliskState.setStatus(LinkStatus.CONNECTED)
        // A previous attempt's failure message (e.g. an earlier scan whose
        // token happened to rotate out right as it arrived) must not keep
        // showing once a later attempt actually succeeds.
        ObeliskState.setError(null)
        updateNotification("Bound to the Obelisk")
    }

    override fun onAuthFail(reason: String?) {
        ObeliskState.setStatus(LinkStatus.DISCONNECTED)
        ObeliskState.setError(reason ?: "The sigil was rejected by the obelisk.")
        stopEverything()
    }

    override fun onCommand(type: String, json: JSONObject) {
        when (type) {
            Protocol.START_SCREEN -> screenCapture.start()
            Protocol.STOP_SCREEN -> screenCapture.stop()
            Protocol.LIST_DIR -> {
                val path = json.optString("path", "").ifBlank { FileManagerHandler.defaultRoot() }
                FileManagerHandler.listDir(path)
            }
            Protocol.GET_FILE -> {
                val path = json.optString("path", "")
                if (path.isNotBlank()) FileManagerHandler.getFile(path)
            }
            Protocol.GET_THUMBNAIL -> {
                val path = json.optString("path", "")
                if (path.isNotBlank()) FileManagerHandler.getThumbnail(path)
            }
        }
    }

    override fun onClosed() {
        ObeliskState.setStatus(LinkStatus.DISCONNECTED)
        screenCapture.stop()
    }

    // ---- Notification ----

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, ObeliskApp.CHANNEL_ID)
            .setContentTitle("Obelisk")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_rune_notif)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIF_ID, buildNotification(text))
    }
}