package com.v2ray.ang.sovra

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Real 3D neon shield: a local WebGL (three.js) scene in assets/shield.html.
 * Fully offline (three.min.js and the logo texture are bundled assets).
 * Pauses rendering when the composable leaves the screen to save battery.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SovraShieldWeb(connected: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val webView = remember {
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript("setConnected($connected)", null)
                }
            }
            loadUrl("file:///android_asset/shield.html")
        }
    }

    LaunchedEffect(connected) {
        webView.evaluateJavascript("setConnected($connected)", null)
    }

    DisposableEffect(Unit) {
        webView.evaluateJavascript("setPaused(false)", null)
        onDispose {
            webView.evaluateJavascript("setPaused(true)", null)
            webView.onPause()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        onRelease = { it.destroy() },
    )
}
