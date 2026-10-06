package tech.granet.grove

internal enum class IndexValidity { UNKNOWN, ABSENT, AVAILABLE, CORRUPT }
internal data class IndexMetadata(
    val validity: IndexValidity = IndexValidity.UNKNOWN,
    val writtenAt: Long = 0,
    val partial: Boolean = false,
    val invalidated: Boolean = false,
) {
    fun fresh(kind: String, now: Long = System.currentTimeMillis()): Boolean =
        validity == IndexValidity.AVAILABLE && !invalidated &&
            now - writtenAt in 0..(if (kind == "files") 24L * 60 * 60_000 else 15L * 60_000)
}
