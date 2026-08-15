package com.dagimg.glide

import android.app.Application
import android.content.Context
import com.dagimg.glide.di.AppContainer
import com.dagimg.glide.di.DefaultAppContainer

class GlideApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContainer = DefaultAppContainer(this)
    }

    companion object {
        lateinit var instance: GlideApplication
            private set

        val container: AppContainer
            get() = instance.appContainer
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GlideApplication).appContainer
