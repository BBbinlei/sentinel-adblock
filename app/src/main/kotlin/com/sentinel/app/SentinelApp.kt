package com.sentinel.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SentinelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SentinelApp)
            modules(emptyList())
        }
    }
}
