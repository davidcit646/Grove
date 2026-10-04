package tech.granet.grove

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import tech.granet.grove.ui.message

/** External search intents and their recoverable app/clipboard outcomes. */
internal class SearchActions(
    private val activity: AppCompatActivity,
    private val showMenu: (String, List<Triple<String, Int, () -> Unit>>) -> Unit,
) {
    fun openWeb(url: String) {
        runCatching {
            val uri = Uri.parse(url)
            require(uri.scheme == "https") { "Only HTTPS links are supported" }
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure { activity.message("No app can open this search") }
    }

    fun openPlayStore(query: String, install: Boolean = false) {
        val encoded = Uri.encode(query)
        val marketUrl = if (install) "market://search?q=$encoded&c=apps" else "market://search?q=$encoded"
        val webUrl = if (install) "https://play.google.com/store/search?q=$encoded&c=apps"
            else "https://play.google.com/store/search?q=$encoded"
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(marketUrl))) }
            .onFailure { openWeb(webUrl) }
    }

    fun webResultMenu(query: String, provider: String) {
        showMenu("$provider search", listOf(
            Triple("Search with Google", R.drawable.ic_public) { openWeb("https://www.google.com/search?q=${Uri.encode(query)}") },
            Triple("Ask an AI (ChatGPT)", R.drawable.ic_ai) { openWeb("https://chatgpt.com/?q=${Uri.encode(query)}") },
            Triple("Ask an AI (Gemini)", R.drawable.ic_ai) { openWeb("https://gemini.google.com/app?q=${Uri.encode(query)}") },
            Triple("Copy search text", R.drawable.ic_copy) {
                runCatching {
                    activity.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Search", query))
                }.onSuccess { activity.message("Search copied") }
                    .onFailure { activity.message("Could not copy search") }
            },
            Triple("Share search", R.drawable.ic_share) {
                runCatching {
                    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(Intent.EXTRA_TEXT, query), "Share search"))
                }.onFailure { activity.message("No app can share this search") }
            },
        ))
    }

    fun playStoreMenu(query: String) {
        showMenu("Play Store search", listOf(
            Triple("Install an app", R.drawable.ic_download) { openPlayStore(query, install = true) },
            Triple("View in Play Store", R.drawable.ic_store) { openPlayStore(query) },
            Triple("Leave a Play Store review", R.drawable.ic_star) {
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://writeReview?package=${activity.packageName}"))) }
                    .onFailure { openWeb("https://play.google.com/store/apps/details?id=${activity.packageName}") }
            },
            Triple("Copy search text", R.drawable.ic_copy) {
                runCatching {
                    activity.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Search", query))
                }.onSuccess { activity.message("Search copied") }
                    .onFailure { activity.message("Could not copy search") }
            },
        ))
    }
}
