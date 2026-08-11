package com.ravi.freedium

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ravi.freedium.utils.links.MediumLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
