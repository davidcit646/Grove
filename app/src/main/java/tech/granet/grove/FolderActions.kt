package tech.granet.grove

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.confirmDialog
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.listDialog
import tech.granet.grove.ui.message

/** Folder dialogs and mutations; commits are delegated to the Activity's checked boundary. */
internal class FolderActions(
    private val activity: AppCompatActivity,
    private val config: () -> Config,
    private val apps: () -> List<App>,
    private val tiles: DrawerTiles,
    private val commit: (Config) -> Boolean,
    private val clearSelection: () -> Unit,
    private val refresh: () -> Unit,
    private val appMenu: (App) -> Unit,
    private val showActions: (String, List<Triple<String, Int, () -> Unit>>) -> Unit,
) {
    fun options(name: String) {
        showActions(name, listOf(
            Triple("Open folder", R.drawable.ic_folder) { open(name) },
            Triple("Rename folder", R.drawable.ic_edit) { promptRename(name) },
            Triple("Delete folder", R.drawable.ic_delete) {
                activity.confirmDialog("Delete $name?", "Apps in this folder will return to All apps.", "Delete") {
                    DrawerState.deleteFolder(config(), name)?.let { if (commit(it)) refresh() }
                }
            },
        ))
    }

    fun open(name: String) {
        val folder = config().folders.firstOrNull { it.name == name } ?: return
        val members = folder.apps.mapNotNull { key -> apps().firstOrNull { it.key == key } }
        val grid = GridView(activity).apply {
            numColumns = 4; verticalSpacing = activity.dp(8)
            adapter = object : BaseAdapter() {
                override fun getCount() = members.size
                override fun getItem(position: Int) = members[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val tile = (convertView?.tag as? DrawerTiles.Tile) ?: tiles.create()
                    tiles.bind(tile, members[position])
                    tile.layout.setOnLongClickListener {
                        showActions(members[position].label, listOf(
                            Triple("Remove from folder", R.drawable.ic_delete) {
                                if (commit(DrawerState.removeFromFolders(config(), setOf(members[position].key)))) {
                                    refresh(); open(name)
                                }
                            },
                            Triple("App options", R.drawable.ic_settings) { appMenu(members[position]) },
                        )); true
                    }
                    return tile.layout
                }
            }
        }
        grid.layoutParams = ViewGroup.LayoutParams(-1, activity.dp(320))
        MaterialAlertDialogBuilder(activity).setTitle(name).setView(grid).setPositiveButton("Done", null).show()
    }

    fun move(keys: Set<String>, name: String) {
        val next = DrawerState.moveToFolder(config(), keys, name) ?: return
        if (commit(next)) { clearSelection(); refresh() }
    }

    fun promptCreate(keys: List<String> = emptyList()) {
        val input = EditText(activity).apply {
            hint = "Folder name"; isSingleLine = true
            setPadding(activity.dp(24), activity.dp(16), activity.dp(24), activity.dp(16))
        }
        val dialog = MaterialAlertDialogBuilder(activity).setTitle("Create folder").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Create", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val next = DrawerState.createFolder(config(), input.text.toString(), keys)
                if (next == null) {
                    input.error = "Choose a unique folder name up to 40 characters"
                    return@setOnClickListener
                }
                if (commit(next)) { clearSelection(); refresh(); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    fun promptRename(name: String) {
        val input = EditText(activity).apply {
            setText(name); isSingleLine = true
            setPadding(activity.dp(24), activity.dp(16), activity.dp(24), activity.dp(16))
        }
        MaterialAlertDialogBuilder(activity).setTitle("Rename folder").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val next = DrawerState.renameFolder(config(), name, input.text.toString())
                if (next == null) activity.message("Choose a unique folder name up to 40 characters")
                else if (commit(next)) refresh()
            }.show()
    }

    fun choose(keys: Set<String>) {
        val names = config().folders.map { it.name }
        activity.listDialog("Move to folder", names) { index -> move(keys, names[index]) }
    }
}
