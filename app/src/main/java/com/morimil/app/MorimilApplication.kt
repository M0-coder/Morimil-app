package com.morimil.app

import android.app.Application
import android.util.Log
import com.morimil.app.improvements.SelfImprovementRuntimeObserver
import com.morimil.app.net.NativeBrowserRuntime

class MorimilApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NativeBrowserRuntime.install(this)
        runCatching {
            SelfImprovementRuntimeObserver.initialize(this)
        }.onFailure { failure ->
            // Self-improvement evidence must fail closed without taking Morimil's
            // primary runtime down with a non-canonical control-plane failure.
            Log.e("MorimilSelfImprove", "Self-improvement observer disabled", failure)
        }
    }

    val container: MorimilAppContainer by lazy {
        MorimilAppContainer(this)
    }
}
