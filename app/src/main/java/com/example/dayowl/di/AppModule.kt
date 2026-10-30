package com.example.dayowl.di

import com.example.dayowl.audio.AudioPlayer
import com.example.dayowl.datastore.DataStoreManager
import com.example.dayowl.network.DiscoveryManager
import com.example.dayowl.network.SessionManager
import com.example.dayowl.repository.SessionRepository
import com.example.dayowl.ui.home.HomeViewModel
import com.example.dayowl.ui.host.HostViewModel
import com.example.dayowl.ui.join.JoinViewModel
import com.example.dayowl.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { DataStoreManager(get()) }
    single { SessionRepository() }
    single { SessionManager(get()) }
    single { DiscoveryManager(get(), get()) }

    // Sockets are not injected: each is owned by the thread that reads or writes it, and the
    // services construct them directly (UdpEndpoint).
    single { AudioPlayer() }

    viewModelOf(::HomeViewModel)
    viewModelOf(::HostViewModel)
    viewModelOf(::JoinViewModel)
    viewModelOf(::SettingsViewModel)
}
