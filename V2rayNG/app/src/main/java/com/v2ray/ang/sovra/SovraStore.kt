package com.v2ray.ang.sovra

import android.content.Context

/** Small local store for the SOVRA session. Values never go to logs. */
object SovraStore {
    private const val FILE = "sovra_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_SUB_ID = "group_id"
    private const val KEY_VERSION = "servers_version"
    private const val KEY_FX3D = "fx3d"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_IRAN_BYPASS = "iran_bypass"
    private const val KEY_SUPPORT_URL = "support_url"
    private const val KEY_ANNOUNCEMENT = "announcement"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun token(ctx: Context): String? =
        prefs(ctx).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }

    fun hasToken(ctx: Context): Boolean = token(ctx) != null

    fun saveToken(ctx: Context, token: String) {
        prefs(ctx).edit().putString(KEY_TOKEN, token).apply()
    }

    fun clearToken(ctx: Context) {
        prefs(ctx).edit().remove(KEY_TOKEN).apply()
    }

    fun groupId(ctx: Context): String = prefs(ctx).getString(KEY_SUB_ID, "") ?: ""

    fun saveGroupId(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_SUB_ID, id).apply()
    }

    fun version(ctx: Context): String = prefs(ctx).getString(KEY_VERSION, "") ?: ""

    fun saveVersion(ctx: Context, v: String) {
        prefs(ctx).edit().putString(KEY_VERSION, v).apply()
    }

    /** 3D shield animation. On by default; users can turn it off to save battery. */
    fun fx3d(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_FX3D, true)

    fun setFx3d(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_FX3D, on).apply()
    }

    /** Theme: "system" (default), "dark" or "light". */
    fun themeMode(ctx: Context): String = prefs(ctx).getString(KEY_THEME, "system") ?: "system"

    fun setThemeMode(ctx: Context, mode: String) {
        prefs(ctx).edit().putString(KEY_THEME, mode).apply()
    }

    /** Bypass Iranian sites/apps (direct routing). On by default per product spec. */
    fun iranBypass(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_IRAN_BYPASS, true)

    fun setIranBypass(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_IRAN_BYPASS, on).apply()
    }

    fun supportUrl(ctx: Context): String = prefs(ctx).getString(KEY_SUPPORT_URL, "") ?: ""

    fun saveSupportUrl(ctx: Context, url: String) {
        prefs(ctx).edit().putString(KEY_SUPPORT_URL, url).apply()
    }

    fun announcement(ctx: Context): String = prefs(ctx).getString(KEY_ANNOUNCEMENT, "") ?: ""

    fun saveAnnouncement(ctx: Context, text: String) {
        prefs(ctx).edit().putString(KEY_ANNOUNCEMENT, text).apply()
    }
}
