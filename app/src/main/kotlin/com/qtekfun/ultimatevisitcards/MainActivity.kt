package com.qtekfun.ultimatevisitcards

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.qtekfun.ultimatevisitcards.navigation.UvcApp
import com.qtekfun.ultimatevisitcards.settings.AppLock
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.ui.LockScreen
import com.qtekfun.ultimatevisitcards.ui.theme.UvcTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A [FragmentActivity] because the biometric prompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var lock: AppLock

    @Inject
    lateinit var preferences: UiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // «Hide content in recent apps» (FLAG_SECURE), applied as soon as it changes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                preferences.secureScreen.collect { secure ->
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            UvcTheme {
                val locked by lock.locked.collectAsStateWithLifecycle()
                // The lock covers the app instead of replacing it: navigation and pending results survive.
                Box(Modifier.fillMaxSize()) {
                    Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) { UvcApp() }
                    if (locked) LockScreen(onUnlock = { title, subtitle -> lock.unlock(this@MainActivity, title, subtitle) })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground(SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        super.onStop()
        // A rotation is not leaving the app.
        if (!isChangingConfigurations) lock.onBackground(SystemClock.elapsedRealtime())
    }
}
