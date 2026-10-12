package com.qtekfun.ultimatevisitcards.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.backup.BackupExporter
import com.qtekfun.ultimatevisitcards.data.backup.BackupManifest
import com.qtekfun.ultimatevisitcards.data.backup.BackupOptions
import com.qtekfun.ultimatevisitcards.data.backup.BackupResult
import com.qtekfun.ultimatevisitcards.data.backup.BackupSettings
import com.qtekfun.ultimatevisitcards.settings.AppInfo
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.ui.common.Documents
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The backup export, step by step (docs/05 §2). */
sealed interface ExportStep {
    data object Idle : ExportStep

    /** Options dialog: originals, encryption. */
    data object Options : ExportStep

    data class Running(val progress: Float) : ExportStep

    data class Done(val manifest: BackupManifest) : ExportStep

    data class Failed(val reason: ExportFailure) : ExportStep
}

enum class ExportFailure { WRITE, VERIFICATION, CANCELLED }

/** The options chosen before picking where to save. The password is never stored anywhere. */
data class ExportChoice(val includeOriginals: Boolean = true, val password: CharArray? = null) {
    override fun equals(other: Any?): Boolean = other is ExportChoice && includeOriginals == other.includeOriginals && password.contentEquals(other.password)

    override fun hashCode(): Int = 31 * includeOriginals.hashCode() + password.contentHashCode()
}

/** Backup export from Settings: options → file picker (SAF) → progress with cancel → verified result. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exporter: BackupExporter,
    private val documents: Documents,
    private val preferences: UiPreferences,
    private val appInfo: AppInfo
) : ViewModel() {
    private val _export = MutableStateFlow<ExportStep>(ExportStep.Idle)
    val export: StateFlow<ExportStep> = _export.asStateFlow()

    private var choice = ExportChoice()
    private var running: Job? = null

    fun startExport() = _export.update { ExportStep.Options }

    /** Options confirmed; the screen opens the file picker next. */
    fun chooseOptions(options: ExportChoice) {
        choice = options
    }

    fun exportTo(uri: Uri) {
        val options = choice
        choice = ExportChoice()
        _export.value = ExportStep.Running(0f)
        running = viewModelScope.launch {
            val result = try {
                documents.openOutput(uri).use { out ->
                    exporter.export(
                        out = out,
                        reopen = { documents.openInput(uri) },
                        options = BackupOptions(
                            appVersion = appInfo.versionName,
                            includeOriginals = options.includeOriginals,
                            password = options.password,
                            settings = BackupSettings(cardsView = preferences.cardsView.value.name)
                        ),
                        onProgress = { p -> _export.value = ExportStep.Running(p) }
                    )
                }
            } catch (e: IOException) {
                BackupResult.Failed(e)
            } finally {
                options.password?.fill(' ')
            }
            _export.value = when (result) {
                is BackupResult.Done -> ExportStep.Done(result.manifest)
                BackupResult.VerificationFailed -> ExportStep.Failed(ExportFailure.VERIFICATION).also { documents.delete(uri) }
                is BackupResult.Failed -> ExportStep.Failed(ExportFailure.WRITE).also { documents.delete(uri) }
            }
        }
        running?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) {
                documents.delete(uri)
                _export.value = ExportStep.Failed(ExportFailure.CANCELLED)
            }
        }
    }

    fun cancelExport() {
        running?.cancel()
    }

    fun closeExport() {
        _export.value = ExportStep.Idle
    }
}
