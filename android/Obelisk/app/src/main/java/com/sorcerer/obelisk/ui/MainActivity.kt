package com.sorcerer.obelisk.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.sorcerer.obelisk.LinkStatus
import com.sorcerer.obelisk.R
import com.sorcerer.obelisk.ObeliskState
import com.sorcerer.obelisk.net.ObeliskService
import com.sorcerer.obelisk.notif.NotificationAppFilterActivity
import com.sorcerer.obelisk.qr.QrScanActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var scanButton: Button

    private lateinit var permRow: View
    private lateinit var notifRow: View
    private lateinit var fileRow: View
    private lateinit var permStatusIcon: ImageView
    private lateinit var notifStatusIcon: ImageView
    private lateinit var fileStatusIcon: ImageView

    private var pendingConnectPayload: JSONObject? = null
    private var glowAnimator: ObjectAnimator? = null

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val raw = result.data?.getStringExtra(QrScanActivity.EXTRA_RESULT_JSON)
            val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json != null) {
                pendingConnectPayload = json
                requestScreenCapturePermission()
            }
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val payload = pendingConnectPayload
        if (result.resultCode == Activity.RESULT_OK && result.data != null && payload != null) {
            startLinkService(payload, result.resultCode, result.data!!)
        } else {
            errorText.text = "Screen sharing permission is required to bind the obelisk."
        }
        pendingConnectPayload = null
    }

    private val runtimePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissionStatuses() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusCard = findViewById(R.id.statusCard)
        errorText = findViewById(R.id.errorText)
        scanButton = findViewById(R.id.scanButton)
        val disconnectButton = findViewById<Button>(R.id.disconnectButton)

        permRow = findViewById(R.id.permRow)
        notifRow = findViewById(R.id.notifRow)
        fileRow = findViewById(R.id.fileRow)
        val notifFilterRow = findViewById<View>(R.id.notifFilterRow)
        permStatusIcon = findViewById(R.id.permStatusIcon)
        notifStatusIcon = findViewById(R.id.notifStatusIcon)
        fileStatusIcon = findViewById(R.id.fileStatusIcon)

        scanButton.setOnClickListener {
            // A stale failure from an earlier attempt shouldn't still be
            // showing while a brand new attempt is underway.
            errorText.text = ""
            ObeliskState.setError(null)
            qrLauncher.launch(Intent(this, QrScanActivity::class.java))
        }

        permRow.setOnClickListener { requestRuntimePermissions() }

        notifRow.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        fileRow.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        notifFilterRow.setOnClickListener {
            startActivity(Intent(this, NotificationAppFilterActivity::class.java))
        }

        disconnectButton.setOnClickListener {
            startService(Intent(this, ObeliskService::class.java).apply {
                action = ObeliskService.ACTION_STOP
            })
        }

        lifecycleScope.launch {
            ObeliskState.status.collect { status ->
                statusText.text = when (status) {
                    LinkStatus.DISCONNECTED -> getString(R.string.status_disconnected)
                    LinkStatus.CONNECTING -> getString(R.string.status_connecting)
                    LinkStatus.CONNECTED -> getString(R.string.status_connected)
                }
                scanButton.isEnabled = status == LinkStatus.DISCONNECTED
                disconnectButton.isEnabled = status != LinkStatus.DISCONNECTED
                setConnectedGlow(status == LinkStatus.CONNECTED)
            }
        }

        lifecycleScope.launch {
            ObeliskState.lastError.collect { err ->
                errorText.text = err ?: ""
            }
        }
    }

    /** A soft breathing glow around the status card while bound to the
     * obelisk — a green stroke plus a slowly pulsing elevation/shadow so
     * the connected state actually feels alive rather than just a label
     * change. */
    private fun setConnectedGlow(active: Boolean) {
        glowAnimator?.cancel()
        val density = resources.displayMetrics.density
        if (active) {
            val glowColor = ContextCompat.getColor(this, R.color.rune_success)
            statusCard.strokeColor = glowColor
            statusCard.strokeWidth = (2 * density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                statusCard.outlineSpotShadowColor = glowColor
                statusCard.outlineAmbientShadowColor = glowColor
            }
            glowAnimator = ObjectAnimator.ofFloat(statusCard, "cardElevation", 6f, 20f).apply {
                duration = 1300
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } else {
            statusCard.strokeColor = ContextCompat.getColor(this, R.color.rune_panel_border)
            statusCard.strokeWidth = (1 * density).toInt()
            statusCard.cardElevation = 6f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val defaultShadow = ContextCompat.getColor(this, android.R.color.black)
                statusCard.outlineSpotShadowColor = defaultShadow
                statusCard.outlineAmbientShadowColor = defaultShadow
            }
        }
    }

    override fun onDestroy() {
        glowAnimator?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Permissions and special-access toggles are granted outside this
        // Activity (system dialogs, Settings screens), so re-check every
        // time the user comes back to it rather than only right after a
        // request callback.
        refreshPermissionStatuses()
    }

    /** Swaps each row's trailing icon between a green check and a red
     * cross so it's obvious at a glance what's still needed. */
    private fun refreshPermissionStatuses() {
        setRowStatus(permStatusIcon, allCorePermissionsGranted())
        setRowStatus(notifStatusIcon, isNotificationAccessGranted())
        setRowStatus(fileStatusIcon, isAllFilesAccessGranted())
    }

    private fun setRowStatus(statusIcon: ImageView, granted: Boolean) {
        statusIcon.setImageResource(if (granted) R.drawable.ic_status_granted else R.drawable.ic_status_not_granted)
    }

    private fun allCorePermissionsGranted(): Boolean {
        val required = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            required.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun isAllFilesAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        runtimePermLauncher.launch(perms.toTypedArray())
    }

    private fun requestScreenCapturePermission() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun startLinkService(payload: JSONObject, resultCode: Int, data: Intent) {
        val ip = payload.optString("ip")
        val port = payload.optInt("port")
        val token = payload.optString("token")
        val wsUrl = "ws://$ip:$port/ws"

        val intent = Intent(this, ObeliskService::class.java).apply {
            putExtra(ObeliskService.EXTRA_WS_URL, wsUrl)
            putExtra(ObeliskService.EXTRA_TOKEN, token)
            putExtra(ObeliskService.EXTRA_DEVICE, Build.MODEL)
            putExtra(ObeliskService.EXTRA_PROJECTION_RESULT_CODE, resultCode)
            putExtra(ObeliskService.EXTRA_PROJECTION_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}