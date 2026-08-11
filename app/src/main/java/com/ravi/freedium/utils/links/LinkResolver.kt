package com.ravi.freedium.utils.links

import com.ravi.freedium.utils.log.FreediumLog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a short or redirecting Medium link into the canonical article URL.
 *
 * `https://medium.com/p/<postId>` is what we can build from a notification, but it is a
 * redirect stub - not something you would want to share or see in the address bar. Walking
 * the redirects gives the real thing:
 *
 *     https://medium.com/p/25a5afe2b71c
 *       -> https://medium.com/blogging-guide/understanding-canonical-links-...-25a5afe2b71c
 *
 * Redirects are followed by hand rather than letting HttpURLConnection do it, so we can cap
 * the hops and read the final URL without pulling the article body down.
 */
object LinkResolver {

    private const val TAG = "LinkResolver"
    private const val MAX_HOPS = 5
    private const val TIMEOUT_MS = 10_000
    private const val MAX_HEAD_CHARS = 200_000

    /** Medium serves different markup to unknown agents; look like a mobile browser. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Returns the canonical URL, or the input unchanged if it is already canonical or the
     * network is unavailable. Never throws - an unresolved link is still a usable link.
     */
    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        var current = url

        repeat(MAX_HOPS) {
            val next = hop(current) ?: return@repeat
            FreediumLog.d(TAG, "redirect: $current -> $next")
            current = next
        }

        current
    }

    /** One redirect step. Returns null when [url] is already the final destination. */
    private fun hop(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                requestMethod = "HEAD"
            }

            var status = connection.responseCode

            // Some hosts reject HEAD outright - retry the same URL as a GET.
            if (status == HttpURLConnection.HTTP_BAD_METHOD) {
                connection.disconnect()
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                status = connection.responseCode
            }

            if (status !in 300..399) return null

            val location = connection.getHeaderField("Location") ?: return null
            // Location may be relative; resolve it against the URL we just asked for.
            URL(URL(url), location).toString()
        } catch (e: Exception) {
            FreediumLog.w(TAG, "Could not resolve $url: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Whether [url] is a short link worth expanding before we hand it to a Custom Tab.
     *
     * Only genuine redirectors qualify. In particular `medium.com/p/<postId>` does **not**:
     * it never redirects - it answers 200 and names the pretty URL in a
     * `<link rel="canonical">` tag - and Medium returns **403 to HttpURLConnection**
     * however browser-like the headers are (verified on device with a mobile-Chrome UA
     * plus Accept / Sec-Fetch-*). Cloudflare keys off far more than headers.
     *
     * Reading that canonical tag needs a real browser, and the Custom Tab is one: it opens
     * the /p/ stub perfectly well and shows the canonical URL in its own address bar. So
     * there is nothing left for this class to do there, and attempting it would only buy a
     * guaranteed 403 on every capture.
     */
    fun needsResolving(url: String?): Boolean {
        if (url == null) return false
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()?.lowercase()
        return host == "link.medium.com"
    }
}
