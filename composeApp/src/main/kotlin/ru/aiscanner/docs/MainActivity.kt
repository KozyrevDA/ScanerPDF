package ru.aiscanner.docs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.AnalyticsEvent
import ru.aiscanner.docs.presentation.navigation.AppNavGraph
import ru.aiscanner.docs.presentation.settings.SettingsViewModel
import ru.aiscanner.docs.presentation.theme.ScannerTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val analytics: Analytics by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) analytics.logEvent(AnalyticsEvent.APP_OPENED)
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            ScannerTheme(themeMode = settings.themeMode) {
                val navController = rememberNavController()
                AppNavGraph(navController)
            }
        }
    }
}
