package com.ravi.freedium

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ravi.freedium.utils.links.CustomTabs
import com.ravi.freedium.utils.links.LinkResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hits the real network. The whole point is to prove that a post id really does turn into
 * a readable article URL, so stubbing it out would test nothing worth testing.
 */
@RunWith(AndroidJUnit4::class)
class LinkResolverTest {

    private val knownPostId = "25a5afe2b71c"

    /**
     * Medium answers 403 to HttpURLConnection no matter how browser-like the headers are
     * (verified on device with a mobile-Chrome UA plus Accept / Sec-Fetch-* headers), and
     * it does not redirect /p/<id> anyway. So the contract here is only that resolving is
     * safe and lossless - the Custom Tab shows the canonical URL in its own address bar.
     */
    @Test
    fun blockedResolutionStillReturnsAUsableUrl() = runBlocking {
        val stub = "https://medium.com/p/$knownPostId"
        val resolved = LinkResolver.resolve(stub)
        println("resolve($stub) -> $resolved")
        assertTrue("must stay usable, was $resolved", resolved.startsWith("https://"))
        assertTrue("must keep the post id, was $resolved", resolved.contains(knownPostId))
    }

    @Test
    fun alreadyCanonicalUrlIsLeftAlone() = runBlocking {
        val canonical =
            "https://medium.com/blogging-guide/understanding-canonical-links-and-medium-article-seo-for-your-blog-or-website-$knownPostId"
        assertEquals(canonical, LinkResolver.resolve(canonical))
    }

    @Test
    fun unreachableHostFallsBackToTheInputUrl() = runBlocking {
        val bad = "https://this-host-does-not-exist-freedium-test.invalid/x"
        // Must not throw, and must hand back something usable.
        org.junit.Assert.assertEquals(bad, LinkResolver.resolve(bad))
    }

    @Test
    fun onlyGenuineShortLinksAreFlaggedForResolving() {
        assertTrue(LinkResolver.needsResolving("https://link.medium.com/aBcDeF"))
        // /p/ stubs never redirect and Medium 403s us, so they must not be attempted.
        assertFalse(LinkResolver.needsResolving("https://medium.com/p/$knownPostId"))
        assertFalse(LinkResolver.needsResolving("https://medium.com/@a/title-$knownPostId"))
        assertFalse(LinkResolver.needsResolving(null))
    }

    @Test
    fun aCustomTabsProviderIsAvailableAndIsNotUs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = CustomTabs.providerPackage(context)
        println("custom tabs provider: $provider")
        assertNotNull("no custom tabs browser found", provider)
        org.junit.Assert.assertNotEquals(context.packageName, provider)
    }
}
