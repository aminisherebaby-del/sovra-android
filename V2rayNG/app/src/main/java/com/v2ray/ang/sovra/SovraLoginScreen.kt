package com.v2ray.ang.sovra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF070B16)
private val Cyan = Color(0xFF22D3EE)
private val Muted = Color(0xFF94A3B8)
private val Danger = Color(0xFFFB7185)

@Composable
fun SovraLoginScreen(onLoggedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sub by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("sub") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("SOVRA VPN", color = Cyan, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("وارد حساب خودت شو", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "با لینک اشتراک، یا نام کاربری وارد شو؛ یا حساب تازه بساز",
                color = Muted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("sub" to "لینک اشتراک", "user" to "نام کاربری", "register" to "ثبت‌نام").forEach { (m, label) ->
                    Text(
                        label,
                        color = if (mode == m) Color(0xFF04202A) else Muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (mode == m) Cyan else Color(0xFF0E1627))
                            .clickable { mode = m; error = null }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (mode == "sub") {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (!clip.isNullOrBlank()) sub = clip.trim()
                }) { Text("📋 چسباندن از کلیپ‌بورد", color = Cyan) }
                OutlinedTextField(
                    value = sub,
                    onValueChange = { sub = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("لینک اشتراک") },
                    singleLine = true,
                    enabled = !busy,
                    colors = fieldColors(),
                )
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("نام کاربری") },
                    singleLine = true,
                    enabled = !busy,
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (mode == "register") "رمز عبور (حداقل ۸ حرف)" else "رمز عبور") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = fieldColors(),
                )
                if (mode == "register") {
                    Spacer(Modifier.height(8.dp))
                    Text("بعد از ثبت‌نام، از تب پلن‌ها می‌تونی تست رایگان بگیری یا خرید کنی.", color = Muted, fontSize = 12.sp)
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error ?: "", color = Danger, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (busy) return@Button
                    scope.launch {
                        busy = true
                        error = null
                        val failure: String? = withContext(Dispatchers.IO) {
                            try {
                                val token = when (mode) {
                                    "sub" -> SovraApi.loginSub(sub)
                                    "register" -> SovraApi.register(username, password)
                                    else -> SovraApi.login(username, password)
                                }
                                SovraStore.saveToken(ctx, token)
                                if (mode == "sub") {
                                    SovraSync.sync(ctx, true)
                                } else {
                                    // New / username accounts may not have servers yet (free test / purchase first).
                                    try { SovraSync.sync(ctx, true) } catch (e: SovraApiException) { /* keep session */ }
                                }
                                null
                            } catch (e: SovraApiException) {
                                if (mode == "sub") SovraStore.clearToken(ctx)
                                e.message ?: "خطا"
                            } catch (e: Exception) {
                                if (mode == "sub") SovraStore.clearToken(ctx)
                                "خطای غیرمنتظره. دوباره امتحان کن"
                            }
                        }
                        busy = false
                        if (failure == null) onLoggedIn() else error = failure
                    }
                },
                enabled = !busy && when (mode) {
                    "sub" -> sub.isNotBlank()
                    "register" -> username.isNotBlank() && password.length >= 8
                    else -> username.isNotBlank() && password.isNotBlank()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = Color(0xFF04202A),
                    disabledContainerColor = Color(0xFF1E293B),
                    disabledContentColor = Muted,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color(0xFF04202A),
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(22.dp),
                    )
                } else {
                    Text(if (mode == "register") "ساخت حساب" else "ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Muted,
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Color(0xFF334155),
    cursorColor = Cyan,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = Muted,
)
