package com.filodot.noscroll

import android.app.Application
import com.filodot.noscroll.runtime.NoScrollRuntime

class NoScrollApplication : Application() {
    val runtime: NoScrollRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NoScrollRuntime.create(this)
    }

    override fun onCreate() {
        super.onCreate()
        runtime
    }
}
