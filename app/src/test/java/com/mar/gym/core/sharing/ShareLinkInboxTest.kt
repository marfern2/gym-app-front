package com.mar.gym.core.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkInboxTest {
    private val shareId = "62b820ec-960f-4e25-8ae4-70dab3e84486"
    private val url = "http://localhost:8080/r/$shareId"
    private fun inbox() = ShareLinkInbox(ShareLinks("http://10.0.2.2:8080",
        listOf("http://localhost:8080")))

    @Test fun `cold start link waits for login and opens shared routine once`() {
        val inbox = inbox()
        inbox.accept(url, null)
        assertEquals(ShareDeepLink.Routine(shareId), inbox.pending.value?.target)
        assertNull(inbox.consumeFor("B"))
        inbox.bindTo("B")
        assertEquals(ShareDeepLink.Routine(shareId), inbox.consumeFor("B"))
        assertNull(inbox.consumeFor("B"))
    }

    @Test fun `new intent targets current authenticated user`() {
        val inbox = inbox()
        inbox.accept(url, "B")
        assertEquals(ShareDeepLink.Routine(shareId), inbox.consumeFor("B"))
    }

    @Test fun `invalid new intent does not replace a valid pending destination`() {
        val inbox = inbox()
        inbox.accept(url, null)
        inbox.accept("https://evil.test/r/$shareId", null)
        inbox.bindTo("B")
        assertEquals(ShareDeepLink.Routine(shareId), inbox.consumeFor("B"))
    }

    @Test fun `pending link owned by A cannot leak into B`() {
        val inbox = inbox()
        inbox.accept(url, "A")
        inbox.bindTo("B")
        assertNull(inbox.consumeFor("B"))
        inbox.accept(url, "A")
        inbox.clearBound()
        assertNull(inbox.pending.value)
    }
}
