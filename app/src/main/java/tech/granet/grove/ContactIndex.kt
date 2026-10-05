package tech.granet.grove

import android.content.ContentResolver
import android.content.ContentUris
import android.content.res.Resources
import android.net.Uri
import android.os.CancellationSignal
import android.provider.ContactsContract

/** Android's aggregate provider includes Google, OEM, and synced contact accounts. */
internal object ContactIndex {
    data class Contact(val id: Long, val lookupKey: String, val name: String) {
        val searchName = Search.normalize(name.take(512))
        val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
    }
    data class Number(val value: String, val label: String)
    data class Channel(val id: Long, val mime: String, val label: String) {
        val uri: Uri get() = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id)
    }
    data class Details(val numbers: List<Number>, val channels: List<Channel>)

    /** Digits-only form of a phone number, used to collapse the same number stored in
     *  different formats (e.g. "917-669-7537" vs "9176697537"). */
    fun normalizeNumber(raw: String): String = raw.filter { it.isDigit() }

    /** One menu row per messaging app (and per business variant). WhatsApp syncs several
     *  data rows per contact (profile, voice call, video call) that must not each become
     *  a separate "Open in WhatsApp" row. */
    fun collapseChannels(channels: List<Channel>): List<Channel> =
        channels.distinctBy { it.label to it.mime.contains("w4b") }

    data class WhatsAppTarget(val packageName: String, val label: String)

    /** "Message via WhatsApp" targets for a contact. A target is offered only when that
     *  WhatsApp account actually synced data for this contact — never merely because the
     *  app is installed on this device and the contact has a phone number. */
    fun whatsAppTargets(channels: List<Channel>, installed: (String) -> Boolean): List<WhatsAppTarget> =
        listOf(
            WhatsAppTarget("com.whatsapp", "WhatsApp") to
                channels.any { it.label == "WhatsApp" && !it.mime.contains("w4b") },
            WhatsAppTarget("com.whatsapp.w4b", "WhatsApp Business") to
                channels.any { it.mime.contains("w4b") },
        ).filter { (target, contactHasIt) -> contactHasIt && installed(target.packageName) }
            .map { (target, _) -> target }

    fun load(resolver: ContentResolver, shouldContinue: () -> Boolean = { true },
             cancellation: CancellationSignal? = null): List<Contact> {
        val result = ArrayList<Contact>()
        resolver.query(ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null, cancellation)?.use { cursor ->
            while (shouldContinue() && cursor.moveToNext() && result.size < 50_000) {
                val name = cursor.getString(2)?.take(512)?.trim().orEmpty()
                val key = cursor.getString(1)
                if (name.isNotEmpty() && !key.isNullOrEmpty()) result.add(Contact(cursor.getLong(0), key, name))
            }
        } ?: error("The device's contacts provider is unavailable")
        return result
    }

    fun details(resolver: ContentResolver, resources: Resources, contact: Contact): Details {
        val numbers = ArrayList<Number>()
        val seenNumbers = HashSet<String>()
        val channels = linkedSetOf<Channel>()
        resolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
            "${ContactsContract.Data.CONTACT_ID}=?", arrayOf(contact.id.toString()), null)?.use { cursor ->
            while (cursor.moveToNext() && numbers.size + channels.size < 80) {
                val mime = cursor.getString(1) ?: continue
                when {
                    mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = cursor.getString(2)?.trim()?.take(128).orEmpty()
                        val digits = normalizeNumber(number)
                        if (number.isNotEmpty() && digits.isNotEmpty() && seenNumbers.add(digits)) {
                            val type = cursor.getInt(3)
                            val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                resources, type, cursor.getString(4)).toString().take(40)
                            numbers.add(Number(number, label))
                        }
                    }
                    mime.startsWith("vnd.android.cursor.item/vnd.com.whatsapp") ->
                        channels.add(Channel(cursor.getLong(0), mime.take(256), "WhatsApp"))
                    mime.contains("facebook", ignoreCase = true) || mime.contains("messenger", ignoreCase = true) ->
                        channels.add(Channel(cursor.getLong(0), mime.take(256), "Messenger"))
                }
            }
        } ?: error("The device's contact details provider is unavailable")
        return Details(numbers.toList(), channels.toList())
    }
}
