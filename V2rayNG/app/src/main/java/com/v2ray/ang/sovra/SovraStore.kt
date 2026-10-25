package com.v2ray.ang.sovra

import android.content.Context

/** Small local store for the SOVRA session. Values never go to logs. */
object SovraStore {
    private const val FILE = "sovra_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_SUB_ID = "group_id"
    private const val KEY_VERSION = "servers_version"
    private const val KEY_FX3D = "fx3d"

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
}
