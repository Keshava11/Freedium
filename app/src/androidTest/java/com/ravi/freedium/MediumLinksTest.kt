package com.ravi.freedium

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ravi.freedium.utils.links.MediumLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the shapes Medium's notification Intent might plausibly take. Instrumented
 * rather than a plain unit test because the resolution leans on android.net.Uri parsing.
 */
@RunWith(AndroidJUnit4::class)
class MediumLinksTest {

    private val postId = "abc123def456"
    private val canonical = "https://medium.com/p/$postId"

    private fun intentWithData(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun plainHttpsLinkIsUsedAsIs() {
        val url = "https://medium.com/@someone/a-real-title-$postId"
        val resolved = MediumLinks.resolve(intentWithData(url))
        assertEquals(url, resolved?.url)
    }

    @Test
    fun deepLinkWithBareIdPathBecomesCanonicalUrl() {
        val resolved = MediumLinks.resolve(intentWithData("medium://p/$postId"))
        assertEquals(canonical, resolved?.url)
    }

    @Test
    fun deepLinkWithSluggedPathBecomesCanonicalUrl() {
        val resolved = MediumLinks.resolve(intentWithData("medium://post/a-real-title-$postId"))
        assertEquals(canonical, resolved?.url)
    }

    @Test
    fun deepLinkWithQueryParamBecomesCanonicalUrl() {
        val resolved = MediumLinks.resolve(intentWithData("medium://open?postId=$postId"))
        assertEquals(canonical, resolved?.url)
    }

    @Test
    fun postIdInExtrasBecomesCanonicalUrl() {
        val intent = Intent().putExtra("postId", postId)
        assertEquals(canonical, MediumLinks.resolve(intent)?.url)
    }

    @Test
    fun deepLinkNestedInExtrasBecomesCanonicalUrl() {
        val intent = Intent().putExtra("deeplink", "medium://p/$postId")
        assertEquals(canonical, MediumLinks.resolve(intent)?.url)
    }

    @Test
    fun httpLinkNestedInExtrasWins() {
        val url = "https://medium.com/@someone/a-real-title-$postId"
        val intent = Intent().putExtra("targetUrl", url)
        assertEquals(url, MediumLinks.resolve(intent)?.url)
    }

    @Test
    fun explicitComponentWithIdExtraStillResolves() {
        // The most likely real shape: an explicit component plus an id, no URI at all.
        val intent = Intent()
            .setClassName("com.medium.reader", "com.medium.reader.PostActivity")
            .putExtra("post_id", postId)
        assertEquals(canonical, MediumLinks.resolve(intent)?.url)
    }

    /**
     * The case that made the whole feature fail in the field. Apps nest their notification
     * payload in a child Bundle; calling toString() on one yields
     * "Bundle[mParcelledData.dataSize=248]" and the id inside is lost.
     */
    @Test
    fun postIdInsideANestedBundleIsFound() {
        val payload = android.os.Bundle().apply { putString("postId", postId) }
        val intent = Intent()
            .setClassName("com.medium.reader", "com.medium.reader.PostActivity")
            .putExtra("payload", payload)

        assertEquals(canonical, MediumLinks.resolve(intent)?.url)
    }

    @Test
    fun postIdNestedTwoLevelsDeepIsFound() {
        val inner = android.os.Bundle().apply { putString("story_id", postId) }
        val outer = android.os.Bundle().apply { putBundle("data", inner) }
        val intent = Intent().putExtra("payload", outer)

        assertEquals(canonical, MediumLinks.resolve(intent)?.url)
    }

    @Test
    fun httpLinkInsideANestedBundleWins() {
        val url = "https://medium.com/@someone/a-real-title-$postId"
        val payload = android.os.Bundle().apply { putString("targetUrl", url) }
        val intent = Intent().putExtra("payload", payload)

        assertEquals(url, MediumLinks.resolve(intent)?.url)
    }

    @Test
    fun looseHexIsFoundEvenUnderAnUnhelpfulKey() {
        // Medium may name the key something we never guessed; a bare 12-hex token under a
        // key that is not on the deny list is still worth trying.
        val intent = Intent().putExtra("n", postId)
        assertEquals(canonical, MediumLinks.resolve(intent)?.url)
    }

    @Test
    fun userIdIsNeverMistakenForAPostId() {
        val payload = android.os.Bundle().apply { putString("userId", postId) }
        val intent = Intent().putExtra("payload", payload)
        assertNull(MediumLinks.resolve(intent))
    }

    /**
     * The Inspect dump is built from [MediumLinks.flatten]. If that stopped recursing, the
     * dump would show "Bundle[mParcelledData.dataSize=...]" and hide the one thing worth
     * reading - which is exactly the state the app shipped in.
     */
    @Test
    fun flattenExposesNestedBundleContentsRatherThanMParcelledData() {
        val inner = android.os.Bundle().apply { putString("postId", postId) }
        val extras = android.os.Bundle().apply {
            putString("android.title", "A title")
            putBundle("payload", inner)
        }

        val flattened = MediumLinks.flatten(extras, "extras")
        val rendered = flattened.joinToString("\n") { "${it.first} = ${it.second}" }

        assertTrue("nested key not exposed:\n$rendered", rendered.contains("extras[payload][postId]"))
        assertTrue("post id not exposed:\n$rendered", rendered.contains(postId))
        assertTrue("nested bundle was stringified:\n$rendered", !rendered.contains("mParcelledData"))
    }

    /**
     * The real-device failure. Medium's contentIntent targets TrampolineActivity and its
     * extras hold Medium's own Parcelable classes; our process cannot unmarshal them, so
     * Android empties the Bundle and every key vanishes while the Intent still reports
     * "(has extras)". Reading the raw parcel bytes recovers the payload anyway.
     */
    @Test
    fun postIdIsRecoveredFromRawParcelBytes() {
        val extras = android.os.Bundle().apply {
            putString("com.medium.android.postId", postId)
            putString("referrerSource", "push_notification")
        }
        val intent = Intent().putExtras(extras)

        val strings = MediumLinks.rawStringsFrom(intent)
        assertTrue("no strings recovered from parcel bytes", strings.isNotEmpty())
        assertTrue(
            "post id missing from recovered strings: $strings",
            strings.any { it.contains(postId) }
        )
        assertEquals(canonical, MediumLinks.resolveFromStrings(strings, "rawExtras")?.url)
    }

    @Test
    fun rawParcelStringsAlsoYieldAPlainHttpLink() {
        val url = "https://medium.com/@someone/a-real-title-$postId"
        val intent = Intent().putExtras(android.os.Bundle().apply { putString("deeplink", url) })

        val strings = MediumLinks.rawStringsFrom(intent)
        assertEquals(url, MediumLinks.resolveFromStrings(strings, "rawExtras")?.url)
    }

    @Test
    fun printableStringsIgnoresBinaryNoise() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x7F.toByte(), 0x00, 0x00)
        assertTrue(MediumLinks.printableStrings(bytes).isEmpty())
    }

    @Test
    fun unrelatedIntentResolvesToNothing() {
        val intent = intentWithData("clock-app://com.google.android.deskclock/timer/0/view")
            .putExtra("com.android.deskclock.extra.EVENT_LABEL", "Notification")
        assertNull(MediumLinks.resolve(intent))
    }

    @Test
    fun randomHexInUnrelatedExtraIsNotTreatedAsAPostId() {
        val intent = Intent().putExtra("session_token", "deadbeefcafe")
        assertNull(MediumLinks.resolve(intent))
    }
}
