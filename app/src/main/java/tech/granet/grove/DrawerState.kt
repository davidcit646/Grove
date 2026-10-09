package tech.granet.grove

/** Ephemeral selection is discarded on navigation; edits return a complete candidate Config. */
internal class DrawerState {
    private val selected = linkedSetOf<String>()
    var selecting = false
        private set
    val keys: Set<String> get() = selected.toSet()
    fun isSelected(key: String): Boolean = key in selected

    fun clear() { selecting = false; selected.clear() }
    fun clearKeys() { selected.clear() }
    fun toggleMode() {
        selecting = !selecting
        if (!selecting) selected.clear()
    }
    fun toggle(key: String) {
        if (!selected.add(key)) selected.remove(key)
    }
    fun select(key: String) { selected.add(key) }

    companion object {
        fun createFolder(config: Config, name: String, keys: Collection<String>): Config? = FolderPolicy.createFolder(config, name, keys)
        fun renameFolder(config: Config, old: String, next: String): Config? = FolderPolicy.renameFolder(config, old, next)
        fun deleteFolder(config: Config, name: String): Config? = FolderPolicy.deleteFolder(config, name)
        fun moveToFolder(config: Config, keys: Set<String>, name: String): Config? = FolderPolicy.moveToFolder(config, keys, name)
        fun removeFromFolders(config: Config, keys: Set<String>): Config = FolderPolicy.removeFromFolders(config, keys)
        fun pin(config: Config, keys: Set<String>): Config? = FolderPolicy.pin(config, keys)
        fun movePin(config: Config, key: String, target: String): Config? = FolderPolicy.movePin(config, key, target)
    }
}
