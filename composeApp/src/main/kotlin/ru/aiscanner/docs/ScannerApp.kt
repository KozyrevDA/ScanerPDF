package ru.aiscanner.docs

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import ru.aiscanner.docs.core.di.appModules

class ScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ScannerApp)
            modules(appModules)
        }
    }
}
