package tech.granet.grove

import android.content.ContentResolver
import android.content.ContentUris
import android.content.res.Resources
import android.net.Uri
import android.os.CancellationSignal
import android.provider.ContactsContract

/** Android's aggregate provider includes Google, OEM, and synced contact accounts. */
internal object ContactIndex {
    data class Contact(val id: Long, val lookupKey: String, val name: String, private val preparedName: String? = null) {
        val searchName = preparedName ?: Search.normalize(name.take(512))
        val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
    }
    data class Number(val value: String, val label: String)
    data class Channel(val id: Long, val mime: String, val label: String) {
        val uri: Uri get() = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id)
    }
    data class Details(val numbers: List<Number>, val channels: List<Channel>)

    /** Digits-only form of a phone number, used to collapse the same number stored in
     *  different formats (e.g. "917-669-7537" vs "9176697537"). */
    fun normalizeNumber(raw: String): String =
        (PortablePolicy.value("phoneDigits", org.json.JSONObject().put("text", raw)) as? String) ?: raw.filter { it.isDigit() }

    /** One menu row per messaging app (and per business variant). WhatsApp syncs several
     *  data rows per contact (profile, voice call, video call) that must not each become
     *  a separate "Open in WhatsApp" row. */
    private fun channelArgs(channels: List<Channel>) = org.json.JSONObject().put("items", org.json.JSONArray().apply {
        channels.forEach { put(org.json.JSONObject().put("label", it.label).put("mime", it.mime)) }
    })
    fun collapseChannels(channels: List<Channel>): List<Channel> {
        val value = PortablePolicy.value("channels", channelArgs(channels)) as? org.json.JSONArray
        if (value != null) {
            val order = (0 until value.length()).map(value::getInt)
            if (order.distinct().size == order.size && order.all { it in channels.indices }) return order.map(channels::get)
        }
        return channels.distinctBy { it.label to it.mime.contains("w4b") }
    }

    data class WhatsAppTarget(val packageName: String, val label: String)

    /** "Message via WhatsApp" targets for a contact. A target is offered only when that
     *  WhatsApp account actually synced data for this contact — never merely because the
     *  app is installed on this device and the contact has a phone number. */
    fun whatsAppTargets(channels: List<Channel>, installed: (String) -> Boolean): List<WhatsAppTarget> {
        val matches = (PortablePolicy.value("whatsApp", channelArgs(channels)) as? org.json.JSONArray)
            ?.takeIf { it.length() == 2 && it.get(0) is Boolean && it.get(1) is Boolean }
        return listOf(
            WhatsAppTarget("com.whatsapp", "WhatsApp") to
                (matches?.optBoolean(0) ?: channels.any { it.label == "WhatsApp" && !it.mime.contains("w4b") }),
            WhatsAppTarget("com.whatsapp.w4b", "WhatsApp Business") to
                (matches?.optBoolean(1) ?: channels.any { it.mime.contains("w4b") }),
        ).filter { (target, contactHasIt) -> contactHasIt && installed(target.packageName) }
            .map { (target, _) -> target }
    }

    data class ScanResult(val contacts: List<Contact>, val truncated: Boolean)

    private val deadlines = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    fun load(resolver: ContentResolver, shouldContinue: () -> Boolean = { true },
             cancellation: CancellationSignal? = null, maxDurationMs: Long = Long.MAX_VALUE,
             maxRawRows: Int = 100_000): ScanResult {
        data class Raw(val id: Long, val key: String, val name: String)
        val result = ArrayList<Raw>()
        val signal = cancellation ?: CancellationSignal()
        val budget = ContactScanBudget(maxRawRows, maxDurationMs, android.os.SystemClock::elapsedRealtime)
        val deadline = if (maxDurationMs == Long.MAX_VALUE) null else
            deadlines.schedule({ signal.cancel() }, maxDurationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        var partial = false
        try {
            if (!shouldContinue()) throw android.os.OperationCanceledException()
            resolver.query(ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null, signal)?.use { cursor ->
                while (true) {
                    if (!shouldContinue()) throw android.os.OperationCanceledException()
                    signal.throwIfCanceled()
                    if (budget.exhausted() || result.size >= 50_000) { partial = true; break }
                    if (!cursor.moveToNext()) break
                    budget.visited()
                    val name = cursor.getString(2)?.take(512)?.trim().orEmpty()
                    val key = cursor.getString(1)
                    if (name.isNotEmpty() && !key.isNullOrEmpty()) result.add(Raw(cursor.getLong(0), key.take(512), name))
                }
            } ?: error("The device's contacts provider is unavailable")
        } catch (error: android.os.OperationCanceledException) {
            if (!shouldContinue() || !budget.expired()) throw error
            partial = true // Deadline reached: useful rows are partial, never Ready(empty).
        } finally { deadline?.cancel(false) }
        if (!shouldContinue()) throw android.os.OperationCanceledException()
        val normalized = Search.normalizeAll(result.map { it.name }, shouldContinue)
        if (!shouldContinue()) throw android.os.OperationCanceledException()
        return ScanResult(result.mapIndexed { i, row -> Contact(row.id, row.key, row.name, normalized[i]) }, partial || ContactCoverage.isPartial(result.size, 50_000))
    }

    fun details(resolver: ContentResolver, resources: Resources, contact: Contact,
                cancellation: CancellationSignal = CancellationSignal()): Details {
        val deadline = deadlines.schedule({ cancellation.cancel() }, 2500L, java.util.concurrent.TimeUnit.MILLISECONDS)
        try {
        cancellation.throwIfCanceled()
        val currentId = resolver.query(contact.uri, arrayOf(ContactsContract.Contacts._ID),
            null, null, null, cancellation)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: error("This contact no longer exists")
        val numbers = ArrayList<Number>()
        val seenNumbers = HashSet<String>()
        val channels = linkedSetOf<Channel>()
        resolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
            "${ContactsContract.Data.CONTACT_ID}=?", arrayOf(currentId.toString()), null, cancellation)?.use { cursor ->
            var visited = 0
            while (visited++ < 1000 && cursor.moveToNext() && numbers.size + channels.size < 80) {
                cancellation.throwIfCanceled()
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
        cancellation.throwIfCanceled()
        return Details(numbers.toList(), channels.toList())
        } finally { deadline.cancel(false) }
    }
}

internal object ContactCoverage {
    fun isPartial(count: Int, limit: Int): Boolean =
        PortablePolicy.ruleBool("coverage", "count" to count, "limit" to limit) ?: (count >= limit)
}
