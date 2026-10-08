package tech.granet.grove

internal object FolderPolicy {
        fun createFolder(config: Config, name: String, keys: Collection<String>): Config? {
            native(config, "create", name, keys = keys)?.let { return it.config }
            val label = name.trim()
            if (label.length !in 1..40 || config.folders.any { it.name.equals(label, true) }) return null
            val members = keys.distinct()
            return config.copy(folders = config.folders.map {
                it.copy(apps = it.apps.filterNot(members::contains))
            } + AppFolder(label, members))
        }

        fun renameFolder(config: Config, old: String, next: String): Config? {
            native(config, "rename", next, old = old)?.let { return it.config }
            val label = next.trim()
            if (label.length !in 1..40 || config.folders.none { it.name == old } ||
                config.folders.any { it.name.equals(label, true) && it.name != old }) return null
            return config.copy(folders = config.folders.map {
                if (it.name == old) it.copy(name = label) else it
            })
        }

        fun deleteFolder(config: Config, name: String): Config? {
            native(config, "delete", name)?.let { return it.config }
            if (config.folders.none { it.name == name }) return null
            return config.copy(folders = config.folders.filterNot { it.name == name })
        }

        fun moveToFolder(config: Config, keys: Set<String>, name: String): Config? {
            native(config, "move", name, keys = keys)?.let { return it.config }
            if (keys.isEmpty() || config.folders.none { it.name == name }) return null
            return config.copy(folders = config.folders.map { folder ->
                if (folder.name == name) folder.copy(apps = (folder.apps + keys).distinct())
                else folder.copy(apps = folder.apps.filterNot(keys::contains))
            })
        }

        fun removeFromFolders(config: Config, keys: Set<String>): Config =
            native(config, "remove", "", keys = keys)?.config ?: config.copy(folders = config.folders.map {
                it.copy(apps = it.apps.filterNot(keys::contains))
            })

        fun pin(config: Config, keys: Set<String>): Config? =
            if (keys.isEmpty()) null else config.copy(favorites = (config.favorites + keys).distinct())

        fun movePin(config: Config, key: String, target: String): Config? {
            val moved = PinnedApps.moveTo(config.favorites, key, target)
            return if (moved == config.favorites) null else config.copy(favorites = moved)
        }
    private data class NativeConfig(val config: Config?)
    private fun native(config: Config, action: String, name: String, old: String? = null, keys: Collection<String> = emptyList()): NativeConfig? {
        val response = CoreBridge.portable("folder", org.json.JSONObject().put("config", org.json.JSONObject(config.json()))
            .put("action", action).put("name", name).put("old", old ?: org.json.JSONObject.NULL).put("keys", org.json.JSONArray(keys))) ?: return null
        if (response.has("error")) return null
        return NativeConfig(if (response.isNull("value")) null else ConfigStore.parse(response.getJSONObject("value").toString()))
    }
}
