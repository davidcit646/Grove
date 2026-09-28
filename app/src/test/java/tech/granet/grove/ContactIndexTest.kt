package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactIndexTest {

    @Test fun normalizeNumberStripsFormatting() {
        assertEquals("9176697537", ContactIndex.normalizeNumber("917-669-7537"))
        assertEquals("9176697537", ContactIndex.normalizeNumber("9176697537"))
        assertEquals("15642096823", ContactIndex.normalizeNumber("+1-564-209-6823"))
        assertEquals("15642096823", ContactIndex.normalizeNumber("+1 (564) 209-6823"))
    }

    @Test fun sameNumberInDifferentFormatsSharesNormalizedForm() {
        assertEquals(
            ContactIndex.normalizeNumber("917-669-7537"),
            ContactIndex.normalizeNumber("9176697537"),
        )
    }

    private fun whatsAppChannel(id: Long, mime: String) =
        ContactIndex.Channel(id, mime, "WhatsApp")

    @Test fun collapseChannelsMergesWhatsAppRowVariants() {
        val channels = listOf(
            whatsAppChannel(1, "vnd.android.cursor.item/vnd.com.whatsapp.profile"),
            whatsAppChannel(2, "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"),
            whatsAppChannel(3, "vnd.android.cursor.item/vnd.com.whatsapp.video.call"),
        )
        val collapsed = ContactIndex.collapseChannels(channels)
        assertEquals(1, collapsed.size)
        assertEquals(1L, collapsed[0].id)
    }

    @Test fun collapseChannelsKeepsBusinessVariantSeparate() {
        val channels = listOf(
            whatsAppChannel(1, "vnd.android.cursor.item/vnd.com.whatsapp.profile"),
            ContactIndex.Channel(2, "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile", "WhatsApp"),
            ContactIndex.Channel(3, "vnd.android.cursor.item/vnd.com.facebook.messenger", "Messenger"),
        )
        assertEquals(3, ContactIndex.collapseChannels(channels).size)
    }

    @Test fun whatsAppTargetOfferedOnlyForContactsWithWhatsAppData() {
        val channels = listOf(
            whatsAppChannel(1, "vnd.android.cursor.item/vnd.com.whatsapp.profile"),
        )
        val targets = ContactIndex.whatsAppTargets(channels) { true }
        assertEquals(listOf(ContactIndex.WhatsAppTarget("com.whatsapp", "WhatsApp")), targets)
    }

    @Test fun whatsAppTargetHiddenWhenContactHasNoWhatsAppData() {
        // App installed on the device, but the contact never synced WhatsApp data:
        // no "Message via WhatsApp" row (the Austin Bannister case).
        val targets = ContactIndex.whatsAppTargets(emptyList()) { true }
        assertTrue(targets.isEmpty())
    }

    @Test fun whatsAppTargetHiddenWhenAppNotInstalled() {
        val channels = listOf(
            whatsAppChannel(1, "vnd.android.cursor.item/vnd.com.whatsapp.profile"),
        )
        assertTrue(ContactIndex.whatsAppTargets(channels) { false }.isEmpty())
    }

    @Test fun whatsAppBusinessTargetUsesBusinessPackage() {
        val channels = listOf(
            ContactIndex.Channel(1, "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile", "WhatsApp"),
        )
        val targets = ContactIndex.whatsAppTargets(channels) { true }
        assertEquals(listOf(ContactIndex.WhatsAppTarget("com.whatsapp.w4b", "WhatsApp Business")), targets)
    }
}
