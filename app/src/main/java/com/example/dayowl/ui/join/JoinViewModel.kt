package com.example.dayowl.ui.join

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.example.dayowl.model.SessionInfo
import com.example.dayowl.network.DiscoveryManager
import com.example.dayowl.network.SessionManager
import com.example.dayowl.repository.ConnectionState
import com.example.dayowl.repository.SessionRepository
import com.example.dayowl.service.ClientService
import kotlinx.coroutines.flow.StateFlow

class JoinViewModel(
    private val context: Context,
    private val discoveryManager: DiscoveryManager,
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val discoveredSessions: StateFlow<List<SessionInfo>> = sessionRepository.discoveredSessions
    val activeSession: StateFlow<SessionInfo?> = sessionRepository.activeSession
    val connectionState: StateFlow<ConnectionState> = sessionRepository.connectionState

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun joinSession(session: SessionInfo) {
        // Sets CONNECTING synchronously; only a real JOIN_ACCEPT sets activeSession. Claiming the
        // session here is what made the UI say "Connected" to a host that never replied.
        sessionManager.joinSession(session)

        val intent = Intent(context, ClientService::class.java).apply {
            action = ClientService.ACTION_START_LISTEN
        }
        context.startForegroundService(intent)
    }

    fun leaveSession() {
        // Teardown clears activeSession and publishes IDLE; ClientService stops itself on that.
        sessionManager.leaveSession()
    }

    override fun onCleared() {
        stopDiscovery()
    }
}
