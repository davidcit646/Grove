package tech.granet.grove

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

internal data class ConfigCandidate(val config: Config, val base: SettingsSnapshot)

/** Retained document jobs/drafts own no Activity. Only an explicit Apply may publish a candidate. */
class SettingsSession(app: Application) : AndroidViewModel(app) {
    internal val repository = (app as GroveApp).settingsRepository
    internal val commands = SettingsCommands(app, repository)
    internal val tutorials = TutorialCommands(app)
    internal val diagnostics = DiagnosticsCommands(app)
    internal val indexes = IndexStatusController(app, repository)
    internal var candidate: ConfigCandidate? = null
    internal var draft: String? = null
    internal var editorBase: SettingsSnapshot? = null
    internal val documentResult = MutableLiveData<CommandFeedback>()
    internal var emailDraft: String? = null
    internal var gridColumns: Int? = null
    internal var gridRows: Int? = null
    internal var busy = false
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    internal fun readDocument(uri: Uri) {
        val base = repository.snapshot()
        runDocument("accept") {
            val config = getApplication<Application>().contentResolver.openInputStream(uri)?.use(ConfigDocuments::read)
                ?: error("Cannot open document")
            return@runDocument { candidate = ConfigCandidate(config, base); documentResult.value = CommandFeedback(true, "Review the configuration before applying.") }
        }
    }
    internal fun export(uri: Uri) {
        val config = repository.snapshot().config
        runDocument("export") {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { ConfigDocuments.write(config, it) }
                ?: error("Cannot write document")
            return@runDocument { documentResult.value = CommandFeedback(true, "Configuration exported") }
        }
    }
    internal fun validateDraft() {
        val text = draft.orEmpty()
        val base = editorBase ?: repository.snapshot()
        runDocument {
            val config = ConfigStore.parse(text)
            return@runDocument { candidate = ConfigCandidate(config, base); documentResult.value = CommandFeedback(true, "Review the configuration before applying.") }
        }
    }
    private fun runDocument(operation: String = "accept", work: () -> (() -> Unit)) {
        val token = ++generation; busy = true
        worker.execute {
            if (token != generation || Thread.currentThread().isInterrupted) return@execute
            val result = try { Result.success(work()) } catch (error: Exception) { Result.failure(error) }
            handler.post {
                if (token != generation) return@post
                busy = false
                result.onSuccess { it() }.onFailure { documentResult.value = CommandFeedback(false,
                    if (operation == "export") "Could not export the configuration. Choose a writable document and try again."
                    else "Could not accept the document. Check its JSON, supported version and 64 KB limit.") }
            }
        }
    }
    internal fun cancelDocument() { generation++; busy = false; candidate = null; documentResult.value = null }
    internal fun apply(): CommandFeedback {
        val pending = candidate ?: return CommandFeedback(false, "Choose or edit a configuration first.")
        val now = repository.snapshot()
        if (!ConfigDocumentGate.canActivate(pending.base, now)) return CommandFeedback(false, "Settings changed. Reopen the editor or import again to review the latest configuration.")
        val result = commands.replace(pending.config, pending.base.revision)
        if (result.saved) { candidate = null; draft = null; editorBase = null }
        return result
    }
    override fun onCleared() { generation++; worker.shutdownNow(); handler.removeCallbacksAndMessages(null) }
}
