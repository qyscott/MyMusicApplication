package com.example.mymusicapplication

import android.app.Application
import com.example.mymusicapplication.music.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MusicApplication)
            modules(sharedModules())
        }
    }
}

