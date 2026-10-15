package com.sorcerer.obelisk.notif

import android.content.Context

/**
 * An empty allow-set means "forward everything" (the default, so the
 * feature is opt-in to filter rather than opt-in to see anything at all).
 * Once the user picks at least one app, only those apps' notifications
 * are forwarded.
 */
object NotificationFilterStore {
    private const val PREFS = "obelisk_notif_filter"
    private const val KEY_ALLOWED = "allowed_packages"

    fun getAllowed(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()
    }

    fun setAllowed(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ALLOWED, packages)
            .apply()
    }

    fun isAllowed(context: Context, packageName: String): Boolean {
        val allowed = getAllowed(context)
        return allowed.isEmpty() || allowed.contains(packageName)
    }
}