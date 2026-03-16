package com.example.mymusicapplication.music

import org.koin.core.module.Module
import org.koin.dsl.module

fun sharedModules(): List<Module> = listOf(commonMusicModule, platformModule())

private val commonMusicModule = module {
    single { PlaybackSettingsRepository() }
    single<MusicRepository> { MusicRepositoryImpl(get(), get()) }
    single { PlaybackController(get(), get(), get()) }
}

expect fun platformModule(): Module

