package com.retrocable.box

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject

/**
 * The cable box interface is a web page bundled in assets/www.
 * This activity shows it full screen, forwards remote buttons to it,
 * and gives it a small bridge ("CableNative") to open the TV's real apps.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Serve bundled files from a real https origin (needed later for YouTube channels)
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true            // remembers settings + favorites
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            isFocusable = true
            isFocusableInTouchMode = true
            addJavascriptInterface(Bridge(), "CableNative")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assets.shouldInterceptRequest(request.url)
            }
        }
        setContentView(web)
        hideSystemBars()
        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    /** Map remote-control keys to the names the interface understands. */
    private fun keyName(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "ok"
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> "back"
        KeyEvent.KEYCODE_MENU -> "menu"
        KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV -> "guide"
        KeyEvent.KEYCODE_INFO -> "info"
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> "chup"
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> "chdn"
        KeyEvent.KEYCODE_LAST_CHANNEL -> "last"
        KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_BOOKMARK -> "fav"
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (code - KeyEvent.KEYCODE_0).toString()
        else -> null   // volume, mute, home, input etc. keep working normally
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val name = keyName(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            web.evaluateJavascript("window.cable&&cable.press('$name')", null)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        web.onResume()
        web.resumeTimers()
        // Coming back from Netflix etc. feels like the box waking up
        web.evaluateJavascript("window.cable&&cable.resume()", null)
    }

    override fun onPause() {
        web.onPause()
        web.pauseTimers()   // stop animating while another app is on screen
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** Methods the web interface can call. */
    inner class Bridge {

        @JavascriptInterface
        fun installedApps(): String {
            val pm = packageManager
            val seen = HashSet<String>()
            val out = JSONArray()
            val queries = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            val items = ArrayList<Pair<String, String>>()
            for (cat in queries) {
                val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(i, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName || !seen.add(pkg)) continue
                    items.add(pkg to ri.loadLabel(pm).toString())
                }
            }
            items.sortBy { it.second.lowercase() }
            for ((pkg, label) in items) out.put(JSONObject().put("pkg", pkg).put("label", label))
            return out.toString()
        }

        @JavascriptInterface
        fun launchApp(pkg: String) {
            runOnUiThread {
                val pm = packageManager
                val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(intent) } catch (_: ActivityNotFoundException) {}
                }
            }
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: ActivityNotFoundException) {}
            }
        }

        @JavascriptInterface
        fun openStore(pkg: String) {
            runOnUiThread {
                val uri = if (pkg.isNotBlank()) Uri.parse("market://details?id=$pkg") else Uri.parse("market://search?q=apps")
                try { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: ActivityNotFoundException) {
                    packageManager.getLeanbackLaunchIntentForPackage("com.android.vending")?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
                    }
                }
            }
        }
    }
}
