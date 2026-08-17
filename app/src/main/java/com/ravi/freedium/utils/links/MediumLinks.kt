package com.ravi.freedium.utils.links

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel

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
 * That matters because Medium's notification does not carry an http link at all. Its
 * contentIntent is an internal deep link or an explicit component with an id buried in the
 * extras. We do not need the URL: we need the id.
 *
 * Crucially the search is **recursive**. Notification and Intent payloads routinely nest a
 * child Bundle (or a JSON blob) under a single key, and calling toString() on one yields
 * `Bundle[mParcelledData.dataSize=248]` - the id is in there and a flat scan throws it
 * away. That was the original reason nothing was ever recovered.
 */
object MediumLinks {

    const val MEDIUM_PACKAGE = "com.medium.reader"

    private const val MAX_DEPTH = 4

    private val HTTP_URL = Regex("""https?://[^\s"'<>)\]}\\]+""")

    /**
     * Medium post ids are lowercase hex, in practice exactly 12 characters, and appear as
     * the trailing segment of every article URL.
     */
    private val POST_ID_EXACT = Regex("""^[0-9a-f]{8,16}$""")
    private val POST_ID_TRAILING = Regex("""-([0-9a-f]{8,16})(?:\?.*)?$""")

    /** A standalone 12-hex token anywhere in a blob of text - the last-resort sweep. */
    private val POST_ID_LOOSE = Regex("""(?<![0-9a-zA-Z])([0-9a-f]{12})(?![0-9a-zA-Z])""")

    /** Extras keys most likely to hold the post id or a link. */
    private val ID_KEY_HINT = Regex(
        "(post|story|article|reference|entity|target|item|source|content|deep|link|uri|url|slug)",
        RegexOption.IGNORE_CASE
    )

    /** Keys whose values are ids of something other than a post - never treat as one. */
    private val ID_KEY_DENY = Regex(
        "(user|author|creator|collection|channel|session|device|token|campaign|trace|request)",
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
     * Ordered most-trustworthy first, so a real URL always beats a guessed id.
     */
    fun resolve(intent: Intent?): ResolvedLink? {
        if (intent == null) return null

        // 1. A real http(s) link as the data URI.
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

        // 3. Everything in the extras, nested Bundles included.
        resolveFromBundle(intent.extras, "intent.extras")?.let { return it }

        // 4. The flattened Intent as text - catches primitives toUri() encodes.
        val flat = runCatching { intent.toUri(0) }.getOrNull()
        firstHttpUrl(flat)?.let { return ResolvedLink(it, "intent.toUri()") }

        return null
    }

    /** Same ladder over a bundle, used for notification extras. */
    fun resolveFromBundle(extras: Bundle?, label: String): ResolvedLink? {
        if (extras == null) return null

        val values = flatten(extras, label)

        // a) a real link anywhere
        for ((path, value) in values) {
            firstHttpUrl(value)?.let { return ResolvedLink(it, path) }
        }

        // b) a deep-link URI carrying an id
        for ((path, value) in values) {
            if (!value.contains("://")) continue
            val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: continue
            postIdFromUri(uri)?.let {
                return ResolvedLink(urlForPostId(it), "$path uri id=$it")
            }
        }

        // c) an id under a plausibly-named key
        for ((path, value) in values) {
            val key = path.substringAfterLast('[').substringBefore(']')
            if (!ID_KEY_HINT.containsMatchIn(key) || ID_KEY_DENY.containsMatchIn(key)) continue
            val trimmed = value.trim()
            if (POST_ID_EXACT.matches(trimmed)) {
                return ResolvedLink(urlForPostId(trimmed), "$path id=$trimmed")
            }
            POST_ID_TRAILING.find(trimmed)?.let { match ->
                val id = match.groupValues[1]
                return ResolvedLink(urlForPostId(id), "$path trailing id=$id")
            }
        }

        // d) last resort: a standalone 12-hex token under any key we have not ruled out.
        // Loose enough to be wrong occasionally, which is why the source is always shown.
        for ((path, value) in values) {
            val key = path.substringAfterLast('[').substringBefore(']')
            if (ID_KEY_DENY.containsMatchIn(key)) continue
            POST_ID_LOOSE.find(value)?.let { match ->
                val id = match.groupValues[1]
                return ResolvedLink(urlForPostId(id), "$path loose id=$id")
            }
        }

        return null
    }

    /**
     * Walks a bundle into a flat list of path -> text pairs, descending into nested
     * Bundles and collections. This is the part that was missing.
     */
    fun flatten(bundle: Bundle?, label: String, depth: Int = 0): List<Pair<String, String>> {
        if (bundle == null || depth > MAX_DEPTH) return emptyList()

        // keySet() forces an unparcel, which throws when the bundle carries classes this
        // process does not have. Callers fall back to the raw parcel bytes.
        val keys = runCatching { bundle.keySet().orEmpty() }.getOrNull() ?: return emptyList()

        val out = mutableListOf<Pair<String, String>>()
        for (key in keys) {
            val value = runCatching { valueOf(bundle, key) }.getOrNull() ?: continue
            val path = "$label[$key]"

            when (value) {
                is Bundle -> out += flatten(value, path, depth + 1)

                is Array<*> -> value.forEachIndexed { i, item ->
                    when (item) {
                        is Bundle -> out += flatten(item, "$path[$i]", depth + 1)
                        null -> Unit
                        else -> out += "$path[$i]" to item.toString()
                    }
                }

                is Iterable<*> -> value.forEachIndexed { i, item ->
                    when (item) {
                        is Bundle -> out += flatten(item, "$path[$i]", depth + 1)
                        null -> Unit
                        else -> out += "$path[$i]" to item.toString()
                    }
                }

                else -> out += path to value.toString()
            }
        }
        return out
    }

    /**
     * Reads the strings out of an Intent's extras **without unparcelling them**.
     *
     * This is the crux of why recovery kept failing. Medium's contentIntent targets
     * `com.medium.android.donkey.push.TrampolineActivity` and carries extras containing
     * Medium's own Parcelable classes. Our process does not have those classes, so the
     * moment anything calls keySet() Android tries to unmarshal, hits
     * ClassNotFoundException, logs "Failed to parse Bundle" and **quietly empties the
     * Bundle**. The Intent still reports "(has extras)" while every key has been thrown
     * away - which is exactly what we were seeing.
     *
     * Writing the Bundle back into a Parcel takes a fast path that copies the original
     * bytes verbatim, no unmarshalling involved. We can then pull the UTF-16 strings out
     * of those raw bytes and find the post id that was there all along.
     *
     * Must be called before anything else touches the extras.
     */
    fun rawStringsFrom(intent: Intent?): List<String> {
        val extras = runCatching { intent?.extras }.getOrNull() ?: return emptyList()

        val bytes = runCatching {
            val parcel = Parcel.obtain()
            try {
                extras.writeToParcel(parcel, 0)
                parcel.marshall()
            } finally {
                parcel.recycle()
            }
        }.getOrNull() ?: return emptyList()

        return printableStrings(bytes)
    }

    /**
     * Pulls UTF-16 runs out of marshalled parcel data. Parcel stores strings as 2-byte
     * little-endian chars aligned to 4 bytes, so both even and odd starting offsets are
     * swept to avoid missing a run that begins mid-word.
     */
    fun printableStrings(bytes: ByteArray, minLength: Int = 4): List<String> {
        val found = LinkedHashSet<String>()

        for (start in 0..1) {
            val builder = StringBuilder()
            var i = start
            while (i + 1 < bytes.size) {
                val low = bytes[i].toInt() and 0xFF
                val high = bytes[i + 1].toInt() and 0xFF
                if (high == 0 && low in 0x20..0x7E) {
                    builder.append(low.toChar())
                } else {
                    if (builder.length >= minLength) found += builder.toString()
                    builder.setLength(0)
                }
                i += 2
            }
            if (builder.length >= minLength) found += builder.toString()
        }

        return found.toList()
    }

    /** The same ladder, applied to strings recovered from raw parcel bytes. */
    fun resolveFromStrings(strings: List<String>, label: String): ResolvedLink? {
        for (value in strings) {
            firstHttpUrl(value)?.let { return ResolvedLink(it, "$label (http)") }
        }
        for (value in strings) {
            if (!value.contains("://")) continue
            val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: continue
            postIdFromUri(uri)?.let {
                return ResolvedLink(urlForPostId(it), "$label uri id=$it")
            }
        }
        for (value in strings) {
            val trimmed = value.trim()
            if (POST_ID_EXACT.matches(trimmed) && trimmed.length == 12) {
                return ResolvedLink(urlForPostId(trimmed), "$label id=$trimmed")
            }
            POST_ID_TRAILING.find(trimmed)?.let { match ->
                val id = match.groupValues[1]
                return ResolvedLink(urlForPostId(id), "$label trailing id=$id")
            }
        }
        for (value in strings) {
            POST_ID_LOOSE.find(value)?.let { match ->
                val id = match.groupValues[1]
                return ResolvedLink(urlForPostId(id), "$label loose id=$id")
            }
        }
        return null
    }

    fun firstHttpUrl(text: String?): String? {
        if (text == null) return null
        return HTTP_URL.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
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
        val names = runCatching { uri.queryParameterNames }.getOrNull().orEmpty()
        for (name in names) {
            if (ID_KEY_DENY.containsMatchIn(name)) continue
            val value = runCatching { uri.getQueryParameter(name) }.getOrNull()?.trim() ?: continue
            if (POST_ID_EXACT.matches(value)) return value
            POST_ID_TRAILING.find(value)?.let { return it.groupValues[1] }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun valueOf(bundle: Bundle, key: String): Any? = bundle.get(key)
}
