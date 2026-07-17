package com.aisoul.app

import android.app.Application
import com.aisoul.app.di.AppContainer

class AiSoulApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
