package tech.granet.grove

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Public Android entry point. External Intent extras never become internal document commands. */
class SettingsEntryActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}
