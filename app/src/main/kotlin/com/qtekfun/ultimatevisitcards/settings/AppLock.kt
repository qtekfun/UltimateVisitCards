package com.qtekfun.ultimatevisitcards.settings

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional app lock (docs/03 «Seguridad»): when enabled, the app locks when it comes back after more than
 * [GRACE_MILLIS] in the background (a quick trip to the file picker or the share sheet does not count) and
 * unlocks with the device's biometrics or its screen-lock PIN/pattern/password. It protects the screen, not
 * the data at rest (the backup has its own encryption).
 *
 * `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`: the only combination with the device credential that works from
 * Android 10 (minSdk 29) on; a lock screen does not need a cryptographic biometric.
 */
@Singleton
class AppLock @Inject constructor(@ApplicationContext private val context: Context, private val preferences: UiPreferences) {
    private val _locked = MutableStateFlow(preferences.lock.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** True when the device has a screen lock or enrolled biometrics, so the lock can be turned on. */
    fun available(): Boolean = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private var backgroundSince: Long? = null

    /** The app went to the background at [now] (elapsed realtime). */
    fun onBackground(now: Long) {
        backgroundSince = now
    }

    /** The app is visible again at [now]: lock it if the option is on and it was away long enough. */
    fun onForeground(now: Long) {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (preferences.lock.value && now - since >= GRACE_MILLIS) _locked.value = true
    }

    /** Turning the option off unlocks at once; turning it on does not lock the current session. */
    fun setEnabled(enabled: Boolean) {
        preferences.setLock(enabled)
        if (!enabled) _locked.value = false
    }

    fun unlock(activity: FragmentActivity, title: String, subtitle: String) {
        // The screen lock was removed from the phone after enabling the option: nothing can authenticate, so
        // the option is switched off instead of locking the user out of their own cards.
        if (!available()) {
            setEnabled(false)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _locked.value = false
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }

    companion object {
        const val GRACE_MILLIS = 30_000L
        private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
