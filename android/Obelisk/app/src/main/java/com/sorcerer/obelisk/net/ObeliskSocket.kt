package com.sorcerer.obelisk.net

import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Process-wide WebSocket client. The phone is always the WS *client*;
 * the PC app is the WS *server*. Kept as a singleton so the foreground
 * service, the notification listener, and the call receiver can all
 * push messages without binding to each other.
 */
object ObeliskSocket {

    private const val TAG = "ObeliskSocket"

    interface Listener {
        fun onAuthOk()
        fun onAuthFail(reason: String?)
        fun onCommand(type: String, json: org.json.JSONObject)
        fun onClosed()
    }

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var listener: Listener? = null
    private var pendingToken: String? = null
    private var pendingDevice: String? = null
    private val preAuthQueue = mutableListOf<String>()

    @Volatile var isConnected: Boolean = false
        private set

    fun setListener(l: Listener?) {
        listener = l
    }

    fun connect(url: String, token: String, deviceName: String) {
        disconnect()
        pendingToken = token
        pendingDevice = deviceName

        val httpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            // Large file transfers can legitimately take a while to flush
            // over a real (possibly congested) LAN. OkHttp's default 10s
            // write timeout was killing the connection mid-transfer purely
            // because the send queue hadn't drained yet, not because
            // anything was actually broken.
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        client = httpClient

        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Socket opened, sending auth")
                webSocket.send(Protocol.auth(pendingToken ?: "", pendingDevice ?: "unknown"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                listener?.onClosed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Socket failure: ${t.message}")
                isConnected = false
                listener?.onClosed()
            }
        })
    }

    private fun handleIncoming(text: String) {
        val json = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return
        when (val type = json.optString("type")) {
            Protocol.AUTH_OK -> {
                isConnected = true
                flushPreAuthQueue()
                listener?.onAuthOk()
            }
            Protocol.AUTH_FAIL -> {
                isConnected = false
                listener?.onAuthFail(json.optString("reason"))
            }
            Protocol.PING -> send(Protocol.pong())
            else -> listener?.onCommand(type, json)
        }
    }

    /**
     * Anything sent before auth completes (e.g. an error from screen-capture
     * setup, which can happen milliseconds after connect() is called but
     * before the handshake finishes) used to be silently dropped since
     * webSocket.send() before auth would go nowhere useful. Queue it and
     * deliver it as soon as the PC confirms auth instead.
     */
    fun send(text: String): Boolean {
        if (!isConnected) {
            Log.w(TAG, "send() before auth complete, queueing: $text")
            preAuthQueue.add(text)
            return false
        }
        return webSocket?.send(text) ?: false
    }

    private fun flushPreAuthQueue() {
        if (preAuthQueue.isEmpty()) return
        val toSend = preAuthQueue.toList()
        preAuthQueue.clear()
        toSend.forEach { webSocket?.send(it) }
    }

    /**
     * Bytes queued to be written but not yet flushed to the socket. A fast
     * producer (like a file-chunk reader) should watch this and pause
     * rather than piling up an unbounded backlog in memory — that backlog
     * is exactly what was causing OOM crashes on large transfers.
     */
    fun pendingBytes(): Long = webSocket?.queueSize() ?: 0L

    fun disconnect() {
        webSocket?.close(1000, "bye")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        isConnected = false
        preAuthQueue.clear()
    }
}