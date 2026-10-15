package com.sorcerer.obelisk.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.sorcerer.obelisk.net.Protocol
import com.sorcerer.obelisk.net.ObeliskSocket
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Exposes the phone's shared storage to the PC. Requires
 * MANAGE_EXTERNAL_STORAGE (Android 11+) to be granted, since this app
 * is a full file-manager-style tool operating outside its own sandbox.
 */
object FileManagerHandler {

    // Raw bytes per chunk before base64 (~683 KB on the wire after encoding).
    // Streaming in fixed-size pieces means there's no upper bound on file
    // size — a 10 KB file and a 10 GB file both transfer the same way,
    // the only difference is how long it takes.
    private const val CHUNK_SIZE = 512 * 1024
    private const val THUMB_TARGET_PX = 160

    // Backpressure cap: pause reading more of the file once this many bytes
    // are queued but not yet flushed to the socket. Without this, disk reads
    // outrun the network by a wide margin — the queue grows unbounded,
    // which is what was causing OOM crashes on large files (and, before
    // that, spurious disconnects once OkHttp's write timeout gave up on a
    // backlog that was never actually going to clear in time).
    private const val MAX_QUEUED_BYTES = 4L * 1024 * 1024
    private const val THROTTLE_POLL_MS = 25L

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    fun defaultRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in imageExtensions

    fun listDir(path: String) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            ObeliskSocket.send(Protocol.error("Not a directory: $path"))
            return
        }
        val files = dir.listFiles()
        if (files == null) {
            ObeliskSocket.send(Protocol.error(
                "Cannot read \"$path\" — grant \"All-files access\" on the phone (see the button on the home screen) and try again."
            ))
            return
        }
        val entries = JSONArray()
        files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
            entries.put(
                JSONObject()
                    .put("name", f.name)
                    .put("isDir", f.isDirectory)
                    .put("size", if (f.isDirectory) 0 else f.length())
                    .put("modified", f.lastModified())
            )
        }
        val response = JSONObject()
            .put("type", Protocol.DIR_LISTING)
            .put("path", dir.absolutePath)
            .put("entries", entries)
        ObeliskSocket.send(response.toString())
    }

    fun getFile(path: String) {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            ObeliskSocket.send(Protocol.error("File not found: $path"))
            return
        }
        // Runs off the socket's own callback thread so a large transfer
        // doesn't delay pings/other messages for its whole duration.
        Thread {
            try {
                val size = file.length()
                val totalChunks = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)

                ObeliskSocket.send(
                    JSONObject()
                        .put("type", Protocol.FILE_META)
                        .put("path", file.absolutePath)
                        .put("name", file.name)
                        .put("size", size)
                        .put("totalChunks", totalChunks)
                        .toString()
                )

                var aborted = false
                file.inputStream().use { input ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    var index = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break

                        if (!waitForSendCapacity()) {
                            Log.w("FileManagerHandler", "Link dropped mid-transfer for ${file.name} at chunk $index")
                            aborted = true
                            break
                        }

                        val chunkBytes = if (read == buffer.size) buffer else buffer.copyOf(read)
                        val b64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
                        ObeliskSocket.send(
                            JSONObject()
                                .put("type", Protocol.FILE_CHUNK)
                                .put("path", file.absolutePath)
                                .put("index", index)
                                .put("data", b64)
                                .toString()
                        )
                        index++
                    }
                }

                if (!aborted) {
                    ObeliskSocket.send(
                        JSONObject().put("type", Protocol.FILE_COMPLETE).put("path", file.absolutePath).toString()
                    )
                }
            } catch (e: Exception) {
                ObeliskSocket.send(Protocol.error("Transfer failed for ${file.name}: ${e.message}"))
            }
        }.start()
    }

    /**
     * Blocks (off the main thread — this always runs inside the transfer's
     * own background Thread) until the socket's outgoing queue has drained
     * below the cap, so disk reads never outrun what the network can
     * actually carry. Returns false if the link dies while waiting, so the
     * caller can abort the transfer instead of spinning forever.
     */
    private fun waitForSendCapacity(): Boolean {
        while (ObeliskSocket.pendingBytes() > MAX_QUEUED_BYTES) {
            if (!ObeliskSocket.isConnected) return false
            Thread.sleep(THROTTLE_POLL_MS)
        }
        return ObeliskSocket.isConnected
    }

    fun getThumbnail(path: String) {
        val file = File(path)
        if (!file.exists() || !file.isFile || !isImage(file.name)) {
            ObeliskSocket.send(Protocol.error("No thumbnail available: $path"))
            return
        }
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= THUMB_TARGET_PX &&
                bounds.outHeight / (sample * 2) >= THUMB_TARGET_PX
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap: Bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: run {
                ObeliskSocket.send(Protocol.error("Could not decode image: ${file.name}"))
                return
            }
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 55, stream)
            bitmap.recycle()
            val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            val response = JSONObject()
                .put("type", Protocol.THUMBNAIL_DATA)
                .put("path", file.absolutePath)
                .put("data", b64)
            ObeliskSocket.send(response.toString())
        } catch (e: Exception) {
            ObeliskSocket.send(Protocol.error("Thumbnail failed for ${file.name}: ${e.message}"))
        }
    }
}