package com.cargenome.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps the real [CarGenomeApplication] for Hilt's test application, so
 * instrumented tests get an injectable component without dragging in
 * WorkManager and the rest of the production startup.
 */
class CarGenomeTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
