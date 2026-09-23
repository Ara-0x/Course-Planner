package ir.courseplanner.app.feature.portal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Native Android In-App Browser for University Portal (Pooya, Golestan, etc.)
 * Unlike web browsers, native Android WebView is NOT restricted by X-Frame-Options
 * and can directly access inner frame DOMs to extract presented courses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalWebViewScreen(
    initialUrl: String = "https://pooya.khorasan.ac.ir/",
    onCoursesExtracted: (rawHtml: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("پرتال دانشگاه") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pageTitle, style = MaterialTheme.typography.titleSmall)
                        Text(currentUrl, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "بارگذاری مجدد")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Button(
                    onClick = {
                        val wv = webViewInstance ?: return@Button
                        extractCoursesFromWebView(wv) { html ->
                            onCoursesExtracted(html)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("⚡ استخراج خودکار دروس این صفحه")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        CookieManager.getInstance().setAcceptCookie(true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                pageTitle = view?.title ?: "پرتال دانشگاه"
                                url?.let { currentUrl = it }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }

                        loadUrl(initialUrl)
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * Recursively scans all frames & iframes for the presented courses table.
 */
fun extractCoursesFromWebView(webView: WebView, onExtracted: (html: String) -> Unit) {
    val jsScript = """
        (function() {
            try {
                function searchWindow(w) {
                    try {
                        if (w.document && w.document.body && w.document.body.innerHTML.includes('شماره درس')) {
                            return w.document.documentElement.outerHTML;
                        }
                    } catch(e) {}
                    for (var i = 0; i < w.frames.length; i++) {
                        var res = searchWindow(w.frames[i]);
                        if (res) return res;
                    }
                    return null;
                }
                var found = searchWindow(window);
                if (found) return found;

                var leftScr = window.frames['LeftScr'] || document.querySelector('frame[name="LeftScr"]');
                if (leftScr && leftScr.document) {
                    return leftScr.document.documentElement.outerHTML;
                }
                return document.documentElement.outerHTML;
            } catch(e) {
                return document.documentElement.outerHTML;
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsScript) { jsonResult ->
        if (jsonResult == null || jsonResult == "null" || jsonResult.isEmpty()) return@evaluateJavascript
        val unescaped = try {
            JSONObject("{\"h\": $jsonResult}").getString("h")
        } catch (e: Exception) {
            jsonResult.removeSurrounding("\"").replace("\\n", "\n").replace("\\\"", "\"")
        }
        onExtracted(unescaped)
    }
}
