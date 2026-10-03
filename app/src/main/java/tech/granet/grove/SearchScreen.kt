package tech.granet.grove

import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

import tech.granet.grove.ui.dp
import tech.granet.grove.ui.iconRow
import tech.granet.grove.ui.label

/** Renders only relevant inline results. The activity handles Android intents and permissions. */
internal class SearchScreen(private val context: Context) {
    data class AppRow(val key: String, val label: String, val icon: Bitmap?, val open: () -> Unit, val menu: () -> Unit)
    data class FileRow(val file: IndexedFile, val open: () -> Unit, val menu: () -> Unit)
    data class ContactRow(val name: String, val open: () -> Unit)

    private fun row(title: String, iconId: Int, subtitle: String? = null, bitmap: Bitmap? = null,
                    iconKey: String? = null, action: () -> Unit, longPress: (() -> Unit)? = null): View =
        context.iconRow(title, iconId, subtitle, bitmap, iconTag = iconKey,
            onClick = action, onLongClick = longPress)

    private fun heading(title: String, iconId: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, context.dp(8), 0, context.dp(4))
        addView(ImageView(context).apply {
            setImageResource(iconId)
            imageTintList = android.content.res.ColorStateList.valueOf(ThemeColors.icon(context))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(8) })
        addView(context.label(title, 12f))
    }

    fun render(target: LinearLayout, query: String, apps: List<AppRow>, contacts: List<ContactRow>, files: List<FileRow>,
               contactState: SearchSourceState, requestContactAccess: () -> Unit,
               retryContacts: () -> Unit, fileState: SearchSourceState,
               requestFileAccess: () -> Unit, retryFiles: () -> Unit,
               searchGoogle: () -> Unit, googleMenu: () -> Unit,
               searchStore: () -> Unit, storeMenu: () -> Unit) {
        target.removeAllViews()
        if (Search.prepare(query).text.isEmpty()) return
        if (apps.isNotEmpty()) target.addView(heading("APPS", R.drawable.ic_grid))
        apps.forEach { app ->
            target.addView(row(app.label, R.drawable.ic_grid, bitmap = app.icon, iconKey = app.key,
                action = app.open, longPress = app.menu))
        }
        if (contactState !is SearchSourceState.Disabled &&
            (contacts.isNotEmpty() || contactState !is SearchSourceState.Ready))
            target.addView(heading("CONTACTS", R.drawable.ic_contact))
        if (contactState is SearchSourceState.Ready) contacts.forEach { contact ->
            target.addView(row(contact.name, R.drawable.ic_contact, action = contact.open, longPress = contact.open))
        }
        when (contactState) {
            SearchSourceState.PermissionRequired -> target.addView(row("Allow contact search", R.drawable.ic_contact,
                "Allow Grove to search your contacts", action = requestContactAccess))
            SearchSourceState.Loading -> target.addView(context.label("Loading contacts…", 14f))
            SearchSourceState.Failed -> target.addView(row("Retry contact search", R.drawable.ic_contact,
                "The contacts provider could not be read", action = retryContacts))
            else -> Unit
        }
        if (fileState !is SearchSourceState.Disabled &&
            (files.isNotEmpty() || fileState !is SearchSourceState.Ready))
            target.addView(heading("FILES", R.drawable.ic_folder))
        if (fileState is SearchSourceState.Ready || fileState is SearchSourceState.Partial) files.forEach { entry ->
            target.addView(row(entry.file.name, R.drawable.ic_document, entry.file.category,
                action = entry.open, longPress = entry.menu))
        }
        when (fileState) {
            SearchSourceState.PermissionRequired -> target.addView(row("Allow device file search", R.drawable.ic_folder,
                "Allow access to shared storage", action = requestFileAccess))
            SearchSourceState.Loading -> target.addView(context.label("Searching files…", 14f))
            SearchSourceState.Failed -> target.addView(row("Retry file search", R.drawable.ic_folder,
                "Shared storage could not be indexed", action = retryFiles))
            is SearchSourceState.Partial -> target.addView(context.label(
                "Some folders could not be searched (${fileState.skippedDirectories} skipped).", 14f))
            else -> Unit
        }
        target.addView(heading("GOOGLE", R.drawable.ic_public))
        target.addView(row("Search Google for “${query.trim()}”", R.drawable.ic_public,
            action = searchGoogle, longPress = googleMenu))
        target.addView(heading("PLAY STORE", R.drawable.ic_store))
        target.addView(row("Search Play Store for “${query.trim()}”", R.drawable.ic_store,
            action = searchStore, longPress = storeMenu))
    }
}
