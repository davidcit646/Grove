package tech.granet.grove

internal fun checked(action: () -> Boolean): CommandFeedback = try {
        if (action()) CommandFeedback(true) else CommandFeedback(false, "Could not save this change. Try again.")
    } catch (_: Exception) { CommandFeedback(false, "This operation is unavailable. Try again.") }
