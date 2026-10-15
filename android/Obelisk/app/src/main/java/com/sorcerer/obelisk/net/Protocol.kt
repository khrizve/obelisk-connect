package com.sorcerer.obelisk.net

import org.json.JSONObject

/**
 * Obelisk wire protocol.
 *
 * Every message is a single JSON object with a "type" field.
 * The PC app is the WebSocket SERVER. The phone is the CLIENT that
 * dials in after scanning the rotating QR sigil shown on the PC.
 *
 * Phone -> PC:
 *   {"type":"auth", "token":"...", "device":"Pixel 7"}
 *   {"type":"frame", "w":1080, "h":2400, "data":"<base64 jpeg>"}
 *   {"type":"call", "state":"ringing|offhook|idle", "number":"+8801...", "name":"Mom"}
 *   {"type":"notification", "app":"...", "package":"...", "title":"...", "text":"...", "postTime":123, "key":"..."}
 *   {"type":"notification_removed", "key":"..."}
 *   {"type":"dir_listing", "path":"...", "entries":[{"name":"...","isDir":true,"size":0}]}
 *   {"type":"file_meta", "path":"...", "name":"...", "size":123, "totalChunks":5}
 *   {"type":"file_chunk", "path":"...", "index":0, "data":"<base64 chunk>"}
 *   {"type":"file_complete", "path":"..."}
 *   {"type":"thumbnail_data", "path":"...", "data":"<base64 small jpeg>"}
 *   {"type":"error", "message":"..."}
 *
 * PC -> Phone:
 *   {"type":"start_screen"} / {"type":"stop_screen"}
 *   {"type":"list_dir", "path":"/storage/emulated/0"}
 *   {"type":"get_file", "path":"..."}
 *   {"type":"get_thumbnail", "path":"..."}
 *
 * File transfer has no fixed size ceiling: files are streamed in fixed-size
 * chunks (see FileManagerHandler.CHUNK_SIZE) instead of being loaded whole
 * into memory and sent as one base64 blob, so arbitrarily large files are
 * fine — the only cost is time.
 */
object Protocol {
    const val AUTH = "auth"
    const val AUTH_OK = "auth_ok"
    const val AUTH_FAIL = "auth_fail"

    const val START_SCREEN = "start_screen"
    const val STOP_SCREEN = "stop_screen"
    const val FRAME = "frame"

    const val CALL = "call"
    const val NOTIFICATION = "notification"
    const val NOTIFICATION_REMOVED = "notification_removed"

    const val LIST_DIR = "list_dir"
    const val DIR_LISTING = "dir_listing"
    const val GET_FILE = "get_file"
    const val FILE_META = "file_meta"
    const val FILE_CHUNK = "file_chunk"
    const val FILE_COMPLETE = "file_complete"
    const val GET_THUMBNAIL = "get_thumbnail"
    const val THUMBNAIL_DATA = "thumbnail_data"

    const val ERROR = "error"
    const val PING = "ping"
    const val PONG = "pong"

    fun type(json: String): String? = runCatching { JSONObject(json).getString("type") }.getOrNull()

    fun auth(token: String, device: String): String =
        JSONObject().put("type", AUTH).put("token", token).put("device", device).toString()

    fun error(message: String): String =
        JSONObject().put("type", ERROR).put("message", message).toString()

    fun pong(): String = JSONObject().put("type", PONG).toString()
}