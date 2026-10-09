package com.test.agenttrade

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.test.agenttrade.data.AppPrefs
import com.test.agenttrade.ui.AppRoot
import com.test.agenttrade.ui.AppRouter
import com.test.agenttrade.ui.theme.OptimAITheme
import com.test.agenttrade.wallet.WalletConnectManager

/**
 * The app's single activity. `agenttrade://buy?…` links — from the keyboard's
 * trading card or the share flow — arrive here (also when already running,
 * via `onNewIntent`) and open the quote screen through `AppRouter`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) AppRouter.handle(intent?.data)
        // The WalletConnect SDK starts after the first frame, off the launch path.
        window.decorView.post { WalletConnectManager.ensureSdk() }
        setContent {
            val theme by AppPrefs.theme.collectAsState()
            OptimAITheme(theme) { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppRouter.handle(intent.data)
    }
}
