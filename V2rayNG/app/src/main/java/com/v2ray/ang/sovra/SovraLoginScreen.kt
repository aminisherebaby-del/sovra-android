package com.v2ray.ang.sovra

import androidx.compose.foundation.background
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
                "لینک اشتراکت رو اینجا بچسبون",
                color = Muted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = sub,
                onValueChange = { sub = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("لینک اشتراک") },
                singleLine = true,
                enabled = !busy,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Muted,
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = Color(0xFF334155),
                    cursorColor = Cyan,
                    focusedLabelColor = Cyan,
                    unfocusedLabelColor = Muted,
                ),
            )

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
                                val token = SovraApi.loginSub(sub)
                                SovraStore.saveToken(ctx, token)
                                SovraSync.sync(ctx, true)
                                null
                            } catch (e: SovraApiException) {
                                SovraStore.clearToken(ctx)
                                e.message ?: "خطا"
                            } catch (e: Exception) {
                                SovraStore.clearToken(ctx)
                                "خطای غیرمنتظره. دوباره امتحان کن"
                            }
                        }
                        busy = false
                        if (failure == null) onLoggedIn() else error = failure
                    }
                },
                enabled = !busy && sub.isNotBlank(),
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
                    Text("ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
