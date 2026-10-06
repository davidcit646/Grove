package tech.granet.grove

import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import tech.granet.grove.ui.bodyText
import tech.granet.grove.ui.dp

/** Provisional editors and review surfaces; Android dialogs are not used as workflow containers. */
internal class SettingsDocumentPages(
    private val activity: SettingsActivity,
    private val session: SettingsSession,
    private val navigate: (String) -> Unit,
    private val feedback: (CommandFeedback) -> Boolean,
) {
    fun render(route: String, content: LinearLayout, pickImport: () -> Unit, pickExport: () -> Unit) {
        val snapshot = session.repository.snapshot()
        when (route) {
            "configuration" -> {
                content.addView(activity.bodyText("Import, export or edit your launcher choices. Widget IDs and Android grants remain on this device."))
                button(content, "Import configuration", pickImport)
                button(content, "Export configuration", pickExport)
                button(content, "Advanced editor") {
                    if (session.draft == null) { session.draft = snapshot.config.json(); session.editorBase = snapshot }
                    navigate("editor")
                }
                if ((activity.application as GroveApp).settingsStore.brokenCustomConfig != null)
                    button(content, "Recover preserved configuration") { navigate("recovery") }
                button(content, "Restore defaults") { navigate("defaults") }
            }
            "recovery" -> {
                content.addView(activity.bodyText("Your damaged custom configuration is preserved. Grove is using a safe fallback until you replace it."))
                button(content, "Edit preserved configuration") {
                    session.draft = (activity.application as GroveApp).settingsStore.brokenCustomConfig ?: snapshot.config.json()
                    session.editorBase = snapshot; navigate("editor")
                }
                button(content, "Import a replacement", pickImport)
                button(content, "Restore defaults") { navigate("defaults") }
            }
            "defaults" -> {
                content.addView(activity.bodyText("Replace your configuration with safe defaults. This clears pins and folders; it does not delete widgets, reports or Android grants."))
                button(content, "Review default configuration") {
                    session.candidate = ConfigCandidate(Config(), session.repository.snapshot()); navigate("review")
                }
            }
            "editor" -> {
                if (session.draft == null) { session.draft = snapshot.config.json(); session.editorBase = snapshot }
                content.addView(activity.bodyText("Changes remain a draft until you review and apply them."))
                val editor = EditText(activity).apply {
                    filters = arrayOf(InputFilter.LengthFilter(65_536))
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    typeface = Typeface.MONOSPACE; textSize = 13f; minLines = 12
                    contentDescription = "Configuration JSON draft"
                    setText(session.draft)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { session.draft = s.toString() }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                }
                content.addView(editor, LinearLayout.LayoutParams(-1, -2))
                if (session.editorBase == null) session.editorBase = snapshot
                button(content, if (session.busy) "Validating…" else "Validate and review") { if (!session.busy) session.validateDraft() }
                button(content, "Discard draft and load current settings") {
                    session.draft = session.repository.snapshot().config.json()
                    session.editorBase = session.repository.snapshot()
                    navigate("configuration")
                }
                if (session.editorBase != snapshot) button(content, "Review against current settings") {
                    session.editorBase = session.repository.snapshot(); session.validateDraft()
                }
            }
            "review" -> {
                val pending = session.candidate
                if (pending == null) { content.addView(activity.bodyText("No configuration is ready to apply. Import or edit one first.")); return }
                content.addView(activity.bodyText("This replaces launcher settings, pins and folders. It does not grant Android access or apply the remembered wallpaper image."))
                if (pending.base != snapshot) content.addView(activity.bodyText("Settings changed after this draft began. Import again or review your draft against current settings."))
                val json = android.widget.TextView(activity).apply {
                    text = pending.config.json(); typeface = Typeface.MONOSPACE; textSize = 13f; setTextIsSelectable(true)
                }
                content.addView(json)
                button(content, "Apply configuration") {
                    if (feedback(session.apply())) navigate("configuration")
                }.isEnabled = pending.base == snapshot
            }
            "email" -> {
                val editor = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    setSingleLine(); setText(session.emailDraft ?: CrashReporter.developerEmail(activity)); contentDescription = "Developer email"
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { session.emailDraft = s.toString() }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                }
                content.addView(editor)
                button(content, "Save email") { feedback(session.commands.email(editor.text.toString())) }
            }
            "deleteReports" -> {
                content.addView(activity.bodyText("Delete all ${CrashReporter.pendingCount(activity)} saved local reports?"))
                button(content, "Delete reports") { if (feedback(session.commands.deleteReports())) navigate("help") }
            }
        }
    }
    private fun button(content: LinearLayout, title: String, action: () -> Unit): MaterialButton = MaterialButton(activity).apply {
        text = title; minimumHeight = activity.dp(48); setOnClickListener { action() }; content.addView(this)
    }
}
