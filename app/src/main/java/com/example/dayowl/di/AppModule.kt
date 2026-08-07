package com.example.dayowl.di

import com.example.dayowl.audio.AudioPlayer
import com.example.dayowl.datastore.DataStoreManager
import com.example.dayowl.network.DiscoveryManager
import com.example.dayowl.network.PacedSender
import com.example.dayowl.network.SessionManager
import com.example.dayowl.network.UdpReceiver
import com.example.dayowl.network.UdpSender
import com.example.dayowl.repository.SessionRepository
import com.example.dayowl.ui.home.HomeViewModel
import com.example.dayowl.ui.host.HostViewModel
import com.example.dayowl.ui.join.JoinViewModel
import com.example.dayowl.ui.settings.SettingsViewModel
import com.example.dayowl.util.StatsManager
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { DataStoreManager(get()) }
    single { SessionRepository() }
    single { StatsManager() }
    factory { UdpSender() }
    factory { UdpReceiver() }
    factory { PacedSender(get(), get()) }
    single { SessionManager(get(), get(), get()) }
    single { DiscoveryManager(get(), get()) }

    factory { AudioPlayer(get()) }

    viewModelOf(::HomeViewModel)
    viewModelOf(::HostViewModel)
    viewModelOf(::JoinViewModel)
    viewModelOf(::SettingsViewModel)
}
