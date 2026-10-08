package tech.granet.grove

import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.provider.ContactsContract
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.ExecutorService
import tech.granet.grove.ui.message

/** Contact-specific actions; the Activity supplies current permission and package state. */
internal class ContactActions(
    private val activity: AppCompatActivity,
    private val current: () -> Config,
    private val hasAccess: () -> Boolean,
    private val installed: (String) -> Boolean,
    private val showActionMenu: (String, List<Triple<String, Int, () -> Unit>>) -> Unit,
) {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun shutdown() { worker.shutdownNow() }
    private fun menu(title: String, actions: List<Triple<String, Int, () -> Unit>>) {
        if (!current().search.contacts || !hasAccess()) return
        showActionMenu(title, actions.map { (label, icon, action) -> Triple(label, icon) {
            if (current().search.contacts && hasAccess()) action()
            else activity.message("Contact access is unavailable")
        } })
    }

    private fun launch(intent: Intent) {
        if (!current().search.contacts || !hasAccess()) {
            activity.message("Contact access is unavailable")
            return
        }
        activity.startActivity(intent)
    }

    fun show(contact: ContactIndex.Contact) {
        if (!current().search.contacts || !hasAccess()) return
        worker.execute {
            val details = runCatching { ContactIndex.details(activity.contentResolver, activity.resources, contact) }
                .onFailure { Log.w("Grove", "Cannot read contact details", it) }.getOrNull()
            activity.runOnUiThread {
                if (activity.isDestroyed || !current().search.contacts || !hasAccess()) return@runOnUiThread
                if (details == null) { activity.message("Contact details unavailable; try again"); return@runOnUiThread }
                fun launchCurrent(intent: Intent) {
                    worker.execute {
                        val fresh = runCatching { ContactIndex.details(activity.contentResolver, activity.resources, contact) }.getOrNull()
                        activity.runOnUiThread {
                            if (activity.isDestroyed) return@runOnUiThread
                            if (fresh == null || fresh != details) activity.message("Contact changed. Open its menu again.")
                            else runCatching { launch(intent) }.onFailure { activity.message("No compatible app is available") }
                        }
                    }
                }
                val actions = mutableListOf<Triple<String, Int, () -> Unit>>()
                fun action(label: String, icon: Int, intent: () -> Intent) {
                    actions.add(Triple(label, icon) {
                        runCatching { launchCurrent(intent()) }.onFailure { activity.message("No compatible app is available") }
                    })
                }
                val waTargets = ContactIndex.whatsAppTargets(details.channels) { pkg ->
                    installed(pkg)
                }
                val numbers = details.numbers
                fun callRow(number: ContactIndex.Number) = Triple("${number.label} · ${number.value}", R.drawable.ic_call) {
                    runCatching {
                        launchCurrent(Intent.createChooser(
                            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.value, null)), "Call with"))
                    }.onFailure { activity.message("No compatible app is available") }
                    Unit
                }
                fun textRow(number: ContactIndex.Number) = Triple("${number.label} · ${number.value}", R.drawable.ic_message) {
                    runCatching {
                        launchCurrent(Intent.createChooser(
                            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number.value, null)), "Message with"))
                    }.onFailure { activity.message("No compatible app is available") }
                    Unit
                }
                // One Call row and one Text row per contact. Tapping always asks which app
                // (Phone, Google Voice, Linphone, …) should place it. A number picker only
                // appears when the contact genuinely has several different numbers.
                when {
                    numbers.size == 1 -> {
                        actions.add(callRow(numbers[0]))
                        actions.add(textRow(numbers[0]))
                    }
                    numbers.size > 1 -> {
                        actions.add(Triple("Call", R.drawable.ic_call) {
                            menu("Call ${contact.name}", numbers.map(::callRow))
                            Unit
                        })
                        actions.add(Triple("Text", R.drawable.ic_message) {
                            menu("Text ${contact.name}", numbers.map(::textRow))
                            Unit
                        })
                    }
                }
                numbers.forEach { number ->
                    val international = PhoneNumberUtils.formatNumberToE164(number.value, Locale.getDefault().country)
                    if (international != null && international.startsWith('+') && international.length in 9..16) {
                        val digits = international.drop(1)
                        waTargets.forEach { target ->
                            action("Message ${number.label} via ${target.label}", R.drawable.ic_message) {
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).setPackage(target.packageName)
                            }
                        }
                    }
                }
                ContactIndex.collapseChannels(details.channels).forEach { channel ->
                    val packageName = when (channel.label) {
                        "WhatsApp" -> if (channel.mime.contains("w4b")) "com.whatsapp.w4b" else "com.whatsapp"
                        "Messenger" -> "com.facebook.orca"
                        else -> null
                    }
                    if (packageName != null && installed(packageName)) {
                        action("Open in ${channel.label}", R.drawable.ic_message) {
                            Intent(Intent.ACTION_VIEW, channel.uri).setPackage(packageName)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                }
                action("View contact card", R.drawable.ic_contact) { Intent(Intent.ACTION_VIEW, contact.uri) }
                action("Edit contact", R.drawable.ic_edit) {
                    Intent(Intent.ACTION_EDIT).setDataAndType(contact.uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                }
                menu(contact.name, actions)
            }
        }
    }

}
