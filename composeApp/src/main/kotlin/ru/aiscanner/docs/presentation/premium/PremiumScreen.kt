package ru.aiscanner.docs.presentation.premium

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.aiscanner.docs.R

/**
 * Экран Premium. Покупки подключаются на Этапе 8 (RuStore Billing);
 * сейчас показываются преимущества и заглушка (п. 11 ТЗ).
 * Paywall не показывается при первом запуске приложения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(navController: NavHostController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.premium_unavailable)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.premium_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                stringResource(R.string.premium_subtitle),
                style = MaterialTheme.typography.titleMedium,
            )
            Text("• " + stringResource(R.string.premium_benefit_pdf), Modifier.padding(top = 16.dp))
            Text("• " + stringResource(R.string.premium_benefit_ocr), Modifier.padding(top = 8.dp))
            Text("• " + stringResource(R.string.premium_benefit_ai), Modifier.padding(top = 8.dp))

            Button(
                onClick = { scope.launch { snackbarHostState.showSnackbar(unavailable) } },
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) {
                Text(stringResource(R.string.premium_monthly))
            }
            Button(
                onClick = { scope.launch { snackbarHostState.showSnackbar(unavailable) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.premium_yearly))
            }
        }
    }
}
