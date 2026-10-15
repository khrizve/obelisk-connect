package com.sorcerer.obelisk.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.sorcerer.obelisk.net.Protocol
import com.sorcerer.obelisk.net.ObeliskSocket
import org.json.JSONObject

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        // Many devices (this is a documented platform quirk, not specific
        // to one OEM) deliver TWO broadcasts for the same logical ringing
        // or idle transition — one carrying the caller's number/name, one
        // without it, arriving milliseconds apart. A BroadcastReceiver
        // declared in the manifest gets a fresh instance per delivery, so
        // this coalescing state has to live in the companion object
        // (static), not as instance fields, to survive across those calls.
        private const val COALESCE_WINDOW_MS = 500L
        private val handler = Handler(Looper.getMainLooper())
        private var pendingRunnable: Runnable? = null
        private var pendingState: String? = null
        private var pendingNumber: String = ""
        private var pendingName: String = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!ObeliskSocket.isConnected) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val mappedState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> "ringing"
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "offhook"
            TelephonyManager.EXTRA_STATE_IDLE -> "idle"
            else -> return
        }

        val name = if (number.isNotBlank()) lookupContactName(context, number) ?: "" else ""

        if (mappedState == pendingState) {
            // Same logical event as the one we're already about to send —
            // fold in whichever field this broadcast has that the other
            // didn't, and let the already-scheduled flush handle sending.
            if (number.isNotBlank() && pendingNumber.isBlank()) pendingNumber = number
            if (name.isNotBlank() && pendingName.isBlank()) pendingName = name
            return
        }

        // A genuinely new state: send whatever was pending immediately,
        // then start a fresh short coalescing window for this one.
        flushPending()
        pendingState = mappedState
        pendingNumber = number
        pendingName = name

        val runnable = Runnable { flushPending() }
        pendingRunnable = runnable
        handler.postDelayed(runnable, COALESCE_WINDOW_MS)
    }

    private fun flushPending() {
        val state = pendingState ?: return
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
        pendingState = null

        val json = JSONObject()
            .put("type", Protocol.CALL)
            .put("state", state)
            .put("number", pendingNumber)
            .put("name", pendingName)
        ObeliskSocket.send(json.toString())
    }

    private fun lookupContactName(context: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        }.getOrNull()
    }
}