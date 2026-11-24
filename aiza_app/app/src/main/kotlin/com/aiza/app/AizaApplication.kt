package com.aiza.app

import android.app.Application
import com.aiza.app.data.AppContainer

class AizaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
