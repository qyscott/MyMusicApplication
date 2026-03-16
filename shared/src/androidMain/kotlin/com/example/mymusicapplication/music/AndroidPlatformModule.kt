package com.example.mymusicapplication.music

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SqlDriverFactory> { AndroidSqlDriverFactory(get()) }
    single<MusicScanner> { AndroidMusicScanner(get()) }
    single<AudioEngine> { AndroidAudioEngine(get()) }
}

private class AndroidSqlDriverFactory(
    private val context: Context,
) : SqlDriverFactory {
    override fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = musicDatabaseSchema,
        context = context,
        name = "music.db",
    )
}

