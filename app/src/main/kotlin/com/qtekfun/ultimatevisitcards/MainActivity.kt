package com.qtekfun.ultimatevisitcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qtekfun.ultimatevisitcards.navigation.UvcApp
import com.qtekfun.ultimatevisitcards.ui.theme.UvcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UvcTheme {
                UvcApp()
            }
        }
    }
}
