package com.ravi.freedium

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ravi.freedium.utils.links.CustomTabs
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Custom Tabs is the only renderer this app has, so a provider must exist - and it must
 * never be Freedium itself, or opening a link would loop straight back into the app.
 */
@RunWith(AndroidJUnit4::class)
class CustomTabsTest {

    @Test
    fun aProviderIsAvailableAndIsNotUs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = CustomTabs.providerPackage(context)
        println("custom tabs provider: $provider")

        assertNotNull("no custom tabs browser found", provider)
        assertNotEquals(context.packageName, provider)
    }
}
