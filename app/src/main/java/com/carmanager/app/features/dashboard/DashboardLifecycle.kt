package com.carmanager.app.features.dashboard

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect

@Composable
internal fun DashboardLifecycle(viewModel: DashboardViewModel) {
    LifecycleStartEffect(viewModel) {
        viewModel.refreshTime()
        onStopOrDispose { }
    }
}
