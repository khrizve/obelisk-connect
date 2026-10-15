package com.sorcerer.obelisk.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.graphics.createBitmap
import com.sorcerer.obelisk.net.Protocol
import com.sorcerer.obelisk.net.ObeliskSocket
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Captures the device screen via MediaProjection and streams it as a
 * sequence of JPEG frames (base64) over the WebSocket. This is a
 * "screenshot stream" rather than an H.264 video feed — simpler, more
 * robust across devices, and plenty smooth at 6-10 fps on a LAN.
 */
class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    private var frameWidth = 0
    private var frameHeight = 0
    private var lastSentAt = 0L
    private var paused = true
    private val minFrameIntervalMs = 110L // ~9 fps cap

    fun initProjection(resultCode: Int, data: Intent) {
        if (mediaProjection != null) {
            Log.i("ScreenCapture", "initProjection: already have a projection, ignoring re-init")
            return
        }
        try {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, data)
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i("ScreenCapture", "MediaProjection stopped by the system")
                    stop()
                    mediaProjection = null
                    ObeliskSocket.send(Protocol.error(
                        "Screen sharing permission was revoked on the phone. Disconnect and reconnect to grant it again."
                    ))
                }
            }, handlerForCallbacks())
            mediaProjection = projection
        } catch (e: Exception) {
            Log.e("ScreenCapture", "initProjection failed: ${e.message}")
            ObeliskSocket.send(Protocol.error("Could not obtain screen capture permission: ${e.message}"))
        }
    }

    private fun handlerForCallbacks(): Handler {
        return Handler(context.mainLooper)
    }

    fun start() {
        val projection = mediaProjection ?: run {
            Log.w("ScreenCapture", "No MediaProjection - cannot start")
            ObeliskSocket.send(Protocol.error(
                "No screen capture permission on file. Disconnect and reconnect to grant it."
            ))
            return
        }

        if (virtualDisplay != null) {
            // Android 14+ throws if createVirtualDisplay() is called a second
            // time on the same MediaProjection instance, so a Stop→Start
            // toggle must reuse the existing display rather than recreate
            // it. Just resume frame delivery.
            paused = false
            return
        }

        val metrics = currentDisplayMetrics() ?: return

        val (w, h) = downscaledDimensions(metrics)
        frameWidth = w
        frameHeight = h

        handlerThread = HandlerThread("RuneScreenCapture").also { it.start() }
        handler = Handler(handlerThread!!.looper)
        paused = false

        try {
            val reader = ImageReader.newInstance(frameWidth, frameHeight, android.graphics.PixelFormat.RGBA_8888, 2)
            imageReader = reader

            virtualDisplay = projection.createVirtualDisplay(
                "ObeliskCapture",
                frameWidth, frameHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to create virtual display: ${e.message}")
            ObeliskSocket.send(Protocol.error(
                "Screen capture permission expired — disconnect and reconnect to re-grant it."
            ))
            stop()
            return
        }

        imageReader!!.setOnImageAvailableListener(imageAvailableListener(), handler)
        registerOrientationListener()
    }

    /**
     * Android doesn't automatically resize an existing VirtualDisplay when
     * the screen rotates. It just keeps compositing the new, differently
     * shaped screen into the old fixed-size capture. That's the small
     * letterboxed rectangle this fix addresses. VirtualDisplay.resize()
     * plus swapping in a new-sized ImageReader is the correct way to
     * handle this. Crucially, it avoids ever calling createVirtualDisplay()
     * again, which Android 14+ forbids doing twice on the same
     * MediaProjection.
     */
    private fun registerOrientationListener() {
        if (displayListener != null) return // already watching

        displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != android.view.Display.DEFAULT_DISPLAY) return
                handleOrientationChange()
            }
        }
        displayListener = listener
        displayManager?.registerDisplayListener(listener, handler)
    }

    private fun handleOrientationChange() {
        if (virtualDisplay == null) return // not currently capturing
        val metrics = currentDisplayMetrics() ?: return
        val (newWidth, newHeight) = downscaledDimensions(metrics)

        if (newWidth == frameWidth && newHeight == frameHeight) return // no real change

        try {
            val oldReader = imageReader
            val newReader = ImageReader.newInstance(newWidth, newHeight, android.graphics.PixelFormat.RGBA_8888, 2)

            frameWidth = newWidth
            frameHeight = newHeight

            virtualDisplay?.resize(newWidth, newHeight, metrics.densityDpi)
            virtualDisplay?.surface = newReader.surface
            newReader.setOnImageAvailableListener(imageAvailableListener(), handler)

            imageReader = newReader
            oldReader?.close()

            Log.i("ScreenCapture", "Resized capture to ${newWidth}x$newHeight after rotation")
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to resize after rotation: ${e.message}")
        }
    }

    private fun currentDisplayMetrics(): DisplayMetrics? {
        return try {
            val metrics = DisplayMetrics()
            // IMPORTANT: WindowManager#defaultDisplay throws
            // UnsupportedOperationException when called from a non-visual
            // context (a Service, as opposed to an Activity) on Android 11+.
            // DisplayManager isn't subject to that restriction.
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            metrics
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to read display metrics: ${e.message}")
            ObeliskSocket.send(Protocol.error("Screen capture failed to start: ${e.message}"))
            null
        }
    }

    private fun downscaledDimensions(metrics: DisplayMetrics): Pair<Int, Int> {
        // Downscale for bandwidth: cap the long edge at 720px, whichever
        // axis that currently is — this naturally adapts to both
        // orientations instead of assuming portrait.
        val scale = 720f / maxOf(metrics.widthPixels, metrics.heightPixels)
        val w = (metrics.widthPixels * scale).toInt().coerceAtLeast(2)
        val h = (metrics.heightPixels * scale).toInt().coerceAtLeast(2)
        return w to h
    }

    private fun imageAvailableListener(): ImageReader.OnImageAvailableListener {
        return ImageReader.OnImageAvailableListener { reader ->
            if (paused) {
                reader.acquireLatestImage()?.close()
                return@OnImageAvailableListener
            }
            val now = System.currentTimeMillis()
            if (now - lastSentAt < minFrameIntervalMs) {
                reader.acquireLatestImage()?.close()
                return@OnImageAvailableListener
            }
            val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
            try {
                lastSentAt = now
                val bitmap = imageToBitmap(image)
                sendFrame(bitmap)
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e("ScreenCapture", "frame error: ${e.message}")
            } finally {
                image.close()
            }
        }
    }

    /** Pauses frame delivery without tearing down the virtual display, since
     * Android 14+ forbids ever calling createVirtualDisplay() again on the
     * same MediaProjection instance. */
    fun stop() {
        paused = true
    }

    /** Fully tears everything down — call only when the link session ends. */
    fun release() {
        paused = true
        displayListener?.let { displayManager?.unregisterDisplayListener(it) }
        displayListener = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * frameWidth

        val bitmap = createBitmap(frameWidth + rowPadding / pixelStride, frameHeight)
        bitmap.copyPixelsFromBuffer(buffer)
        return if (bitmap.width != frameWidth) {
            Bitmap.createBitmap(bitmap, 0, 0, frameWidth, frameHeight)
        } else bitmap
    }

    private fun sendFrame(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 45, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        val json = JSONObject()
            .put("type", Protocol.FRAME)
            .put("w", frameWidth)
            .put("h", frameHeight)
            .put("data", b64)
        ObeliskSocket.send(json.toString())
    }
}