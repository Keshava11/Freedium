package com.ravi.freedium.utils.links

import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** A link we managed to build, plus how we got there - shown in the UI so it can be judged. */
data class ResolvedLink(val url: String, val source: String)

/**
 * Knows how Medium names things.
 *
 * The important fact: a Medium post id alone is enough to build a working URL.
 * `https://medium.com/p/<postId>` serves the article directly - it does not redirect, it
 * answers 200 and names the pretty URL in a `<link rel="canonical">` tag. Verified against
 * a real post (25a5afe2b71c -> medium.com/blogging-guide/understanding-canonical-links...).
 *
 * That matters because Medium's notification does not appear to carry an http link at all.
 * Its contentIntent is an internal deep link or an explicit component with an id in the
 * extras - which is why probing it reports "no URL". We do not need the URL: we need the
 * id - medium.com/p/<id> renders the article perfectly well in a Custom Tab.
 */
object MediumLinks {

    const val MEDIUM_PACKAGE = "com.medium.reader"

    private val HTTP_URL = Regex("""https?://[^\s"'<>)\]}\\]+""")

    /**
     * Medium post ids are lowercase hex, in practice 12 chars, and appear as the trailing
     * segment of every article URL. Kept reasonably tight to avoid matching arbitrary hex
     * blobs, and every hit is reported with its source so a wrong guess is visible.
     */
    private val POST_ID_EXACT = Regex("""^[0-9a-f]{8,16}$""")
    private val POST_ID_TRAILING = Regex("""-([0-9a-f]{8,16})$""")

    /** Extras keys most likely to hold the post id. */
    private val ID_KEY_HINT = Regex(
        "(post|story|article|reference|entity|target|deep|link|uri|url|slug)",
        RegexOption.IGNORE_CASE
    )

    fun urlForPostId(postId: String): String = "https://medium.com/p/$postId"

    fun isMediumUrl(url: String?): Boolean {
        val host = runCatching { Uri.parse(url ?: return false).host }.getOrNull()?.lowercase()
            ?: return false
        return host == "medium.com" || host.endsWith(".medium.com")
    }

    /**
     * Best effort link for an Intent recovered from a notification's PendingIntent.
     * Ordered most-trustworthy first.
     */
    fun resolve(intent: Intent?): ResolvedLink? {
        if (intent == null) return null

        // 1. A real http(s) link, either as the data URI or anywhere inside it.
        intent.data?.let { data ->
            val scheme = data.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                return ResolvedLink(data.toString(), "intent.data")
            }
        }
        firstHttpUrl(intent.dataString)?.let {
            return ResolvedLink(it, "intent.data (wrapped)")
        }

        // 2. A post id in the deep link path, e.g. medium://p/abc123def456.
        intent.data?.let { data ->
            postIdFromUri(data)?.let {
                return ResolvedLink(urlForPostId(it), "intent.data path (${data.scheme}) id=$it")
            }
        }

        // 3. Anything usable in the extras.
        resolveFromBundle(intent.extras, "intent.extras")?.let { return it }

        // 4. Last resort: flatten the whole Intent and scan the text.
        val flat = runCatching { intent.toUri(0) }.getOrNull()
        firstHttpUrl(flat)?.let { return ResolvedLink(it, "intent.toUri()") }
        postIdFromText(flat)?.let {
            return ResolvedLink(urlForPostId(it), "intent.toUri() id=$it")
        }

        return null
    }

    /** Same ladder over a plain bundle, used for notification extras. */
    fun resolveFromBundle(extras: Bundle?, label: String): ResolvedLink? {
        if (extras == null) return null

        val keys = extras.keySet().orEmpty()

        // http link in any extra
        for (key in keys) {
            firstHttpUrl(valueOf(extras, key))?.let {
                return ResolvedLink(it, "$label[$key]")
            }
        }

        // a deep link URI in any extra
        for (key in keys) {
            val value = valueOf(extras, key) ?: continue
            if (!value.contains("://")) continue
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: continue
            postIdFromUri(uri)?.let {
                return ResolvedLink(urlForPostId(it), "$label[$key] uri id=$it")
            }
        }

        // an id sitting in a plausibly-named extra
        for (key in keys) {
            if (!ID_KEY_HINT.containsMatchIn(key)) continue
            val value = valueOf(extras, key)?.trim() ?: continue
            if (POST_ID_EXACT.matches(value)) {
                return ResolvedLink(urlForPostId(value), "$label[$key] id=$value")
            }
            POST_ID_TRAILING.find(value)?.let { match ->
                val id = match.groupValues[1]
                return ResolvedLink(urlForPostId(id), "$label[$key] trailing id=$id")
            }
        }

        return null
    }

    fun firstHttpUrl(text: String?): String? {
        if (text == null) return null
        return HTTP_URL.find(text)?.value?.trimEnd('.', ',', ')')
    }

    /**
     * Pulls a post id out of a URI: either a bare hex path segment (medium://p/<id>) or
     * the trailing hash on a slug (.../some-title-<id>).
     */
    private fun postIdFromUri(uri: Uri): String? {
        val segments = runCatching { uri.pathSegments }.getOrNull().orEmpty()
        for (segment in segments.asReversed()) {
            if (POST_ID_EXACT.matches(segment)) return segment
            POST_ID_TRAILING.find(segment)?.let { return it.groupValues[1] }
        }
        // Some deep links carry it as a query parameter instead of a path segment.
        val names = runCatching { uri.queryParameterNames }.getOrNull().orEmpty()
        for (name in names) {
            if (!ID_KEY_HINT.containsMatchIn(name)) continue
            val value = runCatching { uri.getQueryParameter(name) }.getOrNull()?.trim() ?: continue
            if (POST_ID_EXACT.matches(value)) return value
            POST_ID_TRAILING.find(value)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun postIdFromText(text: String?): String? {
        if (text == null) return null
        // Only trust a trailing-slug id here; a bare hex scan over a flattened Intent
        // would match far too much.
        return POST_ID_TRAILING.find(text)?.groupValues?.get(1)
    }

    @Suppress("DEPRECATION")
    private fun valueOf(extras: Bundle, key: String): String? =
        runCatching { extras.get(key)?.toString() }.getOrNull()
}
