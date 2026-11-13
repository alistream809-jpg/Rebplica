package com.replica.app

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.replica.app.ui.AuroraBackground
import com.replica.app.ui.GlassToast
import com.replica.app.ui.MainShell
import com.replica.app.ui.ProgressScreen
import com.replica.app.ui.ResultScreen
import com.replica.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        AppState.init(this)
        setContent { App() }
    }
}

@Composable
fun App() {
    val screen = AppState.screen
    BackHandler(enabled = screen != Screen.MAIN) {
        when (screen) {
            Screen.PROGRESS -> AppState.cancel()
            else -> AppState.screen = Screen.MAIN
        }
    }
    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Crossfade(targetState = screen, animationSpec = tween(320), label = "screen") { s ->
            when (s) {
                Screen.MAIN -> MainShell()
                Screen.SETTINGS -> SettingsScreen()
                Screen.PROGRESS -> ProgressScreen()
                Screen.RESULT -> ResultScreen()
            }
        }
        val toast = AppState.toast
        if (toast != null) {
            GlassToast(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
        }
    }
}
