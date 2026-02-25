package com.ravi.freedium.ui.composable

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Since there is no alternative for webview in the compose world, using AndroidView's interoperability feature to launch a webview
 */
@Composable
fun WebViewScreen(url: String) {
    // In the Composable's Declarative world, AndroidView is the bridge to the imperative View world
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        // This 'factory' block runs ONCE when the composable enters the Composition -- ???
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
        }
    }, update = { webView ->
        webView.loadUrl(url)
    })
}