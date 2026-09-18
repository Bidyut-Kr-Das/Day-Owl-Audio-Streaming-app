package com.example.dayowl

import android.app.Application
import com.example.dayowl.di.appModule
import com.example.dayowl.network.WifiNetwork
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DayOwlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@DayOwlApplication)
            modules(appModule)
        }
        // Tracked for the life of the process: sockets are pinned to WiFi as they open.
        WifiNetwork.track(this)
    }
}
