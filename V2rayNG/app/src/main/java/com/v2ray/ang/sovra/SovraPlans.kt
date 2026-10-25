package com.v2ray.ang.sovra

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Plans tab: catalogue, free test, card-to-card order and receipt upload. */
@Composable
internal fun PlansTab(onServersChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<SovraPlans?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("volume") }
    var gb by remember { mutableIntStateOf(10) }
    var users by remember { mutableIntStateOf(1) }
    var order by remember { mutableStateOf<SovraOrderResult?>(null) }
    var orders by remember { mutableStateOf<List<SovraOrder>>(emptyList()) }
    var reloadOrders by remember { mutableIntStateOf(0) }
    var detailOrder by remember { mutableStateOf<SovraOrder?>(null) }
    var gbText by remember { mutableStateOf("10") }
    var uploadTargetId by remember { mutableLongStateOf(0L) }
    var canTest by remember { mutableStateOf(false) }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()

    LaunchedEffect(Unit) {
        val p: Pair<SovraPlans?, String?> = withContext(Dispatchers.IO) {
            try { Pair(SovraApi.plans(), null) }
            catch (e: SovraApiException) { Pair(null, e.message) }
            catch (e: Exception) { Pair(null, "خطای شبکه") }
        }
        plans = p.first; error = p.second
    }
    LaunchedEffect(reloadOrders) {
        withContext(Dispatchers.IO) {
            try {
                val token = SovraStore.token(ctx) ?: return@withContext
                val list = SovraApi.orders(token)
                val me = try { SovraApi.me(token) } catch (e: Exception) { null }
                withContext(Dispatchers.Main) {
                    orders = list
                    if (me != null) canTest = me.canStartTest
                }
            } catch (e: Exception) { /* orders are optional; catalogue still works */ }
        }
    }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val targetId = if (uploadTargetId != 0L) uploadTargetId else (order?.orderId ?: 0L)
        if (targetId == 0L) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val failure: String? = withContext(Dispatchers.IO) {
                try {
                    val token = SovraStore.token(ctx) ?: throw SovraApiException("auth_required", "وارد حساب نشدی")
                    val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw SovraApiException("bad_image", "عکس خونده نشد")
                    SovraApi.uploadReceipt(token, targetId, bytes, mime)
                    null
                } catch (e: SovraApiException) { e.message }
                catch (e: Exception) { "خطای غیرمنتظره" }
            }
            busy = false
            if (failure == null) {
                toast("رسید فرستاده شد. بعد از تأیید مدیر، اکانتت فعال/تمدید میشه")
                order = null
                detailOrder = null
                uploadTargetId = 0L
                reloadOrders += 1
            } else toast(failure)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("پلن‌ها", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val p = plans
        if (p == null) {
            Text(error ?: "در حال دریافت پلن‌ها...", color = if (error != null) SovraDanger else SovraMuted)
        } else {
            Text("دوره: ${p.periodDays} روزه • تانل‌شده", color = SovraMuted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToggleChip("حجمی", kind == "volume", Modifier.weight(1f)) { kind = "volume" }
                ToggleChip("نامحدود", kind == "unlimited", Modifier.weight(1f)) { kind = "unlimited" }
            }
            if (kind == "volume") {
                Text("حجم دلخواه (۱ تا ۲۰۰۰ گیگ)", color = Color.White, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(5, 10, 20, 50, 100).forEach { g ->
                        ToggleChip("$g", gb == g, Modifier.weight(1f)) { gb = g; gbText = g.toString() }
                    }
                }
                Slider(
                    value = gb.toFloat(),
                    onValueChange = { gb = it.toInt().coerceIn(1, 200); gbText = gb.toString() },
                    valueRange = 1f..200f,
                    colors = SliderDefaults.colors(thumbColor = SovraCyan, activeTrackColor = SovraCyan),
                )
                OutlinedTextField(
                    value = gbText,
                    onValueChange = { t ->
                        gbText = t.filter { it.isDigit() }.take(4)
                        gbText.toIntOrNull()?.let { gb = it.coerceIn(1, 2000) }
                    },
                    label = { Text("حجم دقیق (گیگابایت)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = SovraCyan, unfocusedBorderColor = Color(0xFF334155),
                        cursorColor = SovraCyan, focusedLabelColor = SovraCyan, unfocusedLabelColor = SovraMuted,
                    ),
                )
                val price = p.pricePerGb * gb
                Text("$gb گیگ • ${p.periodDays} روز • تانل پرسرعت • همه سرورها", color = SovraMuted, fontSize = 12.sp)
                Text("مبلغ: ${formatToman(price)} تومان", color = SovraCyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            } else {
                Text("تعداد کاربر", color = Color.White, fontWeight = FontWeight.Medium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.unlimited.sortedBy { it.users }.forEach { tier ->
                        val popular = tier.users == 3
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (users == tier.users) Color(0xFF16233B) else SovraSurface)
                                .border(1.dp, if (popular) Color(0xFF7B5CFF) else CardLineCompat, RoundedCornerShape(14.dp))
                                .clickable { users = tier.users }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${tier.users} کاربره" + if (popular) " ⭐ محبوب" else "" + "\n۳۰ روز • نامحدود • تانل پرسرعت", color = Color.White, fontSize = 13.sp)
                            Text("${formatToman(tier.price)} ت", color = SovraCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val current = order
            if (current == null) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        scope.launch {
                            busy = true
                            val result: Pair<SovraOrderResult?, String?> = withContext(Dispatchers.IO) {
                                try {
                                    val token = SovraStore.token(ctx) ?: throw SovraApiException("auth_required", "وارد حساب نشدی")
                                    Pair(SovraApi.createOrder(token, kind, if (kind == "volume") gb else 0, if (kind == "unlimited") users else 0), null)
                                } catch (e: SovraApiException) { Pair(null, e.message) }
                                catch (e: Exception) { Pair(null, "خطای شبکه") }
                            }
                            busy = false
                            if (result.first != null) order = result.first else toast(result.second ?: "خطا")
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SovraCyan, contentColor = Color(0xFF04202A)),
                ) { Text(if (busy) "..." else "ادامه به پرداخت", fontWeight = FontWeight.Bold) }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(SovraSurface).border(1.dp, SovraCyan, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("پرداخت کارت‌به‌کارت", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("مبلغ: ${formatToman(current.amount)} تومان", color = SovraCyan)
                    Text("شماره کارت: ${current.cardNumber}", color = Color.White)
                    Text("به نام: ${current.cardHolder}", color = SovraMuted, fontSize = 13.sp)
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("card", current.cardNumber))
                        toast("شماره کارت کپی شد")
                    }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, SovraCyan)) {
                        Text("کپی شماره کارت", color = SovraCyan)
                    }
                    Button(
                        onClick = { receiptPicker.launch("image/*") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SovraCyan, contentColor = Color(0xFF04202A)),
                    ) { Text("آپلود عکس رسید", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { order = null }) { Text("انصراف", color = SovraMuted) }
                }
            }
        }

        if (canTest) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(SovraSurface).border(1.dp, CardLineCompat, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🎁 تست رایگان", color = Color.White, fontWeight = FontWeight.Bold)
                Text("۵۱۲ مگابایت • ۲۴ ساعت از اولین اتصال", color = SovraMuted, fontSize = 13.sp)
                Button(
                    onClick = {
                        if (busy) return@Button
                        scope.launch {
                            busy = true
                            val failure: String? = withContext(Dispatchers.IO) {
                                try {
                                    val token = SovraStore.token(ctx) ?: throw SovraApiException("auth_required", "وارد حساب نشدی")
                                    SovraApi.startTest(token)
                                    SovraSync.sync(ctx, true)
                                    null
                                } catch (e: SovraApiException) { e.message }
                                catch (e: Exception) { "خطای شبکه" }
                            }
                            busy = false
                            if (failure == null) { toast("تست رایگان فعال شد 🎉"); canTest = false; onServersChanged() }
                            else toast(failure)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B5CFF), contentColor = Color.White),
                ) { Text("فعال‌کردن تست رایگان", fontWeight = FontWeight.Bold) }
            }
        }

        if (orders.isNotEmpty()) {
            Text("سفارش‌های من", color = Color.White, fontWeight = FontWeight.Bold)
            orders.take(10).forEach { o ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SovraSurface).border(1.dp, CardLineCompat, RoundedCornerShape(12.dp))
                        .clickable { detailOrder = o }.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "#" + o.id + " • " + if (o.kind == "volume") "${o.gb} گیگ" else "${o.users} کاربره نامحدود",
                        color = Color.White, fontSize = 13.sp,
                    )
                    Text(orderStatusText(o.status), color = orderStatusColor(o.status), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val det = detailOrder
        if (det != null) {
            AlertDialog(
                onDismissRequest = { detailOrder = null },
                title = { Text("سفارش #" + det.id) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("پلن: " + if (det.kind == "volume") "${det.gb} گیگ حجمی" else "${det.users} کاربره نامحدود")
                        Text("مبلغ: ${formatToman(det.amount)} تومان")
                        Text("وضعیت: " + orderStatusText(det.status), color = orderStatusColor(det.status), fontWeight = FontWeight.Bold)
                        Text(
                            when (det.status) {
                                "new" -> "مرحله ۱ از ۳: مبلغ رو کارت‌به‌کارت کن و رسید رو آپلود کن."
                                "pending" -> "مرحله ۲ از ۳: رسیدت رسیده و منتظر تأیید مدیره."
                                "approved" -> "مرحله ۳ از ۳: تأیید شده و اکانتت فعال/تمدید شده. از تب سرورها به‌روزرسانی کن."
                                "rejected" -> "رسید رد شده. یه رسید واضح‌تر آپلود کن یا با پشتیبانی حرف بزن."
                                else -> ""
                            },
                            color = SovraMuted, fontSize = 13.sp,
                        )
                    }
                },
                confirmButton = {
                    if (det.status == "new" || det.status == "pending" || det.status == "rejected") {
                        TextButton(onClick = {
                            uploadTargetId = det.id
                            receiptPicker.launch("image/*")
                        }) { Text("آپلود رسید / ادامه پرداخت", color = SovraCyan) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { detailOrder = null }) { Text("بستن") }
                },
            )
        }
        if (busy) CircularProgressIndicator(color = SovraCyan)
        Spacer(Modifier.height(8.dp))
    }
}

private val CardLineCompat = Color(0xFF1E293B)

@Composable
private fun ToggleChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color(0xFF04202A) else SovraMuted,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) SovraCyan else SovraSurface)
            .border(1.dp, if (selected) SovraCyan else CardLineCompat, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private fun orderStatusText(status: String): String = when (status) {
    "new" -> "در انتظار پرداخت"
    "pending" -> "در حال بررسی"
    "approved" -> "تأیید شده ✅"
    "rejected" -> "رد شده"
    else -> status
}

private fun orderStatusColor(status: String): Color = when (status) {
    "approved" -> SovraGreen
    "rejected" -> SovraDanger
    "pending" -> Color(0xFFFBBF24)
    else -> SovraMuted
}

private fun formatToman(amount: Long): String = String.format(Locale.US, "%,d", amount)
