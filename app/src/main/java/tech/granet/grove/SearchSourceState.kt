package tech.granet.grove

/** One source's explicit state; an empty successful result is Ready(0). */
internal sealed interface SearchSourceState {
    data object Disabled : SearchSourceState
    data object PermissionRequired : SearchSourceState
    data object Loading : SearchSourceState
    data class Ready(val count: Int) : SearchSourceState
    data class Partial(val count: Int, val skippedDirectories: Int) : SearchSourceState
    data object Failed : SearchSourceState

    companion object {
        fun resolve(enabled: Boolean, access: Boolean, loading: Boolean,
                    failed: Boolean, count: Int, skippedDirectories: Int = 0): SearchSourceState = when {
            !enabled -> Disabled
            !access -> PermissionRequired
            failed -> Failed
            loading -> Loading
            skippedDirectories > 0 -> Partial(count, skippedDirectories)
            else -> Ready(count)
        }
    }
}
