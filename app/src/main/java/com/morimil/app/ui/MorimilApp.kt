package com.morimil.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MorimilApp(
    onboardingViewModel: GenesisUltraOnboardingViewModel = viewModel()
) {
    MorimilTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
            when (onboardingState.route) {
                GenesisUltraAppRoute.ONBOARDING -> {
                    OnboardingScreen(onboardingViewModel)
                }

                GenesisUltraAppRoute.RUNTIME -> {
                    // The legacy runtime ViewModel is not constructed before an
                    // authorized Genesis Ultra birth is durably verified.
                    val runtimeViewModel: MorimilViewModel = viewModel()
                    MainTabsScaffold(runtimeViewModel)
                }
            }
        }
    }
}
