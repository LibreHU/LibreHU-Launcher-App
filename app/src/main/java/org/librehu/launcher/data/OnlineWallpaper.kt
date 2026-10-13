package org.librehu.launcher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale

/**
 * Internet wallpaper ([WallpaperKind.ONLINE]): Bing's picture of the day (for a market), Wikimedia Commons' picture
 * of the day, or a random photo from Lorem Picsum. Renewed by [refreshIfDue] (launcher start, back to the front,
 * hourly while it runs) when [OnlineRefresh] says so; the last picture stays without network.
 */
object OnlineWallpaper {
    private const val TAG = "LibreHU-Wallpaper"
    private const val TIMEOUT_MS = 20_000
    private const val MAX_BYTES = 30L * 1024 * 1024
    private val lock = Mutex()
    private var fetchedThisStart = false

    /** Bing markets offered in the settings ("auto" first: the device's language and country). */
    val BING_MARKETS = listOf("auto", "fr-FR", "fr-CA", "en-US", "en-GB", "de-DE", "es-ES", "it-IT", "ja-JP", "zh-CN", "pt-BR")

    /** Renews the picture when it is due; [force] = now (settings button). False when nothing could be fetched. */
    suspend fun refreshIfDue(
        context: Context,
        store: ThemeStore,
        force: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val s = store.settings.value
            if (s.wallpaper != WallpaperKind.ONLINE) return@withContext false
            lock.withLock {
                if (!force && !due(s.onlineRefresh, store.onlineFetchedAt)) return@withLock true
                val tmp = File(context.cacheDir, "wallpaper_online")
                try {
                    val caption = download(s, tmp) ?: return@withLock false
                    val ok = store.setOnline(tmp, caption)
                    if (ok) fetchedThisStart = true
                    ok
                } catch (e: Exception) {
                    Log.w(TAG, "${s.onlineSource}: ${e.message}")
                    false
                } finally {
                    tmp.delete()
                }
            }
        }

    private fun due(
        refresh: OnlineRefresh,
        last: Long,
    ): Boolean {
        if (last == 0L) return true
        val now = System.currentTimeMillis()
        return when (refresh) {
            OnlineRefresh.EACH_START -> !fetchedThisStart
            OnlineRefresh.SIX_HOURS -> now - last >= 6 * 3600_000L
            OnlineRefresh.DAILY -> day(now) != day(last)
        }
    }

    private fun day(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

    /** Downloads the picture into [to]; returns its caption, or null. */
    private fun download(
        s: ThemeSettings,
        to: File,
    ): String? =
        when (s.onlineSource) {
            OnlineSource.BING -> {
                val market = if (s.bingMarket == "auto") Locale.getDefault().toLanguageTag() else s.bingMarket
                val json = JSONObject(text("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=$market"))
                val img = json.getJSONArray("images").getJSONObject(0)
                // urlbase + size: 1920x1080 is offered for every picture (UHD is much bigger than the screen).
                val base = img.optString("urlbase")
                val url = if (base.isNotEmpty()) "https://www.bing.com${base}_1920x1080.jpg" else "https://www.bing.com" + img.getString("url")
                fetch(url, to)
                listOf(img.optString("title"), img.optString("copyright")).filter { it.isNotBlank() }.joinToString(" · ")
            }

            OnlineSource.WIKIMEDIA -> {
                val c = Calendar.getInstance()
                val date = String.format(Locale.ROOT, "%04d/%02d/%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                val json = JSONObject(text("https://api.wikimedia.org/feed/v1/wikipedia/en/featured/$date"))
                val img = json.getJSONObject("image")
                // The full file can be tens of megabytes: the thumbnail address, widened to the screen.
                val thumb = img.optJSONObject("thumbnail")?.optString("source").orEmpty()
                val full = img.optJSONObject("image")?.optString("source").orEmpty()
                val url = if (thumb.contains(Regex("/\\d+px-"))) thumb.replace(Regex("/\\d+px-"), "/1920px-") else full.ifEmpty { thumb }
                fetch(url, to)
                val artist = img.optJSONObject("artist")?.optString("text").orEmpty()
                val desc = img.optJSONObject("description")?.optString("text").orEmpty()
                listOf(desc.take(120), artist.ifEmpty { "Wikimedia Commons" }).filter { it.isNotBlank() }.joinToString(" · ")
            }

            OnlineSource.PICSUM -> {
                fetch("https://picsum.photos/1920/1080", to)
                "Lorem Picsum · Unsplash"
            }
        }

    private fun open(url: String): HttpURLConnection {
        var u = URL(url)
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "LibreHU-Launcher (https://github.com/LibreHU/LibreHU-Launcher-App)")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw java.io.IOException("redirect")
                c.disconnect()
                u = URL(u, loc)
                if (u.protocol != "https") throw java.io.IOException("not https")
            } else {
                if (code !in 200..299) throw java.io.IOException("HTTP $code")
                return c
            }
        }
        throw java.io.IOException("redirects")
    }

    private fun text(url: String): String = open(url).let { c -> c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.also { c.disconnect() } }

    private fun fetch(
        url: String,
        to: File,
    ) {
        val c = open(url)
        try {
            var total = 0L
            c.inputStream.use { i ->
                to.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw java.io.IOException("too big")
                        o.write(buf, 0, n)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
