package tech.granet.grove

import android.content.Context

internal class TutorialCommands(private val context: Context) {
    fun searchReplayPending(): Boolean = context.getSharedPreferences("grove", Context.MODE_PRIVATE).getBoolean("search_tutorial_pending", false)
    fun replaySearch(enabled: Boolean): CommandFeedback = checked {
        context.getSharedPreferences("grove", Context.MODE_PRIVATE).edit().apply {
            if (enabled) {
                putBoolean("search_tutorial_pending", true)
                putLong("search_tutorial_request", System.nanoTime())
            } else remove("search_tutorial_pending")
        }.commit()
    }
    fun replayPending(): Boolean = context.getSharedPreferences("grove", Context.MODE_PRIVATE).getBoolean("setup_pending", false)
    fun replay(enabled: Boolean): CommandFeedback = checked {
        context.getSharedPreferences("grove", Context.MODE_PRIVATE).edit().apply {
            if (enabled) putBoolean("setup_pending", true) else remove("setup_pending")
        }.commit()
    }
}
