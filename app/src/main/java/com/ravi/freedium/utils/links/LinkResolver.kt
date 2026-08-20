package com.ravi.freedium.utils.links

import com.ravi.freedium.utils.log.FreediumLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * What we learned about a link: where it really points, and whether it is paywalled.
 * [memberOnly] is null when we could not find out.
 */
data class Resolution(val url: String, val memberOnly: Boolean? = null)

/**
 * Turns a `medium.com/p/<postId>` stub into the canonical article URL, and works out
 * whether the article is member-only.
 *
 * Medium **does** redirect the stub - a correction to an earlier assumption here:
 *
 *     https://www.medium.com/p/826ebf9ad9fb -> 301 -> https://medium.com/p/826ebf9ad9fb
 *     https://medium.com/p/826ebf9ad9fb     -> 302 -> https://netflixtechblog.medium.com/...
 *
 * Note the "www." costs an extra hop, so the host is normalised before the walk begins and
 * the chain is followed rather than stopping at the first Location.
 *
 * Paywall status comes from the article page. Medium emits schema.org JSON-LD carrying
 * `"isAccessibleForFree":false` for member-only stories, alongside its own
 * `"isLocked":true`. Verified against a free and a locked article.
 *
 * Everything here fails soft. Medium sits behind Cloudflare and has been observed
 * returning 403 to HttpURLConnection from some networks; when that happens the caller
 * still gets a usable stub URL and a null paywall verdict rather than an error. Chrome
 * follows the redirect itself, so an unresolved stub still opens the right article.
 */
object LinkResolver {

    private const val TAG = "LinkResolver"
    private const val MAX_HOPS = 5
    private const val TIMEOUT_MS = 15_000
    private const val MAX_HEAD_CHARS = 400_000

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    // Both plain and backslash-escaped forms appear in Medium's markup.
    private val ACCESSIBLE_FOR_FREE =
        Regex("""\\?"isAccessibleForFree\\?"\s*:\s*(true|false)""")
    private val IS_LOCKED =
        Regex("""\\?"isLocked\\?"\s*:\s*(true|false)""")

    /** Whether a link is a stub worth expanding before we show or store it. */
    fun needsResolving(url: String?): Boolean {
        if (url == null) return false
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()?.lowercase()
            ?: return false
        val isMedium = host == "medium.com" || host.endsWith(".medium.com")
        return isMedium && (url.contains("/p/") || host == "link.medium.com")
    }

    /**
     * Follows the redirect chain and then reads the paywall markers off the final page.
     * Returns the input unchanged if anything goes wrong.
     */
    suspend fun resolve(url: String): Resolution = withContext(Dispatchers.IO) {
        var current = stripWww(url)

        repeat(MAX_HOPS) {
            val next = hop(current) ?: return@repeat
            if (next == current) return@repeat
            FreediumLog.d(TAG, "redirect: $current -> $next")
            current = next
        }

        val memberOnly = readPaywallFlag(current)
        FreediumLog.d(TAG, "resolved $url -> $current (memberOnly=$memberOnly)")
        Resolution(current, memberOnly)
    }

    /** `www.medium.com/p/x` 301s to `medium.com/p/x` - skip the wasted hop. */
    private fun stripWww(url: String): String = runCatching {
        val uri = android.net.Uri.parse(url)
        val host = uri.host ?: return url
        if (!host.lowercase().startsWith("www.")) return url
        uri.buildUpon().authority(host.substring(4)).build().toString()
    }.getOrDefault(url)

    /** One redirect step. Returns null when [url] is already the final destination. */
    private fun hop(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url, "HEAD")
            var status = connection.responseCode

            if (status == HttpURLConnection.HTTP_BAD_METHOD) {
                connection.disconnect()
                connection = open(url, "GET")
                status = connection.responseCode
            }

            if (status !in REDIRECT_CODES) return null

            val location = connection.getHeaderField("Location") ?: return null
            // Location may be relative; resolve it against the URL just requested.
            URL(URL(url), location).toString()
        } catch (e: Exception) {
            FreediumLog.w(TAG, "hop failed for $url: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Reads `isAccessibleForFree` / `isLocked` from the article page. Stops early once a
     * verdict is found rather than pulling the whole article down.
     */
    private fun readPaywallFlag(url: String): Boolean? {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url, "GET").apply { instanceFollowRedirects = true }
            if (connection.responseCode !in 200..299) {
                FreediumLog.w(TAG, "paywall check got HTTP ${connection.responseCode} for $url")
                return null
            }

            val buffer = StringBuilder()
            connection.inputStream.bufferedReader().use { reader ->
                val chunk = CharArray(16 * 1024)
                while (buffer.length < MAX_HEAD_CHARS) {
                    val read = reader.read(chunk)
                    if (read == -1) break
                    buffer.appendRange(chunk, 0, read)
                    if (ACCESSIBLE_FOR_FREE.containsMatchIn(buffer)) break
                }
            }

            val document = buffer.toString()
            ACCESSIBLE_FOR_FREE.find(document)?.let { return it.groupValues[1] == "false" }
            IS_LOCKED.find(document)?.let { return it.groupValues[1] == "true" }
            null
        } catch (e: Exception) {
            FreediumLog.w(TAG, "paywall check failed for $url: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = method
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
}
