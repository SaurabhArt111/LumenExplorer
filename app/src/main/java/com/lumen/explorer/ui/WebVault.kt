package com.lumen.explorer.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.lumen.explorer.AppState
import com.lumen.explorer.FileOps
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream

/**
 * Receives files that SecureVault "downloads" (blob: links) and stores them in Downloads/SecureVault.
 * The page has no network access at all (the app declares no INTERNET permission).
 */
class WebBridge(private val ctx: Context) {
    private var name = "download"
    private var mime = "application/octet-stream"
    private var tmp: File? = null
    private var os: OutputStream? = null

    @JavascriptInterface fun isNative(): Boolean = true

    @JavascriptInterface
    fun begin(fileName: String, mimeType: String) {
        cleanup()
        name = fileName.ifBlank { "download" }
        mime = mimeType.ifBlank { "application/octet-stream" }
        val f = File.createTempFile("sv", ".tmp", ctx.cacheDir)
        tmp = f
        os = f.outputStream().buffered()
    }

    @JavascriptInterface
    fun chunk(b64: String) {
        try { os?.write(Base64.decode(b64, Base64.DEFAULT)) } catch (e: Exception) { fail(e.message ?: "write failed") }
    }

    @JavascriptInterface
    fun end() {
        val f = tmp
        try { os?.flush(); os?.close() } catch (_: Exception) { }
        os = null
        if (f == null) return
        val where = try { saveToDownloads(f, name, mime) } catch (e: Exception) { null }
        f.delete(); tmp = null
        AppState.scope.launch {
            if (where != null) { AppState.toast("Saved to $where"); AppState.changed(emptyList()) }
            else AppState.toast("Couldn't save the file")
        }
    }

    @JavascriptInterface
    fun fail(message: String) {
        cleanup()
        AppState.scope.launch { AppState.toast("Download failed: $message") }
    }

    private fun cleanup() {
        runCatching { os?.close() }; os = null
        tmp?.delete(); tmp = null
    }

    private fun saveToDownloads(src: File, rawName: String, mimeType: String): String? {
        val safe = rawName.replace(Regex("[\\\\/:*?\"<>|\u0000-\u001f]"), "_").trim().ifBlank { "download" }
        return if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/SecureVault")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            r.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } } ?: return null
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            r.update(uri, done, null, null)
            "Downloads/SecureVault/$safe"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SecureVault")
            dir.mkdirs()
            val dst = FileOps.uniqueFile(dir, safe)
            src.copyTo(dst, overwrite = false)
            "Downloads/SecureVault/${dst.name}"
        }
    }
}

/** Hooks blob: download links so they reach WebBridge (WebView has no download UI of its own). */
private const val HOOK_JS = """
(function(){
  if (window.__lumenHook) return; window.__lumenHook = true;
  function toB64(buf){ var s='',b=new Uint8Array(buf),i=0,n=b.length; for(;i<n;i+=0x8000){ s+=String.fromCharCode.apply(null,b.subarray(i,Math.min(n,i+0x8000))); } return btoa(s); }
  async function send(a){
    try{
      var name=a.getAttribute('download')||'download';
      var res=await fetch(a.href); var blob=await res.blob();
      LumenBridge.begin(name, blob.type||'application/octet-stream');
      var CH=3*1024*256, off=0;
      while(off<blob.size){ var part=await blob.slice(off,off+CH).arrayBuffer(); LumenBridge.chunk(toB64(part)); off+=CH; }
      LumenBridge.end();
    }catch(e){ LumenBridge.fail(String(e&&e.message||e)); }
  }
  var orig=HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click=function(){
    if(this.hasAttribute('download') && /^blob:/.test(this.href)){ send(this); return; }
    return orig.apply(this, arguments);
  };
  document.addEventListener('click', function(e){
    var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;
    if(a && /^blob:/.test(a.href)){ e.preventDefault(); send(a); }
  }, true);
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebVaultScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var chooser by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var multiple by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val cb = chooser; chooser = null
        val list = if (multiple) uris else uris.take(1)
        cb?.onReceiveValue(list.toTypedArray())
    }
    val loader = remember {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .build()
    }
    var web by remember { mutableStateOf<WebView?>(null) }
    // leaving the screen destroys the WebView, which also locks the vault again
    DisposableEffect(Unit) {
        onDispose {
            web?.let { w -> runCatching { w.loadUrl("about:blank"); w.stopLoading(); (w.parent as? ViewGroup)?.removeView(w); w.destroy() } }
            web = null
        }
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { c ->
            WebView(c).apply {
                web = this
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportMultipleWindows(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                setBackgroundColor(0xFF0B0F17.toInt())
                addJavascriptInterface(WebBridge(c.applicationContext), "LumenBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host != "appassets.androidplatform.net"

                    override fun onPageFinished(view: WebView, url: String?) { view.evaluateJavascript(HOOK_JS, null) }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: WebChromeClient.FileChooserParams): Boolean {
                        chooser?.onReceiveValue(null)
                        chooser = cb
                        multiple = p.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                        return try { picker.launch(arrayOf("*/*")); true } catch (_: Exception) { chooser = null; cb.onReceiveValue(null); false }
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/securevault/index.html")
            }
        },
        onRelease = { /* destroyed in DisposableEffect */ }
    )
}
