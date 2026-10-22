package com.github.phoswald.localbrowser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.github.phoswald.localbrowser.ui.theme.LocalBrowserTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val root = Environment.getExternalStorageDirectory()
        setContent {
            LocalBrowserTheme {
                if (hasPermission) {
                    BrowserScreen(root)
                } else {
                    PermissionScreen(onGrant = ::requestPermission)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermission = Environment.isExternalStorageManager()
    }

    private fun requestPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        )
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.permission_text))
            Button(onClick = onGrant) {
                Text(stringResource(R.string.permission_button))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(root: File) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var address by remember { mutableStateOf("/") }
    var canGoBack by remember { mutableStateOf(false) }

    fun openExternal(uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
        }
    }

    val webView = remember {
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/", LocalFileHandler(root))
            .build()
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.scheme == "https" && request.url.host == HOST) return false
                    openExternal(request.url)
                    return true
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    address = urlToDisplayPath(url)
                    canGoBack = view.canGoBack()
                }
            }
            loadUrl(BASE_URL)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }
    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    fun go() {
        focusManager.clearFocus()
        val input = address.trim()
        if (input.contains("://")) {
            openExternal(Uri.parse(input))
        } else {
            webView.loadUrl(displayPathToUrl(root, input))
        }
    }

    BrowserLayout(
        address = address,
        onAddressChange = { address = it },
        onGo = ::go,
        canGoBack = canGoBack,
        onBack = { webView.goBack() },
        onHome = { webView.loadUrl(BASE_URL) },
    ) { modifier ->
        AndroidView(factory = { webView }, modifier = modifier)
    }
}

@Composable
fun BrowserLayout(
    address: String,
    onAddressChange: (String) -> Unit,
    onGo: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier.statusBarsPadding().padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = canGoBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                IconButton(onClick = onHome) {
                    Icon(painterResource(R.drawable.ic_home), contentDescription = stringResource(R.string.home))
                }
                BasicTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            innerTextField()
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        content(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding())
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun BrowserLayoutPreview() {
    LocalBrowserTheme {
        BrowserLayout(
            address = "/Documents/",
            onAddressChange = {},
            onGo = {},
            canGoBack = true,
            onBack = {},
            onHome = {},
        ) { modifier ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("WebView")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PermissionScreenPreview() {
    LocalBrowserTheme {
        PermissionScreen(onGrant = {})
    }
}
