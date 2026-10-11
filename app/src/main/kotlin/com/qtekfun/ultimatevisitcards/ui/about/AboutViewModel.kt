package com.qtekfun.ultimatevisitcards.ui.about

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import com.qtekfun.ultimatevisitcards.settings.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** A library shipped in the APK; [flavour] is `play` or `foss` when only one flavour includes it. */
data class ThirdPartyLicense(val name: String, val license: String, val url: String, val flavour: String? = null)

data class AboutUiState(
    val version: String = "",
    @StringRes val engine: Int? = null,
    val licenses: List<ThirdPartyLicense> = emptyList(),
    val licensesFailed: Boolean = false
)

/** «Acerca de» (docs/03 Ajustes): version, OCR engine, privacy and third-party licences. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appInfo: AppInfo,
    private val ocr: OcrService,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {
    private val _state = MutableStateFlow(AboutUiState(engine = engineLabel(ocr.engineId)))
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val version = withContext(io) { runCatching { appInfo.versionName }.getOrDefault("") }
            _state.update { it.copy(version = version) }
            val flavour = if (ocr.engineId == OcrService.MLKIT) "play" else "foss"
            try {
                val licenses = withContext(io) { readLicenses().filter { it.flavour == null || it.flavour == flavour } }
                _state.update { it.copy(licenses = licenses) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(licensesFailed = true) }
            }
        }
    }

    private fun readLicenses(): List<ThirdPartyLicense> {
        val text = context.resources.openRawResource(R.raw.third_party_licenses).bufferedReader().use { it.readText() }
        return parseLicenses(text)
    }

    companion object {
        @StringRes
        fun engineLabel(engineId: String): Int? = when (engineId) {
            OcrService.MLKIT -> R.string.about_engine_mlkit
            OcrService.TESSERACT -> R.string.about_engine_tesseract
            else -> null
        }

        /** Parses `third_party_licenses.json`: an array of {name, license, url, flavour?}. */
        fun parseLicenses(json: String): List<ThirdPartyLicense> {
            val array = JSONArray(json)
            return (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                ThirdPartyLicense(
                    name = item.getString("name"),
                    license = item.getString("license"),
                    url = item.getString("url"),
                    flavour = item.optString("flavour").takeIf { it.isNotEmpty() }
                )
            }.sortedBy { it.name.lowercase() }
        }
    }
}
