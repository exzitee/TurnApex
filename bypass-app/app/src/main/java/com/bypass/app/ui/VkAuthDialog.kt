package com.bypass.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bypass.app.ui.theme.TermBlack
import com.bypass.app.ui.theme.TermBorder
import com.bypass.app.ui.theme.TermDim
import com.bypass.app.ui.theme.TermWhite

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VkAuthDialog(
    onHashesExtracted: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var statusText by remember { mutableStateOf("Opening VK ID login...") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TermBlack)
                .padding(12.dp)
                .border(1.dp, TermBorder, RectangleShape)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "╔══ VK AUTO-AUTH (WEBVIEW) ═══════",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                TerminalButton("[X] CLOSE") {
                    onDismiss()
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusText,
                color = TermDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, TermWhite, RectangleShape)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    statusText = "Loading: ${url?.take(30)}..."
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    if (url.contains("vk.com/call/join/") || url.contains("vk.ru/call/join/")) {
                                        val token = url.substringAfter("call/join/").trimEnd('/')
                                        if (token.isNotEmpty()) {
                                            statusText = "Captured call hash: ${token.take(6)}...!"
                                            onHashesExtracted(listOf(token))
                                            onDismiss()
                                            return true
                                        }
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    statusText = "Ready. Please login to VK ID."

                                    // Inject script to detect call links or token responses
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            if (window.location.href.indexOf('call/join') !== -1) {
                                                console.log("Found call link: " + window.location.href);
                                            }
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                }
                            }

                            loadUrl("https://id.vk.com/")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
