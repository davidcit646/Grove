package tech.granet.grove

/** Raw rows count even when unusable; elapsed deadlines include provider query time. */
internal class ContactScanBudget(private val maxRows: Int, private val maxDurationMs: Long,
                                 private val now: () -> Long) {
    private val started = now()
    var rows = 0; private set
    fun expired() = now() - started >= maxDurationMs
    fun exhausted() = rows >= maxRows || expired()
    fun visited() { rows++ }
}
