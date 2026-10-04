package tech.granet.grove

/** One Android confirmation at a time; cancellation stops the remaining batch. */
internal class UninstallBatch {
    private val pending = ArrayDeque<String>()
    private var current: String? = null

    fun start(packages: List<String>): String? {
        cancel()
        pending.addAll(packages.filter { it.isNotBlank() }.distinct())
        return advance()
    }

    fun accepted(): String? {
        if (current == null) return null
        current = null
        return advance()
    }

    fun cancel(): Int {
        val remaining = pending.size
        pending.clear()
        current = null
        return remaining
    }

    private fun advance(): String? = (if (pending.isEmpty()) null else pending.removeFirst())
        .also { current = it }
}
