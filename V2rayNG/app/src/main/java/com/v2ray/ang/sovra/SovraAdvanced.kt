package com.v2ray.ang.sovra

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

/**
 * SOVRA's own advanced settings. Everything here writes the core settings
 * directly (Mmkv) inside the app; no external/legacy screens are used.
 * Most changes apply on the next connect.
 */
@Composable
internal fun SovraAdvancedScreen(
    iranBypass: Boolean,
    onIranBypass: (Boolean) -> Unit,
    onPerApp: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ برگشت", color = SovraCyan) }
            Text("تنظیمات پیشرفته SOVRA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "این تنظیمات مال خود SOVRAئه و روی هسته اتصال اعمال میشه. بیشترشون از اتصال بعدی اثر می‌کنن؛ اگه وصلی، یه بار قطع و وصل کن.",
            color = SovraMuted, fontSize = 12.sp,
        )

        Section("مسیریابی و ایران")
        SwitchRow(
            "دور زدن سایت‌ها و اپ‌های ایرانی",
            "ترافیک ایران مستقیم میره؛ سریع‌تر و کم‌مصرف‌تر",
            iranBypass,
        ) { onIranBypass(it) }
        OutlinedButton(onClick = onPerApp, modifier = Modifier.fillMaxWidth()) {
            Text("انتخاب اپ‌هایی که از VPN رد بشن", color = SovraCyan)
        }
        Text("پریست مسیریابی:", color = SovraMuted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PresetChip("ایران 🇮🇷", Modifier.weight(1f)) {
                SettingsManager.resetRoutingRulesetsFromPresets(ctx, RoutingType.WHITE_IRAN)
                onIranBypass(true); toast("پریست ایران فعال شد")
            }
            PresetChip("سفید", Modifier.weight(1f)) {
                SettingsManager.resetRoutingRulesetsFromPresets(ctx, RoutingType.WHITE)
                toast("پریست سفید فعال شد")
            }
            PresetChip("همه از VPN", Modifier.weight(1f)) {
                SettingsManager.resetRoutingRulesetsFromPresets(ctx, RoutingType.GLOBAL)
                toast("همه ترافیک از VPN میره")
            }
        }
        MmkvSwitch("عبور ترافیک شبکه داخلی (LAN) مستقیم", AppConfig.PREF_VPN_BYPASS_LAN)
        MmkvSwitch("تشخیص دامنه مقصد (Sniffing)", AppConfig.PREF_SNIFFING_ENABLED)
        MmkvSwitch("فقط مسیریابی (بدون تغییر DNS مقصد)", AppConfig.PREF_ROUTE_ONLY_ENABLED)

        Section("سرعت و پایداری هسته")
        MmkvSwitch("شتاب‌دهنده اتصال (Mux)", AppConfig.PREF_MUX_ENABLED)
        MmkvText("هم‌زمانی Mux (پیش‌فرض ۸)", AppConfig.PREF_MUX_CONCURRENCY, "8")
        MmkvSwitch("Fragment (کمک به عبور از محدودیت)", AppConfig.PREF_FRAGMENT_ENABLED)
        MmkvText("بسته‌های Fragment", AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
        MmkvSwitch("موتور تونل سریع (hev-tunnel)", AppConfig.PREF_USE_HEV_TUNNEL)
        MmkvSwitch("IPv6", AppConfig.PREF_IPV6_ENABLED)
        MmkvSwitch("ترجیح IPv6", AppConfig.PREF_PREFER_IPV6)
        MmkvText("MTU تونل (پیش‌فرض ۱۵۰۰)", AppConfig.PREF_VPN_MTU, "1500")

        Section("DNS")
        MmkvSwitch("DNS محلی", AppConfig.PREF_LOCAL_DNS_ENABLED)
        MmkvSwitch("FakeDNS (سرعت بیشتر در باز شدن سایت‌ها)", AppConfig.PREF_FAKE_DNS_ENABLED)
        MmkvText("DNS راه‌دور", AppConfig.PREF_REMOTE_DNS, "")
        MmkvText("DNS داخلی", AppConfig.PREF_DOMESTIC_DNS, "")
        MmkvText("DNS تونل VPN", AppConfig.PREF_VPN_DNS, "")

        Section("تست و نمایش")
        MmkvSwitch("نمایش سرعت در اعلان", AppConfig.PREF_SPEED_ENABLED)
        MmkvText("آدرس تست تأخیر", AppConfig.PREF_DELAY_TEST_URL, "")
        MmkvText("هم‌زمانی تست پینگ واقعی", AppConfig.PREF_REAL_PING_CONCURRENCY, "")

        Section("پروکسی محلی (متخصص‌ها)")
        MmkvSwitch("پروکسی محلی SOCKS", AppConfig.PREF_ENABLE_LOCAL_PROXY)
        MmkvSwitch("اشتراک پروکسی در شبکه (LAN Sharing)", AppConfig.PREF_PROXY_SHARING)
        MmkvText("پورت SOCKS", AppConfig.PREF_SOCKS_PORT, "10808")

        Section("عیب‌یابی")
        MmkvText("سطح لاگ هسته", AppConfig.PREF_LOGLEVEL, "warning")
        Button(
            onClick = { toast("تنظیمات ذخیره‌ست و از اتصال بعدی اعمال میشه") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SovraCyan, contentColor = Color(0xFF04202A)),
        ) { Text("تمام شد ✓", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, color = SovraCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(SovraSurface).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (sub.isNotBlank()) Text(sub, color = SovraMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF04202A), checkedTrackColor = SovraCyan),
        )
    }
}

@Composable
private fun MmkvSwitch(title: String, key: String) {
    var on by remember(key) { mutableStateOf(MmkvManager.decodeSettingsBool(key)) }
    SwitchRow(title, "", on) { v ->
        on = v
        MmkvManager.encodeSettings(key, v)
    }
}

@Composable
private fun MmkvText(label: String, key: String, hint: String) {
    var value by remember(key) { mutableStateOf(MmkvManager.decodeSettingsString(key, "") ?: "") }
    var saved by remember(key) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(SovraSurface).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; saved = false },
            label = { Text(label) },
            placeholder = if (hint.isNotBlank()) ({ Text(hint, color = SovraMuted) }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = SovraCyan, unfocusedBorderColor = Color(0xFF334155),
                cursorColor = SovraCyan, focusedLabelColor = SovraCyan, unfocusedLabelColor = SovraMuted,
            ),
        )
        TextButton(onClick = {
            MmkvManager.encodeSettings(key, value.trim())
            saved = true
        }) { Text(if (saved) "ذخیره شد ✓" else "ذخیره", color = if (saved) SovraGreen else SovraCyan) }
    }
}

@Composable
private fun PresetChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label, color = SovraCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(SovraSurface)
            .border(1.dp, SovraCyan, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}
