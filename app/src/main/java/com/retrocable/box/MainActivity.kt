package com.retrocable.box

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.ContentApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ListItem
import com.spotify.protocol.types.PlayerState
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import android.media.AudioManager
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
    private var cuePlayer: MediaPlayer? = null
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

    private var moviePos = 0L

    private fun startStream() {
        val p = ensurePlayer()
        val url = streamUrls[streamIndex]
        val b = MediaItem.Builder().setUri(url)
        if (!url.contains(".mp4", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (moviePos > 0) p.setMediaItem(b.build(), moviePos) else p.setMediaItem(b.build())
        moviePos = 0L
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

    // ---- Twilight: the owner's own music files, sent from a phone on the same Wi-Fi; they stay on this device ----
    private val twDir by lazy { File(filesDir, "twmusic").apply { mkdirs() } }
    private var twPlayer: MediaPlayer? = null
    private var twIdx = 0
    @Volatile private var twServer: ServerSocket? = null
    private fun twList(): List<File> = (twDir.listFiles() ?: arrayOf()).filter { it.isFile && it.length() > 0 && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
    private fun twFilesJson(): String { val a = JSONArray(); twList().forEach { a.put(JSONObject().put("name", it.name).put("size", it.length())) }; return a.toString() }
    private fun twNotify() = runOnUiThread { web.evaluateJavascript("window.cable&&cable.twFiles&&cable.twFiles(" + JSONObject.quote(twFilesJson()) + ")", null) }
    private fun twStop() { twPlayer?.let { try { it.stop() } catch (e: Exception) {}; it.release() }; twPlayer = null }
    private fun twStart() {
        val L = twList(); if (L.isEmpty()) { twStop(); return }
        if (twIdx >= L.size) twIdx = 0
        twStop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(L[twIdx].absolutePath)
            mp.isLooping = L.size == 1
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { twIdx = (twIdx + 1) % maxOf(1, twList().size); twStart() }
            mp.setOnErrorListener { _, _, _ -> twIdx++; if (twIdx < twList().size) twStart(); true }
            mp.prepareAsync()
            twPlayer = mp
        } catch (e: Exception) { mp.release() }
    }
    private fun localIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.inetAddresses.toList().forEach { a -> if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) return a.hostAddress ?: "" }
            }
        } catch (e: Exception) {}
        return ""
    }
    private val TW_PAGE = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>NyxVision Twilight Music</title>
<style>body{margin:0;font:16px -apple-system,Roboto,Arial,sans-serif;background:#050b16;color:#e8f0ff;padding:24px}h1{font-weight:200;letter-spacing:.12em;color:#bfe3ff;text-shadow:0 0 10px #3d9bff;margin:0 0 4px}
p{color:#9fb4d6}label{display:block;margin:20px 0;padding:22px;border:2px dashed #3d6fb0;border-radius:14px;text-align:center;font-size:18px;color:#cfe3ff}input{display:none}
button{width:100%;padding:16px;border:0;border-radius:12px;background:#2f7dff;color:#fff;font-size:18px;font-weight:600}button:disabled{opacity:.4}
li{margin:6px 0}.bar{height:8px;background:#13233f;border-radius:4px;overflow:hidden;margin-top:4px}.bar i{display:block;height:100%;width:0;background:#4fb2ff}.ok{color:#7dff8a}.err{color:#ff7a6a}</style></head>
<body><h1>Twilight</h1><p>Send music to Nyx Twilight on your TV. You can pick several files; they play in filename order and loop.</p>
<label>Tap to choose music files<input id="f" type="file" accept="audio/*,.mp3,.m4a,.aac,.ogg,.wav,.flac" multiple></label><ul id="l"></ul><button id="go" disabled>Send to TV</button><p id="st"></p>
<script>const f=document.getElementById('f'),l=document.getElementById('l'),go=document.getElementById('go'),st=document.getElementById('st');
f.onchange=()=>{l.innerHTML='';[...f.files].forEach((x,i)=>{l.innerHTML+='<li>'+x.name.replace(/</g,'&lt;')+' <small>('+(x.size/1048576).toFixed(1)+' MB)</small><div class="bar"><i id="b'+i+'"></i></div></li>'});go.disabled=!f.files.length};
go.onclick=async()=>{go.disabled=true;let ok=0;for(let i=0;i<f.files.length;i++){const x=f.files[i];st.textContent='Sending '+x.name+'…';
 await new Promise(res=>{const r=new XMLHttpRequest();r.open('POST','/upload?name='+encodeURIComponent(x.name));r.upload.onprogress=e=>{if(e.lengthComputable)document.getElementById('b'+i).style.width=(e.loaded/e.total*100)+'%'};
 r.onload=()=>{if(r.status==200)ok++;res()};r.onerror=res;r.send(x)})}
 st.innerHTML=ok==f.files.length?'<span class="ok">Done! '+ok+' file(s) are on your TV.</span>':'<span class="err">'+ok+' of '+f.files.length+' sent. Try the rest again.</span>';go.disabled=false}</script></body></html>"""
    private fun twHandle(sock: Socket) {
        sock.use { so ->
            so.soTimeout = 30000
            val inp = java.io.BufferedInputStream(so.getInputStream()); val out = so.getOutputStream()
            fun line(): String { val b = StringBuilder(); while (true) { val c = inp.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) b.append(c.toChar()) }; return b.toString() }
            val req = line().split(" "); if (req.size < 2) return
            var len = -1L; while (true) { val h = line(); if (h.isEmpty()) break; val i = h.indexOf(':'); if (i > 0 && h.substring(0, i).trim().equals("Content-Length", true)) len = h.substring(i + 1).trim().toLongOrNull() ?: -1 }
            fun send(code: String, type: String, body: ByteArray) { out.write(("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()); out.write(body); out.flush() }
            val path = req[1]
            if (req[0] == "GET" && (path == "/" || path.startsWith("/?"))) { send("200 OK", "text/html; charset=utf-8", TW_PAGE.toByteArray()); return }
            if (req[0] == "POST" && path.startsWith("/upload")) {
                val raw = Uri.parse("http://x$path").getQueryParameter("name") ?: "music.mp3"
                val name = raw.substringAfterLast('/').replace(Regex("[^A-Za-z0-9 ._()\\-]"), "_").take(80).ifBlank { "music.mp3" }
                if (len <= 0 || len > 300L * 1024 * 1024 || !Regex("\\.(mp3|m4a|aac|ogg|wav|flac|opus)$", RegexOption.IGNORE_CASE).containsMatchIn(name)) { send("400 Bad Request", "text/plain", "Unsupported file".toByteArray()); return }
                val tmp = File(twDir, ".up-" + System.nanoTime())
                tmp.outputStream().use { fo -> val buf = ByteArray(65536); var left = len; while (left > 0) { val n = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) break; fo.write(buf, 0, n); left -= n } }
                if (tmp.length() != len) { tmp.delete(); send("400 Bad Request", "text/plain", "Incomplete".toByteArray()); return }
                val dest = File(twDir, name); dest.delete(); tmp.renameTo(dest)
                send("200 OK", "application/json", "{\"ok\":true}".toByteArray()); twNotify(); return
            }
            send("404 Not Found", "text/plain", "Not found".toByteArray())
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

    // ---- Spotify (the Spotify app plays the music; NyxVision is the on-screen tape deck) ----
    private var spRemote: SpotifyAppRemote? = null
    private var spSub: Subscription<PlayerState>? = null
    private var spArtUri: String? = null
    private val spItems = ConcurrentHashMap<String, ListItem>()
    private fun spSend(fn: String, json: String) = runOnUiThread {
        web.evaluateJavascript("window.cable&&cable.$fn&&cable.$fn(" + JSONObject.quote(json) + ")", null)
    }
    private fun spOnState(st: PlayerState) {
        val o = JSONObject().put("paused", st.isPaused).put("pos", st.playbackPosition)
        val t = st.track
        if (t != null) {
            o.put("name", t.name ?: "").put("artist", t.artist?.name ?: "").put("album", t.album?.name ?: "")
                .put("dur", t.duration).put("uri", t.uri ?: "").put("albumUri", t.album?.uri ?: "")
            val iu = t.imageUri
            if (iu?.raw != null && iu.raw != spArtUri) {
                spArtUri = iu.raw
                try {
                    spRemote?.imagesApi?.getImage(iu, Image.Dimension.THUMBNAIL)?.setResultCallback { bmp ->
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 82, bos)
                        spSend("spArt", "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
                    }
                } catch (e: Exception) {}
            }
        }
        spSend("spPlayer", o.toString())
    }
    private fun spItemJson(it: ListItem): JSONObject {
        spItems[it.id] = it
        return JSONObject().put("id", it.id).put("uri", it.uri ?: "").put("title", it.title ?: "").put("sub", it.subtitle ?: "").put("play", it.playable)
    }

    override fun onDestroy() {
        try { twServer?.close() } catch (e: Exception) {}
        twStop()
        try { spSub?.cancel(); SpotifyAppRemote.disconnect(spRemote) } catch (e: Exception) {}
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
                    conn.setRequestProperty("User-Agent", "NyxVision/1.0 (personal hobby app)")
                    conn.setRequestProperty("Accept", "application/geo+json, application/json")
                    if (headersJson.isNotBlank()) {
                        val h = JSONObject(headersJson)
                        // "__timeout" (ms) lets slow services (like OpenStreetMap's Overpass) take longer; it isn't sent as a header
                        for (k in h.keys()) { if (k == "__timeout") conn.readTimeout = h.optInt(k, 30000) else conn.setRequestProperty(k, h.getString(k)) }
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

        /** Plays one progressive MP4 (Nyx Classic Theater), starting partway in so it runs like live TV. */
        @JavascriptInterface
        fun playMovie(url: String, startMs: Long) {
            runOnUiThread {
                streamUrls = listOf(url)
                streamIndex = 0
                moviePos = startMs
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

        /** Text the owner copied on this device (used by the Paste button when entering a service key). */
        @JavascriptInterface
        fun clipText(): String {
            val latch = java.util.concurrent.CountDownLatch(1)
            var out = ""
            runOnUiThread {
                try {
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = cm.primaryClip
                    if (clip != null && clip.itemCount > 0) out = clip.getItemAt(0).coerceToText(this@MainActivity)?.toString() ?: ""
                } catch (e: Exception) {}
                latch.countDown()
            }
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            return out
        }

        /** Plays a short one-shot sound (alert chime, race-call fanfare) that NyxVision wrote with jazzBegin/jazzChunk. */
        @JavascriptInterface
        fun clipPlay(style: String) {
            runOnUiThread {
                val f = musicFile(style)
                cuePlayer?.let { try { it.release() } catch (e: Exception) {} }
                cuePlayer = null
                if (!f.exists()) return@runOnUiThread   // clipPlay("none") just stops the current cue
                val mp = MediaPlayer()
                try {
                    mp.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    mp.setDataSource(f.absolutePath)
                    mp.setOnPreparedListener { it.start() }
                    mp.setOnCompletionListener { it.release(); if (cuePlayer === it) cuePlayer = null }
                    mp.setOnErrorListener { m, _, _ -> m.release(); if (cuePlayer === m) cuePlayer = null; true }
                    mp.prepareAsync()
                    cuePlayer = mp
                } catch (e: Exception) { mp.release() }
            }
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
        fun spotifyInstalled(): Boolean = SpotifyAppRemote.isSpotifyInstalled(this@MainActivity)

        /** Connect to the Spotify app with the owner's own Client ID. The first time, Spotify asks them to agree. */
        @JavascriptInterface
        fun spotifyConnect(clientId: String) {
            runOnUiThread {
                val r0 = spRemote
                if (r0 != null && r0.isConnected) { spSend("spState", JSONObject().put("ok", true).toString()); return@runOnUiThread }
                try { SpotifyAppRemote.disconnect(r0) } catch (e: Exception) {}
                try {
                val params = ConnectionParams.Builder(clientId.trim())
                    .setRedirectUri("http://127.0.0.1:8898/callback").showAuthView(true).build()
                SpotifyAppRemote.connect(this@MainActivity, params, object : Connector.ConnectionListener {
                    override fun onConnected(r: SpotifyAppRemote) {
                        spRemote = r
                        spSend("spState", JSONObject().put("ok", true).toString())
                        try { spSub?.cancel() } catch (e: Exception) {}
                        spSub = r.playerApi.subscribeToPlayerState().setEventCallback { st -> spOnState(st) }
                    }
                    override fun onFailure(e: Throwable) {
                        spRemote = null
                        spSend("spState", JSONObject().put("err", e.javaClass.simpleName).put("msg", e.message ?: "").toString())
                    }
                })
                } catch (e: Throwable) {
                    spSend("spState", JSONObject().put("err", e.javaClass.simpleName).put("msg", e.message ?: "").toString())
                }
            }
        }

        /** play/pause/resume/next/prev/shuffle/item (item = play a shelf item by id) */
        @JavascriptInterface
        fun spotifyCmd(cmd: String, arg: String) {
            runOnUiThread {
                val r = spRemote ?: return@runOnUiThread
                try {
                    val p = r.playerApi
                    when (cmd) {
                        "play" -> p.play(arg)
                        "pause" -> p.pause()
                        "resume" -> p.resume()
                        "next" -> p.skipNext()
                        "prev" -> p.skipPrevious()
                        "shuffle" -> p.setShuffle(arg == "1")
                        "item" -> { val it = spItems[arg]; if (it != null) r.contentApi.playContentItem(it) else if (arg.startsWith("spotify:")) p.play(arg) }
                        else -> {}
                    }
                } catch (e: Exception) {}
            }
        }

        /** The listener's shelves (recently played, playlists, ...) from the Spotify app, sent back as cable.spList(json). */
        @JavascriptInterface
        fun spotifyLibrary() {
            runOnUiThread {
                val r = spRemote ?: return@runOnUiThread
                r.contentApi.getRecommendedContentItems(ContentApi.ContentType.DEFAULT).setResultCallback { top ->
                    val items = top.items ?: arrayOf()
                    val secs = arrayOfNulls<JSONObject>(items.size)
                    val left = AtomicInteger(items.size)
                    fun done() { if (left.decrementAndGet() == 0) { val a = JSONArray(); secs.forEach { if (it != null) a.put(it) }; spSend("spList", a.toString()) } }
                    if (items.isEmpty()) spSend("spList", "[]")
                    items.forEachIndexed { i, it ->
                        if (it.playable && !it.hasChildren) {
                            secs[i] = JSONObject().put("title", it.title ?: "").put("items", JSONArray().put(spItemJson(it))); done()
                        } else {
                            r.contentApi.getChildrenOfItem(it, 24, 0).setResultCallback { ch ->
                                val arr = JSONArray()
                                (ch.items ?: arrayOf()).forEach { c -> if (c.playable) arr.put(spItemJson(c)) }
                                secs[i] = JSONObject().put("title", it.title ?: "").put("items", arr); done()
                            }.setErrorCallback { done() }
                        }
                    }
                }.setErrorCallback { e -> spSend("spList", JSONObject().put("err", e.javaClass.simpleName).toString()) }
            }
        }

        /** Settings → Disconnect Spotify: stop talking to the Spotify app and forget everything from it. */
        @JavascriptInterface
        fun spotifyDisconnect() {
            runOnUiThread {
                try { spSub?.cancel() } catch (e: Exception) {}
                spSub = null
                try { SpotifyAppRemote.disconnect(spRemote) } catch (e: Exception) {}
                spRemote = null
                spItems.clear()
                spArtUri = null
            }
        }

        /** "Open in Spotify": the album (or track) in the Spotify app. */
        @JavascriptInterface
        fun spotifyOpen(uri: String) {
            runOnUiThread {
                if (!uri.startsWith("spotify:")) return@runOnUiThread
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.spotify.music")) }
                catch (e: Exception) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (e2: Exception) {} }
            }
        }

        @JavascriptInterface
        fun openSpotify() {
            runOnUiThread {
                val pm = packageManager
                val i = listOf("com.spotify.tv.android", "com.spotify.music").firstNotNullOfOrNull { pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it) }
                try { if (i != null) startActivity(i) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music"))) } catch (e: Exception) {}
            }
        }

        /** Twilight music upload page: starts a small web server on this device's Wi-Fi address; returns "http://ip:port/" or "" */
        @JavascriptInterface
        fun twServe(on: Boolean): String {
            if (!on) { try { twServer?.close() } catch (e: Exception) {}; twServer = null; return "" }
            val ip = localIp(); if (ip.isEmpty()) return ""
            if (twServer == null) {
                fun bindOn(port: Int): ServerSocket? = try { ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(port)) } } catch (e: Exception) { null }
                val ss = bindOn(8088) ?: bindOn(8089) ?: return ""
                twServer = ss
                Thread { while (!ss.isClosed) { try { val c = ss.accept(); Thread { try { twHandle(c) } catch (e: Exception) {} }.start() } catch (e: Exception) { break } } }.start()
            }
            return "http://" + ip + ":" + (twServer?.localPort ?: 8088) + "/"
        }
        @JavascriptInterface fun twFiles(): String = twFilesJson()
        @JavascriptInterface fun twClear() { twList().forEach { it.delete() }; runOnUiThread { twStop() }; twNotify() }
        /** Play (true) or stop (false) the owner's Twilight music, in filename order, looping. */
        @JavascriptInterface fun twPlay(on: Boolean) { runOnUiThread { if (on) { if (twPlayer == null) twStart() } else twStop() } }

        /** On-screen remote VOL/MUTE: 1 = up, -1 = down, 0 = mute on/off. Shows Android's own volume slider. */
        @JavascriptInterface
        fun volume(dir: Int) {
            runOnUiThread {
                try {
                    val am = getSystemService(AUDIO_SERVICE) as AudioManager
                    val adj = when { dir > 0 -> AudioManager.ADJUST_RAISE; dir < 0 -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_TOGGLE_MUTE }
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adj, AudioManager.FLAG_SHOW_UI)
                } catch (e: Exception) {}
            }
        }

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

        /** Opens a news story in the device's web browser (tablet). False if there's no browser. */
        @JavascriptInterface
        fun openUrl(url: String): Boolean {
            if (!url.startsWith("http")) return false
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) == null) return false
            runOnUiThread { try { startActivity(intent) } catch (e: Exception) {} }
            return true
        }

        /** Opens a web link (e.g. a YouTube search) in the first installed app from the list. False if none took it. */
        @JavascriptInterface
        fun openLink(pkgsJson: String, url: String): Boolean {
            val arr = JSONArray(pkgsJson)
            for (i in 0 until arr.length()) {
                val pkg = arr.getString(i)
                if (packageManager.getLaunchIntentForPackage(pkg) == null &&
                    packageManager.getLeanbackLaunchIntentForPackage(pkg) == null) continue
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(packageManager) != null) {
                    runOnUiThread { try { startActivity(intent) } catch (e: Exception) {} }
                    return true
                }
            }
            return false
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
