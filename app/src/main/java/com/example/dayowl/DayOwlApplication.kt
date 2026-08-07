package com.example.dayowl

import android.app.Application
import com.example.dayowl.di.appModule
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
    }
}
