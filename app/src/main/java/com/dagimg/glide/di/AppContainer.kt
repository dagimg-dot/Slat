package com.dagimg.glide.di

import android.content.Context
import com.dagimg.glide.data.ClipboardDatabase
import com.dagimg.glide.data.ClipboardRepository
import com.dagimg.glide.data.ClipboardRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface AppContainer {
    val clipboardRepository: ClipboardRepository
    val ioDispatcher: CoroutineDispatcher
    val mainDispatcher: CoroutineDispatcher
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    private val database: ClipboardDatabase by lazy {
        ClipboardDatabase.getInstance(context)
    }

    override val clipboardRepository: ClipboardRepository by lazy {
        ClipboardRepositoryImpl(
            dao = database.clipboardDao(),
            filesDir = context.filesDir,
            ioDispatcher = ioDispatcher,
        )
    }
}
