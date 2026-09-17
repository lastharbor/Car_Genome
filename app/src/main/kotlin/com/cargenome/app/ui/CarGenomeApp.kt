package com.cargenome.app.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.cargenome.app.data.settings.AppSettingsRepository
import com.cargenome.app.ui.navigation.CarGenomeNavHost

@Composable
fun CarGenomeApp(
    settingsRepository: AppSettingsRepository? = null,
) {
    Surface {
        CarGenomeNavHost(settingsRepo = settingsRepository)
    }
}
