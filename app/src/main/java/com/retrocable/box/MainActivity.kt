package com.retrocable.box

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * The cable box interface is a web page bundled in assets/www.
 * This activity shows it full screen, forwards remote buttons to it,
 * and gives it a small bridge ("CableNative") to open the TV's real apps.
 */
@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private val results = ConcurrentHashMap<Int, String>()

    // Live video (Pluto TV channels) plays on this surface, underneath the see-through interface
    private lateinit var videoSurface: SurfaceView
    private var player: ExoPlayer? = null
    private var streamUrls: List<String> = emptyList()
    private var streamIndex = 0

    // A downloaded update waiting for the one-time "install unknown apps" permission
    private var pendingUpdate: File? = null

    // Radar channel music, played by Android's media player (not the web view)
    private var jazzPlayer: MediaPlayer? = null
    private var jazzStyle = ""
    private fun musicFile(style: String) = File(cacheDir, "nyx-music-" + style.filter { it.isLetter() } + ".wav")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Serve bundled files from a real https origin (needed later for YouTube channels)
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
        videoSurface = SurfaceView(this).apply { visibility = View.INVISIBLE }
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        root.addView(videoSurface, FrameLayout.LayoutParams(1, 1))
        root.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        hideSystemBars()
        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    private fun js(code: String) = web.evaluateJavascript("window.cable&&$code", null)

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        p.setVideoSurfaceView(videoSurface)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val name = when (state) {
                    Player.STATE_READY -> "ready"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_ENDED -> "ended"
                    else -> "idle"
                }
                js("cable.playerState('$name')")
            }
            override fun onPlayerError(error: PlaybackException) {
                // try the next address for this channel before giving up
                if (streamIndex + 1 < streamUrls.size) {
                    streamIndex++
                    startStream()
                } else {
                    js("cable.playerState('error')")
                }
            }
        })
        player = p
        return p
    }

    private fun startStream() {
        val p = ensurePlayer()
        val item = MediaItem.Builder()
            .setUri(streamUrls[streamIndex])
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
    }

    private fun startJazz(style: String) {
        if (jazzPlayer != null && jazzStyle == style) return
        jazzPlayer?.let { try { it.stop() } catch (e: Exception) {}; it.release() }
        jazzPlayer = null
        val jazzFile = musicFile(style)
        if (!jazzFile.exists()) { js("cable.jazzNative('error')"); return }
        jazzStyle = style
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(jazzFile.absolutePath)
            mp.isLooping = true
            mp.setVolume(0.85f, 0.85f)
            mp.setOnPreparedListener { it.start(); js("cable.jazzNative('playing')") }
            mp.setOnErrorListener { _, _, _ -> js("cable.jazzNative('error')"); true }
            mp.prepareAsync()
            jazzPlayer = mp
        } catch (e: Exception) {
            mp.release()
            js("cable.jazzNative('error')")
        }
    }

    private fun stopJazz() {
        jazzPlayer?.let { try { it.stop() } catch (e: Exception) {}; it.release() }
        jazzPlayer = null
        js("cable.jazzNative('stopped')")
    }

    private fun stopStream() {
        player?.stop()
        player?.clearMediaItems()
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
        // Also hand the page a real copy of the key press. Android's web view only lets a page
        // start sound (the radar channel's jazz) after a genuine button press; the page ignores
        // these copies, so nothing happens twice. Back and Menu stay with the app.
        if (name != "back" && name != "menu") web.dispatchKeyEvent(event)
        return true
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        web.onResume()
        web.resumeTimers()
        // Coming back from Netflix etc. feels like the box waking up
        web.evaluateJavascript("window.cable&&cable.resume()", null)
        // Back from the permission screen: install the update that was waiting
        pendingUpdate?.let { if (packageManager.canRequestPackageInstalls()) { pendingUpdate = null; openInstaller(it) } }
    }

    private fun openInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
            js("cable.updateState('installing')")
        } catch (e: Exception) {
            js("cable.updateState('error')")
        }
    }

    override fun onPause() {
        js("cable.pause()")  // tells the page the app is in the background
        stopJazz()
        stopStream()        // no streaming in the background while Netflix etc. is open
        videoSurface.visibility = View.INVISIBLE
        web.onPause()
        web.pauseTimers()   // stop animating while another app is on screen
        super.onPause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        jazzPlayer?.release()
        jazzPlayer = null
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
                    try { startActivity(intent) } catch (e: ActivityNotFoundException) {}
                }
            }
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: ActivityNotFoundException) {}
            }
        }

        /** Fetches a URL off the main thread (weather + Pluto TV listings), then tells the page it's ready. */
        @JavascriptInterface
        fun httpGet(id: Int, url: String, headersJson: String) {
            Thread {
                var status = 0
                var body = ""
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("User-Agent", "RetroCableBox/0.3 (personal hobby project)")
                    conn.setRequestProperty("Accept", "application/geo+json, application/json")
                    if (headersJson.isNotBlank()) {
                        val h = JSONObject(headersJson)
                        for (k in h.keys()) conn.setRequestProperty(k, h.getString(k))
                    }
                    status = conn.responseCode
                    val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                    body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    conn.disconnect()
                } catch (e: Exception) {
                    status = 0
                    body = e.toString()
                }
                results[id] = body
                runOnUiThread { web.evaluateJavascript("window.cable&&cable.httpDone($id,$status)", null) }
            }.start()
        }

        @JavascriptInterface
        fun takeResult(id: Int): String = results.remove(id) ?: ""

        /** Plays a live HLS stream. urlsJson is a list of addresses to try in order. */
        @JavascriptInterface
        fun playStream(urlsJson: String) {
            val arr = JSONArray(urlsJson)
            val list = (0 until arr.length()).map { arr.getString(it) }
            if (list.isEmpty()) return
            runOnUiThread {
                streamUrls = list
                streamIndex = 0
                startStream()
            }
        }

        @JavascriptInterface
        fun stopStream() {
            runOnUiThread { this@MainActivity.stopStream() }
        }

        /** Where the video shows, in screen pixels. A zero size hides the picture (sound keeps playing). */
        @JavascriptInterface
        fun videoRect(x: Int, y: Int, w: Int, h: Int) {
            runOnUiThread {
                if (w <= 0 || h <= 0) {
                    videoSurface.visibility = View.INVISIBLE
                } else {
                    val lp = FrameLayout.LayoutParams(w, h)
                    lp.leftMargin = x
                    lp.topMargin = y
                    videoSurface.layoutParams = lp
                    videoSurface.visibility = View.VISIBLE
                }
            }
        }

        /** The page sends the composed jazz loop in pieces, then asks for it to play. */
        @JavascriptInterface
        fun jazzReady(style: String): Boolean = musicFile(style).let { it.exists() && it.length() > 100000 }

        @JavascriptInterface
        fun jazzBegin(style: String) { musicFile(style).delete() }

        @JavascriptInterface
        fun jazzChunk(style: String, b64: String) {
            java.io.FileOutputStream(musicFile(style), true).use { it.write(Base64.decode(b64, Base64.DEFAULT)) }
        }

        @JavascriptInterface
        fun jazzPlay(style: String, on: Boolean) {
            runOnUiThread { if (on) startJazz(style) else stopJazz() }
        }

        /** True on a phone or tablet (touch screen, no TV remote). */
        @JavascriptInterface
        fun isTouch(): Boolean =
            !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)

        /** Keeps the tablet's screen on while a channel is playing. */
        @JavascriptInterface
        fun keepAwake(on: Boolean) {
            runOnUiThread {
                if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /** Back on the tablet's main menu leaves NyxVision. */
        @JavascriptInterface
        fun exitApp() { runOnUiThread { moveTaskToBack(true) } }

        /** This app's build number (100 + the GitHub build number). */
        @JavascriptInterface
        fun appVersion(): Int = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: Exception) { 0 }

        /** Downloads a new NyxVision APK and opens Android's installer for it. */
        @JavascriptInterface
        fun installUpdate(url: String) {
            Thread {
                try {
                    val out = File(cacheDir, "nyxvision-update.apk")
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    conn.setRequestProperty("User-Agent", "NyxVision-Updater")
                    val total = conn.contentLengthLong
                    var done = 0L
                    var lastPct = -1
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; runOnUiThread { js("cable.updateProgress($pct)") } }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    runOnUiThread {
                        if (!packageManager.canRequestPackageInstalls()) {
                            // One-time permission: let NyxVision install its own updates
                            pendingUpdate = out
                            js("cable.updateState('permission')")
                            try {
                                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) { js("cable.updateState('error')") }
                        } else {
                            openInstaller(out)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { js("cable.updateState('error')") }
                }
            }.start()
        }

        /** Opens a Pluto TV channel in the Pluto TV app. Returns false if Pluto TV isn't installed. */
        @JavascriptInterface
        fun openPluto(slug: String): Boolean {
            val pkg = "tv.pluto.android"
            val launch = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            runOnUiThread {
                val deep = Intent(Intent.ACTION_VIEW, Uri.parse("https://pluto.tv/live-tv/$slug"))
                    .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    if (slug.isNotBlank()) startActivity(deep) else throw ActivityNotFoundException()
                } catch (e: Exception) {
                    // Pluto didn't accept the channel link: open the app's home screen instead
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(launch) } catch (e2: Exception) {}
                }
            }
            return true
        }

        @JavascriptInterface
        fun openStore(pkg: String) {
            runOnUiThread {
                val uri = if (pkg.isNotBlank()) Uri.parse("market://details?id=$pkg") else Uri.parse("market://search?q=apps")
                try { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: ActivityNotFoundException) {
                    packageManager.getLeanbackLaunchIntentForPackage("com.android.vending")?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
                    }
                }
            }
        }
    }
}
