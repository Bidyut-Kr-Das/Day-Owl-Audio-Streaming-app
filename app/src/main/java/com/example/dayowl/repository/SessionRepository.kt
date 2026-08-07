package com.example.dayowl.repository

import com.example.dayowl.model.SessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionRepository {
    private val _discoveredSessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val discoveredSessions: StateFlow<List<SessionInfo>> = _discoveredSessions.asStateFlow()

    private val _activeSession = MutableStateFlow<SessionInfo?>(null)
    val activeSession: StateFlow<SessionInfo?> = _activeSession.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    fun updateDiscoveredSessions(sessions: List<SessionInfo>) {
        _discoveredSessions.value = sessions
    }

    fun setActiveSession(session: SessionInfo?) {
        _activeSession.value = session
    }

    fun setBroadcasting(broadcasting: Boolean) {
        _isBroadcasting.value = broadcasting
    }
}
