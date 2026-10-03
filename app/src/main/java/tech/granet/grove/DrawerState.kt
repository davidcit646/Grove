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
        fun createFolder(config: Config, name: String, keys: Collection<String>): Config? {
            val label = name.trim()
            if (label.length !in 1..40 || config.folders.any { it.name.equals(label, true) }) return null
            val members = keys.distinct()
            return config.copy(folders = config.folders.map {
                it.copy(apps = it.apps.filterNot(members::contains))
            } + AppFolder(label, members))
        }

        fun renameFolder(config: Config, old: String, next: String): Config? {
            val label = next.trim()
            if (label.length !in 1..40 || config.folders.none { it.name == old } ||
                config.folders.any { it.name.equals(label, true) && it.name != old }) return null
            return config.copy(folders = config.folders.map {
                if (it.name == old) it.copy(name = label) else it
            })
        }

        fun deleteFolder(config: Config, name: String): Config? {
            if (config.folders.none { it.name == name }) return null
            return config.copy(folders = config.folders.filterNot { it.name == name })
        }

        fun moveToFolder(config: Config, keys: Set<String>, name: String): Config? {
            if (keys.isEmpty() || config.folders.none { it.name == name }) return null
            return config.copy(folders = config.folders.map { folder ->
                if (folder.name == name) folder.copy(apps = (folder.apps + keys).distinct())
                else folder.copy(apps = folder.apps.filterNot(keys::contains))
            })
        }

        fun removeFromFolders(config: Config, keys: Set<String>): Config =
            config.copy(folders = config.folders.map {
                it.copy(apps = it.apps.filterNot(keys::contains))
            })

        fun pin(config: Config, keys: Set<String>): Config? =
            if (keys.isEmpty()) null else config.copy(favorites = (config.favorites + keys).distinct())

        fun movePin(config: Config, key: String, target: String): Config? {
            val moved = PinnedApps.moveTo(config.favorites, key, target)
            return if (moved == config.favorites) null else config.copy(favorites = moved)
        }
    }
}
