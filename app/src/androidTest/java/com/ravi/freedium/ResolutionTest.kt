package com.ravi.freedium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ravi.freedium.utils.links.LinkResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers stub resolution and the member-only verdict.
 *
 * The network-facing tests assert only the soft contract - never throw, always hand back
 * something usable - because Medium sits behind Cloudflare and refuses some clients and
 * networks outright. They print what actually happened so a run can be read to find out
 * whether this device can reach Medium at all.
 */
@RunWith(AndroidJUnit4::class)
class ResolutionTest {

    private val netflixId = "826ebf9ad9fb"
    private val stub = "https://medium.com/p/$netflixId"

    @Test
    fun onlyMediumStubsAndShortLinksAreFlagged() {
        assertTrue(LinkResolver.needsResolving(stub))
        assertTrue(LinkResolver.needsResolving("https://www.medium.com/p/$netflixId"))
        assertTrue(LinkResolver.needsResolving("https://link.medium.com/aBcDeF"))
        // Already canonical - nothing to do.
        assertFalse(LinkResolver.needsResolving("https://medium.com/@a/title-$netflixId"))
        assertFalse(LinkResolver.needsResolving("https://example.com/p/abc"))
        assertFalse(LinkResolver.needsResolving(null))
    }

    @Test
    fun resolvingIsAlwaysSafeAndLossless() = runBlocking {
        val resolution = LinkResolver.resolve(stub)
        println("RESOLVED $stub -> ${resolution.url} memberOnly=${resolution.memberOnly}")

        assertTrue("must stay usable, was ${resolution.url}", resolution.url.startsWith("https://"))
        assertTrue("must keep the post id, was ${resolution.url}", resolution.url.contains(netflixId))
    }

    @Test
    fun theWwwHopIsSkipped() = runBlocking {
        val fromWww = LinkResolver.resolve("https://www.medium.com/p/$netflixId")
        println("RESOLVED www -> ${fromWww.url} memberOnly=${fromWww.memberOnly}")

        // Whatever happens, we must never come back still pointing at www - that was the
        // wasted 301 that made the original Python script return the wrong URL.
        assertFalse("still on www: ${fromWww.url}", fromWww.url.startsWith("https://www."))
    }

    @Test
    fun unreachableHostFallsBackToTheInput() = runBlocking {
        val bad = "https://this-host-does-not-exist-freedium.invalid/p/abc123def456"
        assertEquals(bad, LinkResolver.resolve(bad).url)
    }
}
