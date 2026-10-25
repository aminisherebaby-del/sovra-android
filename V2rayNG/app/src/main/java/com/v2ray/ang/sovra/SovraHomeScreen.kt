package com.v2ray.ang.sovra

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainStatus
import com.v2ray.ang.ui.main.MainUiState
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.main.ServerRowUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Amber = Color(0xFFFBBF24)
private val Card = Color(0xFF0E1627)
private val CardLine = Color(0xFF1E293B)

@Composable
internal fun SovraHomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onOpenAdvanced: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groupId = remember(ui.groups) { SovraStore.groupId(ctx) }
    val groupFlow = remember(groupId) { mainViewModel.serverGroupState(groupId) }
    val group by groupFlow.collectAsStateWithLifecycle()
    val rows = group.rows
    var tab by remember { mutableIntStateOf(0) }
    var fx3d by remember { mutableStateOf(SovraStore.fx3d(ctx)) }
    var askLogout by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(SovraStore.themeMode(ctx)) }
    var iranBypass by remember { mutableStateOf(SovraStore.iranBypass(ctx)) }
    var appCfg by remember { mutableStateOf<SovraAppConfig?>(null) }
    var homeAccount by remember { mutableStateOf<SovraAccount?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val cfg = SovraApi.appConfig()
                SovraStore.saveSupportUrl(ctx, cfg.supportUrl)
                SovraStore.saveAnnouncement(ctx, cfg.announcement)
                withContext(Dispatchers.Main) { appCfg = cfg }
            } catch (e: Exception) { /* app must open without API */ }
        }
    }
    LaunchedEffect(tab) {
        if (tab == 0 || tab == 3) {
            val acc = withContext(Dispatchers.IO) {
                try { SovraStore.token(ctx)?.let { SovraApi.me(it) } } catch (e: Exception) { null }
            }
            if (acc != null) homeAccount = acc
        }
    }

    fun applyIranBypass(on: Boolean) {
        iranBypass = on
        SovraStore.setIranBypass(ctx, on)
        SettingsManager.resetRoutingRulesetsFromPresets(ctx, if (on) RoutingType.WHITE_IRAN else RoutingType.WHITE)
        Toast.makeText(ctx, "مسیریابی ایران به‌روز شد. برای اعمال، یک بار قطع و وصل کن", Toast.LENGTH_LONG).show()
    }

    val selectedRow = rows.firstOrNull { it.guid == ui.selectedGuid }

    LaunchedEffect(tab, groupId) {
        if (tab == 1 && groupId.isNotEmpty()) onAction(MainAction.SelectGroup(groupId))
    }

    LaunchedEffect(rows.size, ui.selectedGuid) {
        if (rows.isNotEmpty() && rows.none { it.guid == ui.selectedGuid }) {
            mainViewModel.updateSelectedGuid(rows.first().guid)
        }
    }

    fun toast(text: String) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }

    fun testAll() {
        if (groupId.isEmpty()) return
        onAction(MainAction.SelectGroup(groupId))
        onAction(MainAction.TestRealAllServers)
    }

    fun pickBest() {
        val best = rows.filter { it.testDelayMillis > 0 }.minByOrNull { it.testDelayMillis }
        if (best == null) {
            toast("اول تست پینگ بزن")
        } else {
            onAction(MainAction.SelectServer(best.guid))
            toast("بهترین سرور انتخاب شد: " + best.remarks)
        }
    }

    fun refreshServers() {
        scope.launch {
            val failure: String? = withContext(Dispatchers.IO) {
                try {
                    SovraSync.sync(ctx, true)
                    null
                } catch (e: SovraApiException) {
                    e.message ?: "خطا"
                } catch (e: Exception) {
                    "خطای غیرمنتظره"
                }
            }
            if (failure == null) {
                onAction(MainAction.RefreshGroups)
                toast("لیست سرورها به‌روز شد")
            } else {
                toast(failure)
                if (!SovraStore.hasToken(ctx)) onLoggedOut()
            }
        }
    }

    fun logout() {
        scope.launch {
            if (ui.isRunning) onAction(MainAction.ToggleService)
            withContext(Dispatchers.IO) {
                val token = SovraStore.token(ctx)
                if (token != null) SovraApi.logout(token)
                val gid = SovraStore.groupId(ctx)
                if (gid.isNotEmpty()) MmkvManager.removeSubscription(gid)
                SovraStore.clearToken(ctx)
                SovraStore.saveGroupId(ctx, "")
                SovraStore.saveVersion(ctx, "")
            }
            onLoggedOut()
        }
    }

    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = if (darkTheme) SovraBg else Color(0xFFEAF1FF),
            bottomBar = {
                NavigationBar(containerColor = SovraSurface) {
                    val items = listOf("خانه" to "🛡️", "سرورها" to "🌐", "پلن‌ها" to "💳", "حساب" to "👤")
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(item.second, fontSize = 20.sp) },
                            label = { Text(item.first) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = SovraCyan,
                                unselectedTextColor = SovraMuted,
                                indicatorColor = Color(0xFF16233B),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                when (tab) {
                    0 -> HomeTab(
                        account = homeAccount,
                        announcement = appCfg?.announcement ?: "",
                        maintenance = appCfg?.maintenance == true,
                        ui = ui,
                        serverName = selectedRow?.remarks,
                        statusLine = statusLine(mainViewModel, ui),
                        fx3d = fx3d,
                        onToggle = { onAction(MainAction.ToggleService) },
                        onTestConnection = { onAction(MainAction.TestCurrentServer) },
                        onAdvanced = onOpenAdvanced,
                    )

                    1 -> ServersTab(
                        rows = rows,
                        selectedGuid = ui.selectedGuid,
                        testing = ui.isTesting,
                        onTest = { testAll() },
                        onCancel = { onAction(MainAction.CancelTesting) },
                        onBest = { pickBest() },
                        onSelect = { guid -> onAction(MainAction.SelectServer(guid)) },
                    )

                    2 -> PlansTab(onServersChanged = {
                        mainViewModel.onAction(MainAction.RefreshGroups)
                    })

                    else -> AccountTab(
                        fx3d = fx3d,
                        onFx3d = { on ->
                            fx3d = on
                            SovraStore.setFx3d(ctx, on)
                        },
                        onRefreshServers = { refreshServers() },
                        onAdvanced = onOpenAdvanced,
                        onLogout = { askLogout = true },
                        themeMode = themeMode,
                        onThemeMode = { mode ->
                            themeMode = mode
                            SovraStore.setThemeMode(ctx, mode)
                        },
                        iranBypass = iranBypass,
                        onIranBypass = { applyIranBypass(it) },
                        onPerApp = {
                            ctx.startActivity(Intent(ctx, PerAppProxyActivity::class.java))
                        },
                        onSupport = {
                            val url = appCfg?.supportUrl?.takeIf { it.isNotBlank() } ?: SovraStore.supportUrl(ctx)
                            if (url.isNotBlank()) {
                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                catch (e: Exception) { toast("لینک پشتیبانی باز نشد") }
                            } else toast("لینک پشتیبانی تنظیم نشده")
                        },
                    )
                }
            }
        }

        if (askLogout) {
            AlertDialog(
                onDismissRequest = { askLogout = false },
                title = { Text("خروج از حساب؟") },
                text = { Text("سرورهای SOVRA از این گوشی پاک میشن. برای ورود دوباره، لینک اشتراکت رو لازم داری.") },
                confirmButton = {
                    TextButton(onClick = {
                        askLogout = false
                        logout()
                    }) { Text("خروج", color = SovraDanger) }
                },
                dismissButton = {
                    TextButton(onClick = { askLogout = false }) { Text("انصراف") }
                },
            )
        }
    }
}

private fun statusLine(vm: MainViewModel, ui: MainUiState): String? = when (ui.status) {
    MainStatus.Disconnected, MainStatus.Connected -> null
    else -> vm.formatStatus(ui.status)
}

@Composable
private fun HomeTab(
    account: SovraAccount?,
    announcement: String,
    maintenance: Boolean,
    ui: MainUiState,
    serverName: String?,
    statusLine: String?,
    fx3d: Boolean,
    onToggle: () -> Unit,
    onTestConnection: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val connected = ui.isRunning
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SOVRA", color = SovraCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onAdvanced) { Text("تنظیمات پیشرفته", color = SovraMuted, fontSize = 13.sp) }
        }
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(280.dp)) {
            SovraShield(connected = connected, fx3d = fx3d)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (connected) "متصل هستی" else "متصل نیستی",
            color = if (connected) SovraGreen else Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(serverName ?: "سروری انتخاب نشده", color = SovraMuted, fontSize = 15.sp)
        if (maintenance) {
            Spacer(Modifier.height(8.dp))
            Text("⚠️ سرویس در حال تعمیره. اتصال فعلی قطع نمیشه، ولی خرید و ورود جدید ممکنه موقتاً بسته باشه.", color = Amber, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        if (announcement.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("📢 " + announcement, color = SovraCyan, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        val acc = account
        if (acc != null) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
                    .border(1.dp, CardLine, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("وضعیت اکانت", color = SovraMuted, fontSize = 13.sp)
                    Text(if (acc.active) "فعال ✅" else "غیرفعال", color = if (acc.active) SovraGreen else SovraDanger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("روز مانده", color = SovraMuted, fontSize = 13.sp)
                    Text(acc.daysLeft?.let { "$it روز" } ?: "—", color = Color.White, fontSize = 13.sp)
                }
                if (acc.quotaBytes > 0L) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("حجم", color = SovraMuted, fontSize = 13.sp)
                        Text(formatBytes(acc.usedBytes) + " از " + formatBytes(acc.quotaBytes), color = Color.White, fontSize = 13.sp)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("حجم", color = SovraMuted, fontSize = 13.sp)
                        Text("نامحدود", color = Color.White, fontSize = 13.sp)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("سرور فعلی", color = SovraMuted, fontSize = 13.sp)
                    Text(serverName ?: "—", color = Color.White, fontSize = 13.sp)
                }
            }
        }
        if (statusLine != null) {
            Spacer(Modifier.height(6.dp))
            Text(statusLine, color = SovraMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onToggle,
            enabled = serverName != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connected) SovraDanger else SovraCyan,
                contentColor = Color(0xFF04202A),
                disabledContainerColor = CardLine,
                disabledContentColor = SovraMuted,
            ),
        ) {
            Text(if (connected) "قطع کن" else "وصل شو", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (connected) {
            TextButton(onClick = onTestConnection) { Text("تست اتصال", color = SovraCyan) }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ServersTab(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    testing: Boolean,
    onTest: () -> Unit,
    onCancel: () -> Unit,
    onBest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "سرورها",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = if (testing) onCancel else onTest,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, SovraCyan),
            ) {
                Text(if (testing) "توقف تست" else "تست پینگ", color = SovraCyan)
            }
            Button(
                onClick = onBest,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SovraCyan,
                    contentColor = Color(0xFF04202A),
                ),
            ) {
                Text("بهترین سرور")
            }
        }
        if (testing) {
            Text(
                "در حال تست پینگ...",
                color = SovraMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        if (rows.isEmpty()) {
            Text(
                "سروری پیدا نشد. از تب حساب، به‌روزرسانی سرورها رو بزن.",
                color = SovraMuted,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows, key = { it.guid }) { row ->
                ServerRow(row, selected = row.guid == selectedGuid, onClick = { onSelect(row.guid) })
            }
        }
    }
}

@Composable
private fun ServerRow(row: ServerRowUiModel, selected: Boolean, onClick: () -> Unit) {
    val delay = row.testDelayMillis
    val delayText = when {
        delay > 0L -> "$delay ms"
        delay < 0L -> "ناموفق"
        else -> "—"
    }
    val delayColor = when {
        delay in 1L..300L -> SovraGreen
        delay in 301L..800L -> Amber
        delay > 800L -> SovraDanger
        delay < 0L -> SovraDanger
        else -> SovraMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(
                width = 1.dp,
                color = if (selected) SovraCyan else CardLine,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.remarks, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(row.typeDescription, color = SovraMuted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(delayText, color = delayColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(pingBars(delay), color = delayColor, fontSize = 11.sp)
        }
    }
}

private fun pingBars(delay: Long): String = when {
    delay in 1L..150L -> "▂▄▆█ عالی"
    delay in 151L..300L -> "▂▄▆ خوب"
    delay in 301L..800L -> "▂▄ متوسط"
    delay > 800L -> "▂ ضعیف"
    delay < 0L -> "ناموفق"
    else -> "تست نشده"
}

@Composable
private fun AccountTab(
    fx3d: Boolean,
    onFx3d: (Boolean) -> Unit,
    onRefreshServers: () -> Unit,
    onAdvanced: () -> Unit,
    onLogout: () -> Unit,
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
    iranBypass: Boolean = true,
    onIranBypass: (Boolean) -> Unit = {},
    onPerApp: () -> Unit = {},
    onSupport: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var account by remember { mutableStateOf<SovraAccount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        val result: Pair<SovraAccount?, String?> = withContext(Dispatchers.IO) {
            try {
                val token = SovraStore.token(ctx)
                    ?: throw SovraApiException("auth_required", "وارد حساب نشدی")
                Pair(SovraApi.me(token), null)
            } catch (e: SovraApiException) {
                Pair(null, e.message ?: "خطا")
            } catch (e: Exception) {
                Pair(null, "خطای غیرمنتظره")
            }
        }
        account = result.first
        error = result.second
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("حساب من", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        val acc = account
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Card)
                .border(1.dp, CardLine, RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                Text("در حال دریافت اطلاعات...", color = SovraMuted)
            } else if (acc == null) {
                Text(error ?: "اطلاعات حساب در دسترس نیست", color = SovraDanger)
                TextButton(onClick = { reload += 1 }) { Text("تلاش دوباره", color = SovraCyan) }
            } else {
                val statusText = when {
                    acc.active -> "فعال"
                    acc.reason == "expired" -> "اشتراک تموم شده"
                    acc.reason == "over_quota" -> "حجم تموم شده"
                    else -> "غیرفعال"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("وضعیت", color = SovraMuted)
                    Text(
                        statusText,
                        color = if (acc.active) SovraGreen else SovraDanger,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("زمان باقی‌مونده", color = SovraMuted)
                    Text(
                        acc.daysLeft?.let { "$it روز" } ?: "بدون انقضا",
                        color = Color.White,
                    )
                }
                if (acc.quotaBytes > 0L) {
                    val fraction = (acc.usedBytes.toFloat() / acc.quotaBytes.toFloat()).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("مصرف", color = SovraMuted)
                        Text(
                            formatBytes(acc.usedBytes) + " از " + formatBytes(acc.quotaBytes),
                            color = Color.White,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(CardLine),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(if (fraction > 0.9f) SovraDanger else SovraCyan),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("حجم", color = SovraMuted)
                        Text("نامحدود", color = Color.White)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onRefreshServers,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SovraCyan),
        ) { Text("به‌روزرسانی سرورها", color = SovraCyan) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Card)
                .border(1.dp, CardLine, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("سپر سه‌بعدی", color = Color.White, fontSize = 16.sp)
                Text(
                    "خاموش کردنش مصرف باتری رو کم می‌کنه",
                    color = SovraMuted,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = fx3d,
                onCheckedChange = onFx3d,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF04202A),
                    checkedTrackColor = SovraCyan,
                ),
            )
        }

        // راهنمایی: این دکمه‌ها از همین صفحه در دسترسن
        OutlinedButton(
            onClick = onSupport,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SovraCyan),
        ) { Text("💬 پشتیبانی تلگرام", color = SovraCyan) }

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card)
                .border(1.dp, CardLine, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("دور زدن سایت‌ها و اپ‌های ایرانی", color = Color.White, fontSize = 16.sp)
                Text("ترافیک ایران مستقیم میره (سریع‌تر و کم‌مصرف‌تر)", color = SovraMuted, fontSize = 12.sp)
            }
            Switch(checked = iranBypass, onCheckedChange = onIranBypass,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF04202A), checkedTrackColor = SovraCyan))
        }

        OutlinedButton(
            onClick = onPerApp,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, CardLine),
        ) { Text("انتخاب اپ‌هایی که از VPN رد بشن", color = SovraMuted) }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card)
                .border(1.dp, CardLine, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("تم برنامه", color = Color.White, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("system" to "خودکار", "dark" to "تیره", "light" to "روشن").forEach { (mode, label) ->
                    Text(label,
                        color = if (themeMode == mode) Color(0xFF04202A) else SovraMuted,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (themeMode == mode) SovraCyan else Card)
                            .border(1.dp, CardLine, RoundedCornerShape(10.dp))
                            .clickable { onThemeMode(mode) }.padding(vertical = 9.dp),
                        textAlign = TextAlign.Center)
                }
            }
            Text("تم روشن/تیره روی صفحه اصلی اعمال میشه؛ بقیه صفحه‌ها تم تیره‌ی نئونی دارن.", color = SovraMuted, fontSize = 11.sp)
        }

        OutlinedButton(
            onClick = onAdvanced,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, CardLine),
        ) { Text("تنظیمات پیشرفته", color = SovraMuted) }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SovraDanger),
        ) { Text("خروج از حساب", color = SovraDanger) }
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1073741824.0
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        String.format(Locale.US, "%.0f MB", bytes / 1048576.0)
    }
}
