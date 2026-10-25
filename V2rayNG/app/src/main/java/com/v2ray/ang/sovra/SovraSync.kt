package com.v2ray.ang.sovra

import android.content.Context
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils

/** Pulls the account's servers from the SOVRA API into the app's own server list (group "SOVRA"). */
object SovraSync {
    private const val GROUP_NAME = "SOVRA"

    /**
     * Blocking: call from a background thread. Returns the number of servers.
     * The stored list is replaced only after the new list was fetched and parsed successfully.
     */
    fun sync(ctx: Context, force: Boolean = false): Int {
        val token = SovraStore.token(ctx)
            ?: throw SovraApiException("auth_required", "وارد حساب نشدی")
        val servers = try {
            SovraApi.servers(token)
        } catch (e: SovraApiException) {
            if (e.code == "session_expired" || e.code == "auth_required") {
                SovraStore.clearToken(ctx)
            }
            throw e
        }

        var groupId = SovraStore.groupId(ctx)
        val groupExists = groupId.isNotEmpty() && MmkvManager.decodeSubscription(groupId) != null
        if (!force && groupExists && servers.version == SovraStore.version(ctx)) {
            return servers.configs.size
        }
        if (!groupExists) {
            groupId = Utils.getUuid()
            MmkvManager.encodeSubscription(groupId, SubscriptionItem(remarks = GROUP_NAME))
            SovraStore.saveGroupId(ctx, groupId)
        }

        val text = servers.configs.joinToString("\n") { it.link }
        val result = AngConfigManager.importBatchConfig(text, groupId, false)
        if (result.first <= 0) {
            throw SovraApiException("import_failed", "کانفیگ‌های سرور خونده نشد")
        }
        SovraStore.saveVersion(ctx, servers.version)

        // Keep the selection valid after every sync: if the previously selected
        // server is not in the fresh SOVRA group (removed/rotated configs),
        // fall back to the first available server instead of a dangling guid.
        val groupServers = MmkvManager.decodeServerList(groupId)
        val currentSelection = MmkvManager.getSelectServer()
        if (currentSelection.isNullOrEmpty() || currentSelection !in groupServers) {
            val first = groupServers.firstOrNull()
            if (first != null) MmkvManager.setSelectServer(first)
        }
        return result.first
    }
}
