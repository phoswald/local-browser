package com.github.phoswald.localbrowser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark status bar icons even when the system is in dark mode; the app is always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val root = Environment.getExternalStorageDirectory()
        setContent {
            MaterialTheme {
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
    var upUrl by remember { mutableStateOf<String?>(null) }

    fun openExternal(uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
        }
    }

    fun openFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: IllegalArgumentException) {
            // Outside the storage root shared by the FileProvider.
        } catch (_: ActivityNotFoundException) {
        }
    }

    val webView = remember {
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            // Must precede "/", whose handler never returns null.
            .addPathHandler(ASSETS_PATH, WebViewAssetLoader.AssetsPathHandler(context))
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
                    if (request.url.scheme == "https" && request.url.host == HOST) {
                        val ext = externalFile(root, request.url.toString()) ?: return false
                        openFile(ext.file, ext.mimeType)
                        return true
                    }
                    openExternal(request.url)
                    return true
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    address = urlToDisplayPath(url)
                    canGoBack = view.canGoBack()
                    upUrl = parentUrl(url)
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
            val url = displayPathToUrl(root, input)
            val ext = externalFile(root, url)
            if (ext != null) {
                openFile(ext.file, ext.mimeType)
            } else {
                webView.loadUrl(url)
            }
        }
    }

    BrowserLayout(
        address = address,
        onAddressChange = { address = it },
        onGo = ::go,
        canGoBack = canGoBack,
        onBack = { webView.goBack() },
        canGoUp = upUrl != null,
        onUp = {
            val url = upUrl
            if (url != null) webView.loadUrl(url)
        },
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
    canGoUp: Boolean,
    onUp: () -> Unit,
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
                // Shrink the 48dp minimum touch target so the buttons sit closer together.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                    IconButton(onClick = onBack, enabled = canGoBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    IconButton(onClick = onUp, enabled = canGoUp) {
                        Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = stringResource(R.string.up))
                    }
                    IconButton(onClick = onHome) {
                        Icon(painterResource(R.drawable.ic_home), contentDescription = stringResource(R.string.home))
                    }
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
    MaterialTheme {
        BrowserLayout(
            address = "/Documents/",
            onAddressChange = {},
            onGo = {},
            canGoBack = true,
            onBack = {},
            canGoUp = true,
            onUp = {},
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
    MaterialTheme {
        PermissionScreen(onGrant = {})
    }
}
