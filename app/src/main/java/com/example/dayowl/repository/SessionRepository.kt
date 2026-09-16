package com.example.dayowl.repository

import com.example.dayowl.model.SessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IDLE is the teardown signal. Join timeout, host lost and user leave are three roads to the same
 * destination, so they all land here and one observer (ClientService) acts on it.
 */
enum class ConnectionState { IDLE, CONNECTING, CONNECTED }

class SessionRepository {
    private val _discoveredSessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val discoveredSessions: StateFlow<List<SessionInfo>> = _discoveredSessions.asStateFlow()

    private val _activeSession = MutableStateFlow<SessionInfo?>(null)
    val activeSession: StateFlow<SessionInfo?> = _activeSession.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun updateDiscoveredSessions(sessions: List<SessionInfo>) {
        _discoveredSessions.value = sessions
    }

    fun setActiveSession(session: SessionInfo?) {
        _activeSession.value = session
    }

    fun setBroadcasting(broadcasting: Boolean) {
        _isBroadcasting.value = broadcasting
    }

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
